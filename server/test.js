// Smoke test: two clients create/join a room and relay a message.
process.env.PORT = process.env.PORT || "18080";
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

  console.log({ relay: ok, quickMatch: quickOk, peerLeft: leftOk });
  [a, c, d].forEach((w) => w.close());
  wss.close();
  process.exit(ok && quickOk && leftOk ? 0 : 1);
})();
