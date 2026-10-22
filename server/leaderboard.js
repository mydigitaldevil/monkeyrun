// Score records + weekly leagues for Jungle Run.
// Storage is a JSON file (good for a single small server); swap for Postgres/Redis/Firestore at scale.

const fs = require("fs");
const path = require("path");

const LEAGUES = [
  { name: "Bronze", min: 0 },
  { name: "Silver", min: 2000 },
  { name: "Gold", min: 6000 },
  { name: "Diamond", min: 15000 },
  { name: "Legend", min: 35000 },
];

function leagueFor(score) {
  let l = LEAGUES[0];
  for (const x of LEAGUES) if (score >= x.min) l = x;
  return l.name;
}

/** ISO week key, e.g. "2026-W43". Leagues reset every Monday (UTC). */
function weekKey(date = new Date()) {
  const d = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const day = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - day);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((d - yearStart) / 86400000 + 1) / 7);
  return `${d.getUTCFullYear()}-W${String(week).padStart(2, "0")}`;
}

/** Rejects obviously impossible runs (speed cap, score formula bounds). */
function plausible(s) {
  if (!s || typeof s !== "object") return "bad body";
  const { score, meters, food, durationSec, multiplier } = s;
  for (const [k, v] of Object.entries({ score, meters, food, durationSec })) {
    if (!Number.isFinite(v) || v < 0) return `bad ${k}`;
  }
  if (durationSec < 1) return "too short";
  if (meters > durationSec * 50 + 20) return "too fast";
  const mul = Math.min(Math.max(Number(multiplier) || 1, 1), 30);
  const maxScore = (meters + food * 5 + 400 + durationSec * 30) * mul;
  if (score > maxScore) return "score too high";
  return null;
}

class Leaderboard {
  constructor(file) {
    this.file = file;
    this.data = { players: {}, weeks: {} };
    try {
      this.data = JSON.parse(fs.readFileSync(file, "utf8"));
    } catch {
      /* first run */
    }
    this.saveTimer = null;
  }

  save() {
    if (this.saveTimer) return;
    this.saveTimer = setTimeout(() => {
      this.saveTimer = null;
      fs.mkdirSync(path.dirname(this.file), { recursive: true });
      const tmp = this.file + ".tmp";
      fs.writeFileSync(tmp, JSON.stringify(this.data));
      fs.renameSync(tmp, this.file);
    }, 1000);
  }

  submit(s) {
    const err = plausible(s);
    if (err) return { ok: false, error: err };
    const id = String(s.playerId || "").slice(0, 64);
    if (!/^[A-Za-z0-9-]{8,64}$/.test(id)) return { ok: false, error: "bad playerId" };
    const name = String(s.name || "Player").replace(/[^\p{L}\p{N} _.-]/gu, "").slice(0, 16) || "Player";
    const animal = String(s.animal || "monkey").slice(0, 24);
    const score = Math.floor(s.score);

    const p = (this.data.players[id] ||= { name, best: 0, bestAnimal: animal, runs: 0 });
    p.name = name;
    p.runs++;
    if (score > p.best) {
      p.best = score;
      p.bestAnimal = animal;
    }
    const wk = weekKey();
    const week = (this.data.weeks[wk] ||= {});
    const w = (week[id] ||= { best: 0, animal });
    if (score > w.best) {
      w.best = score;
      w.animal = animal;
    }
    // keep only the last 8 weeks
    const keys = Object.keys(this.data.weeks).sort();
    while (keys.length > 8) delete this.data.weeks[keys.shift()];
    this.save();
    const rank = this.rankOf("week", id);
    return { ok: true, week: wk, weekBest: w.best, allTimeBest: p.best, league: leagueFor(w.best), rank };
  }

  entries(scope) {
    if (scope === "all") {
      return Object.entries(this.data.players).map(([id, p]) => ({ id, name: p.name, score: p.best, animal: p.bestAnimal }));
    }
    const week = this.data.weeks[weekKey()] || {};
    return Object.entries(week).map(([id, w]) => ({
      id,
      name: (this.data.players[id] || {}).name || "Player",
      score: w.best,
      animal: w.animal,
    }));
  }

  rankOf(scope, id) {
    const sorted = this.entries(scope).sort((a, b) => b.score - a.score);
    const i = sorted.findIndex((e) => e.id === id);
    return i < 0 ? null : i + 1;
  }

  top(scope, limit, playerId) {
    const sorted = this.entries(scope).sort((a, b) => b.score - a.score);
    const entries = sorted.slice(0, limit).map((e, i) => ({
      rank: i + 1,
      name: e.name,
      score: e.score,
      animal: e.animal,
      league: leagueFor(e.score),
      me: e.id === playerId,
    }));
    let me = null;
    const i = sorted.findIndex((e) => e.id === playerId);
    if (i >= 0) me = { rank: i + 1, score: sorted[i].score, league: leagueFor(sorted[i].score) };
    return { scope, week: weekKey(), total: sorted.length, entries, me, leagues: LEAGUES };
  }
}

module.exports = { Leaderboard, leagueFor, weekKey, plausible, LEAGUES };
