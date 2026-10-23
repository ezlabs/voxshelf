/**
 * Voxshelf ratings: thumbs up or down on a LibriVox recording, plus optional problem reports.
 * POST /ratings saves (or replaces) one phone's rating. GET /ratings?ids=1,2 returns totals.
 * POST /crashes files an app crash as a GitHub issue (one issue per distinct crash; repeats just add to a count).
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
    if (url.pathname === "/crashes" && request.method === "POST") {
      const ip = request.headers.get("cf-connecting-ip") || "unknown";
      if (env.RATINGS_LIMIT && !(await env.RATINGS_LIMIT.limit({ key: `crash:${ip}` })).success) return json({ error: "too many" }, 429);
      try {
        return await crash(await request.json(), env);
      } catch (e) {
        return json({ error: "bad request" }, 400);
      }
    }
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
  if (!body.up && (problems.length || note)) await reportProblem(env, recording, String(body.title || "").slice(0, 200), problems, note, chapter);
  return json({ ok: true });
}

// Problem reports go to GitHub so they're easy to read: one issue per recording, one comment per report.
async function reportProblem(env, recording, title, problems, note, chapter) {
  const text = [
    problems.length ? `**Problems:** ${problems.join(", ")}` : "",
    chapter != null ? `**Chapter:** ${chapter + 1}` : "",
    note ? `**Note:**\n> ${note.replace(/\n/g, "\n> ")}` : "",
  ].filter(Boolean).join("\n\n");
  const existing = await env.DB.prepare("SELECT issue FROM problem_issue WHERE recording = ?").bind(recording).first();
  if (existing) {
    await github(env, `issues/${existing.issue}/comments`, { body: text });
    return;
  }
  const created = await github(env, "issues", {
    title: `Problem report: ${title || `recording ${recording}`}`,
    labels: ["problem report"],
    body: `Recording ${recording} · https://librivox.org/search?q=${encodeURIComponent(title)}

${text}`,
  });
  if (created) await env.DB.prepare("INSERT INTO problem_issue (recording, issue) VALUES (?, ?)").bind(recording, created.number).run();
}

/** Calls the GitHub API for the app's repo; returns the response, or null if GitHub isn't set up or the call failed. */
async function github(env, path, payload) {
  if (!env.GITHUB_TOKEN || !env.GITHUB_REPO) return null;
  const res = await fetch(`https://api.github.com/repos/${env.GITHUB_REPO}/${path}`, {
    method: "POST",
    headers: { authorization: `Bearer ${env.GITHUB_TOKEN}`, accept: "application/vnd.github+json", "user-agent": "voxshelf-ratings" },
    body: JSON.stringify(payload),
  });
  return res.ok ? await res.json() : null;
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
    await env.COVERS.put(key, request.body, { httpMetadata: { contentType: "image/webp", cacheControl: "public, max-age=31536000, immutable" } });
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

// Crash reports carry only the error, app version, Android version and phone model: nothing about the person.
async function crash(body, env) {
  const trace = String(body.trace || "").slice(0, 8000);
  const version = String(body.version || "").slice(0, 40);
  const android = String(body.android || "").slice(0, 40);
  const model = String(body.model || "").slice(0, 80);
  if (!trace) return json({ error: "bad request" }, 400);
  const first = trace.split("\n")[0].slice(0, 200);
  // Same error type at the same first line of our own code counts as the same crash.
  const place = trace.split("\n").find((l) => l.includes("app.voxshelf")) || "";
  const signature = await sha(`${first.split(":")[0]}|${place.trim()}`);
  const now = Date.now();
  const existing = await env.DB.prepare("SELECT issue, count FROM crash WHERE signature = ?").bind(signature).first();
  if (existing) {
    await env.DB.prepare("UPDATE crash SET count = count + 1, last_at = ? WHERE signature = ?").bind(now, signature).run();
    return json({ ok: true, issue: existing.issue });
  }
  const created = await github(env, "issues", {
    title: `Crash: ${first}`.slice(0, 200),
    labels: ["crash"],
    body: `App ${version} · Android ${android} · ${model}\n\n\`\`\`\n${trace}\n\`\`\``,
  });
  const issue = created ? created.number : null;
  await env.DB.prepare("INSERT INTO crash (signature, issue, count, first_at, last_at) VALUES (?, ?, 1, ?, ?)")
    .bind(signature, issue, now, now).run();
  return json({ ok: true, issue });
}

async function sha(text) {
  const bytes = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
  return [...bytes].slice(0, 12).map((b) => b.toString(16).padStart(2, "0")).join("");
}
