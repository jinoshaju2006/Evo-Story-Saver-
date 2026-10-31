import { createServer } from "node:http";

const PORT = Number(process.env.PORT || 3000);
const GIS_KEY = process.env.GIS_KEY;
const WINDOW_MS = 60_000;
const MAX_REQUESTS = 10;
const requests = new Map();

function send(res, status, body) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type"
  });
  res.end(JSON.stringify(body));
}

function serviceFor(raw) {
  let u;
  try { u = new URL(raw); } catch { return null; }
  const host = u.hostname.toLowerCase().replace(/^www\./, "");
  if (host !== "instagram.com") return null;
  if (/^\/stories\/highlights\//.test(u.pathname)) return "instagram-highlight";
  if (/^\/stories\//.test(u.pathname)) return "instagram-story";
  if (/^\/(p|reel|tv)\//.test(u.pathname)) return "instagram";
  return null;
}

createServer(async (req, res) => {
  if (req.method === "OPTIONS") return send(res, 204, {});
  if (req.method === "GET" && req.url === "/health") return send(res, 200, { ok: true, service: "EVO Insta Saver API" });
  if (req.method !== "POST" || req.url !== "/api/download") return send(res, 404, { error: "Not found" });
  if (!GIS_KEY) return send(res, 503, { error: "Server API key is not configured." });

  const ip = req.socket.remoteAddress || "unknown";
  const now = Date.now();
  const entry = requests.get(ip) || { start: now, count: 0 };
  if (now - entry.start > WINDOW_MS) { entry.start = now; entry.count = 0; }
  entry.count++;
  requests.set(ip, entry);
  if (entry.count > MAX_REQUESTS) return send(res, 429, { error: "Too many requests. Please wait a minute." });

  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 4096) return send(res, 413, { error: "Request too large." });
  }

  let link;
  try { link = JSON.parse(raw).link; } catch { return send(res, 400, { error: "Send JSON containing a link." }); }
  if (typeof link !== "string" || link.length > 2048) return send(res, 400, { error: "Enter a valid Instagram link." });
  const service = serviceFor(link);
  if (!service) return send(res, 400, { error: "Supported links: public Instagram posts, Reels, Stories and Highlights." });

  try {
    const upstream = await fetch("https://developers.getinsaver.com/v1/resolve", {
      method: "POST",
      headers: { "Authorization": `Bearer ${GIS_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({ service, url: link, max_results: 10 }),
      signal: AbortSignal.timeout(130000)
    });
    const data = await upstream.json().catch(() => ({}));
    if (!upstream.ok) return send(res, upstream.status, {
      error: data.message || "The media could not be resolved.",
      code: data.code || "UPSTREAM_ERROR"
    });
    const items = Array.isArray(data.items) ? data.items.filter(x => x && typeof x.url === "string" && ["video", "image", "audio"].includes(x.type)) : [];
    return send(res, 200, { ok: true, items, author: data.metadata?.author || null, request_id: data.request_id || null, credits_used: data.credits_used ?? null });
  } catch {
    return send(res, 502, { error: "The media service did not respond. Please try again." });
  }
}).listen(PORT, "0.0.0.0", () => console.log(`EVO API listening on ${PORT}`));
