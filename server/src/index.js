/**
 * Voxshelf ratings: thumbs up or down on a LibriVox recording, plus optional problem reports.
 * POST /ratings saves (or replaces) one phone's rating. GET /ratings?ids=1,2 returns totals.
 * Also serves small copies of LibriVox cover art: GET /covers/123.webp (PUT with the upload key adds one).
 */
const PROBLEMS = ["sound", "hard_to_follow", "missing", "wont_play", "order", "other"];
// Totals stay hidden until a recording has this many ratings, so one rating can't define it.
const MIN_RATINGS = 3;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const cover = url.pathname.match(/^\/covers\/(\d+)\.webp$/);
    if (cover) return covers(request, env, `${cover[1]}.webp`);
    if (url.pathname !== "/ratings") return json({ error: "not found" }, 404);
    try {
      if (request.method === "POST") {
        // Stops one address from flooding in fake ratings. A real person rates far less often than this.
        const ip = request.headers.get("cf-connecting-ip") || "unknown";
        if (env.RATINGS_LIMIT && !(await env.RATINGS_LIMIT.limit({ key: ip })).success) return json({ error: "too many ratings" }, 429);
        return await save(await request.json(), env);
      }
      if (request.method === "GET") return await totals(url.searchParams.get("ids") || "", env);
      return json({ error: "method not allowed" }, 405);
    } catch (e) {
      return json({ error: "bad request" }, 400);
    }
  },
};

async function save(body, env) {
  const device = String(body.device || "");
  const recording = Number(body.recording);
  if (!/^[0-9a-f-]{36}$/.test(device) || !Number.isInteger(recording) || recording <= 0 || typeof body.up !== "boolean") {
    return json({ error: "bad request" }, 400);
  }
  const problems = body.up ? [] : [...new Set((body.problems || []).filter((p) => PROBLEMS.includes(p)))];
  const note = body.up ? "" : String(body.note || "").slice(0, 1000);
  const chapter = Number.isInteger(body.chapter) ? body.chapter : null;
  await env.DB.prepare(
    "INSERT OR REPLACE INTO rating (device, recording, up, problems, note, chapter, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
  ).bind(device, recording, body.up ? 1 : 0, problems.join(","), note, chapter, Date.now()).run();
  return json({ ok: true });
}

async function totals(ids, env) {
  const list = [...new Set(ids.split(",").map(Number).filter((n) => Number.isInteger(n) && n > 0))].slice(0, 50);
  if (list.length === 0) return json({});
  const { results } = await env.DB.prepare(
    `SELECT recording, up, problems FROM rating WHERE recording IN (${list.map(() => "?").join(",")})`,
  ).bind(...list).all();
  const out = {};
  for (const r of results) {
    const t = (out[r.recording] ||= { total: 0, up: 0, problems: {} });
    t.total++;
    t.up += r.up;
    for (const p of r.problems.split(",").filter(Boolean)) t.problems[p] = (t.problems[p] || 0) + 1;
  }
  for (const id of Object.keys(out)) if (out[id].total < MIN_RATINGS) out[id] = { total: out[id].total };
  return json(out, 200, { "Cache-Control": "public, max-age=300" });
}

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json", ...headers } });
}

async function covers(request, env, key) {
  if (request.method === "PUT") {
    if (!env.UPLOAD_KEY || request.headers.get("x-upload-key") !== env.UPLOAD_KEY) return json({ error: "forbidden" }, 403);
    await env.COVERS.put(key, request.body, { httpMetadata: { contentType: "image/webp" } });
    return json({ ok: true });
  }
  if (request.method !== "GET") return json({ error: "method not allowed" }, 405);
  // Covers never change, so keep them in Cloudflare's cache near the phone and on the phone itself.
  const cache = caches.default;
  const hit = await cache.match(request);
  if (hit) return hit;
  const object = await env.COVERS.get(key);
  if (!object) return new Response("not found", { status: 404 });
  const response = new Response(object.body, {
    headers: { "content-type": "image/webp", "cache-control": "public, max-age=31536000, immutable" },
  });
  await cache.put(request, response.clone());
  return response;
}
