// Smoke test: two clients create/join a room and relay a message.
process.env.PORT = process.env.PORT || "18080";
process.env.DATA_FILE = require("path").join(require("os").tmpdir(), `jr-test-${Date.now()}.json`);
const { wss } = require("./index");
const WebSocket = require("ws");
const url = `ws://127.0.0.1:${process.env.PORT}`;

function client() {
  const ws = new WebSocket(url);
  ws.inbox = [];
  ws.on("message", (d) => ws.inbox.push(JSON.parse(d.toString())));
  return new Promise((res) => ws.on("open", () => res(ws)));
}
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const a = await client();
  a.send(JSON.stringify({ t: "create", name: "A" }));
  await wait(100);
  const code = a.inbox.find((m) => m.t === "room").code;
  const b = await client();
  b.send(JSON.stringify({ t: "join", code, name: "B" }));
  await wait(100);
  a.send(JSON.stringify({ t: "relay", p: { t: "start", seed: 42 } }));
  await wait(100);
  const ok =
    a.inbox.some((m) => m.t === "peer_joined") &&
    b.inbox.some((m) => m.t === "room" && m.role === "guest") &&
    b.inbox.some((m) => m.t === "relay" && m.p.seed === 42);

  // Quick match pairs two strangers
  const c = await client();
  const d = await client();
  c.send(JSON.stringify({ t: "quick", name: "C" }));
  await wait(100);
  d.send(JSON.stringify({ t: "quick", name: "D" }));
  await wait(100);
  const quickOk = c.inbox.some((m) => m.t === "peer_joined") && d.inbox.some((m) => m.t === "peer_joined");

  b.close();
  await wait(100);
  const leftOk = a.inbox.some((m) => m.t === "peer_left");

  // Leaderboard: submit two scores, reject a cheat, read the weekly board
  const http = `http://127.0.0.1:${process.env.PORT}`;
  const post = (body) =>
    fetch(`${http}/api/scores`, { method: "POST", body: JSON.stringify(body) }).then((r) => r.json());
  const good = await post({ playerId: "player-aaaa-1", name: "Momo", score: 1500, meters: 600, food: 120, durationSec: 60, animal: "monkey" });
  await post({ playerId: "player-bbbb-2", name: "Leo", score: 2500, meters: 900, food: 150, durationSec: 80, animal: "lion" });
  const cheat = await post({ playerId: "player-cccc-3", name: "Hax", score: 99999999, meters: 10, food: 0, durationSec: 5 });
  const lb = await fetch(`${http}/api/leaderboard?scope=week&playerId=player-aaaa-1`).then((r) => r.json());
  const boardOk = good.ok && !cheat.ok && lb.entries.length === 2 && lb.entries[0].name === "Leo" && lb.me.rank === 2 && lb.entries[1].league === "Bronze";

  console.log({ relay: ok, quickMatch: quickOk, peerLeft: leftOk, leaderboard: boardOk });
  [a, c, d].forEach((w) => w.close());
  wss.close();
  require("./index").server.close();
  process.exit(ok && quickOk && leftOk && boardOk ? 0 : 1);
})();
