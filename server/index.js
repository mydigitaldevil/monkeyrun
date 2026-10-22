// Jungle Run multiplayer relay.
// Pairs two players into a room (private code or quick match) and forwards game messages
// between them. All gameplay runs on the phones; the server never simulates anything.
//
// Client → server: {t:"create",name} | {t:"join",code,name} | {t:"quick",name} | {t:"relay",p:{...}}
// Server → client: {t:"room",code,role} | {t:"peer_joined"} | {t:"peer_left"} | {t:"error",msg} | {t:"relay",p:{...}}

const http = require("http");
const path = require("path");
const { WebSocketServer } = require("ws");
const { Leaderboard } = require("./leaderboard");

const PORT = Number(process.env.PORT || 8080);
const MAX_MSG_BYTES = 4096;
const ROOM_TTL_MS = 30 * 60 * 1000;
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I confusion

/** @type {Map<string, {code:string, players:any[], quick:boolean, createdAt:number}>} */
const rooms = new Map();
let quickWaiting = null; // room code waiting for a quick-match opponent

function newCode() {
  for (;;) {
    let c = "";
    for (let i = 0; i < 5; i++) c += CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)];
    if (!rooms.has(c)) return c;
  }
}

function send(ws, obj) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(obj));
}

function createRoom(ws, quick) {
  const code = newCode();
  const room = { code, players: [ws], quick, createdAt: Date.now() };
  rooms.set(code, room);
  ws.roomCode = code;
  send(ws, { t: "room", code, role: "host" });
  return room;
}

function joinRoom(ws, room) {
  room.players.push(ws);
  ws.roomCode = room.code;
  send(ws, { t: "room", code: room.code, role: "guest" });
  for (const p of room.players) send(p, { t: "peer_joined" });
}

function leave(ws) {
  const code = ws.roomCode;
  if (!code) return;
  ws.roomCode = null;
  const room = rooms.get(code);
  if (!room) return;
  room.players = room.players.filter((p) => p !== ws);
  for (const p of room.players) send(p, { t: "peer_left" });
  if (room.players.length === 0) rooms.delete(code);
  if (quickWaiting === code) quickWaiting = null;
}

const board = new Leaderboard(process.env.DATA_FILE || path.join(__dirname, "data", "scores.json"));

// ---- HTTP API: score submission + leaderboards (same port as the WebSocket relay)
function json(res, code, body) {
  res.writeHead(code, { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" });
  res.end(JSON.stringify(body));
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, "http://localhost");
  if (req.method === "GET" && url.pathname === "/health") return json(res, 200, { ok: true });
  if (req.method === "GET" && url.pathname === "/api/leaderboard") {
    const scope = url.searchParams.get("scope") === "all" ? "all" : "week";
    const limit = Math.min(Number(url.searchParams.get("limit")) || 50, 100);
    return json(res, 200, board.top(scope, limit, url.searchParams.get("playerId")));
  }
  if (req.method === "POST" && url.pathname === "/api/scores") {
    let body = "";
    req.on("data", (c) => {
      body += c;
      if (body.length > 4096) req.destroy();
    });
    req.on("end", () => {
      let s;
      try {
        s = JSON.parse(body);
      } catch {
        return json(res, 400, { ok: false, error: "bad json" });
      }
      const r = board.submit(s);
      json(res, r.ok ? 200 : 422, r);
    });
    return;
  }
  json(res, 404, { ok: false, error: "not found" });
});

const wss = new WebSocketServer({ server, maxPayload: MAX_MSG_BYTES });
server.listen(PORT);

wss.on("connection", (ws) => {
  ws.isAlive = true;
  ws.on("pong", () => (ws.isAlive = true));

  ws.on("message", (data) => {
    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return;
    }
    switch (msg.t) {
      case "create":
        leave(ws);
        createRoom(ws, false);
        break;
      case "join": {
        leave(ws);
        const room = rooms.get(String(msg.code || "").toUpperCase());
        if (!room) return send(ws, { t: "error", msg: "Room not found. Check the code." });
        if (room.players.length >= 2) return send(ws, { t: "error", msg: "Room is full." });
        joinRoom(ws, room);
        break;
      }
      case "quick": {
        leave(ws);
        const waiting = quickWaiting && rooms.get(quickWaiting);
        if (waiting && waiting.players.length === 1) {
          quickWaiting = null;
          joinRoom(ws, waiting);
        } else {
          quickWaiting = createRoom(ws, true).code;
        }
        break;
      }
      case "relay": {
        const room = ws.roomCode && rooms.get(ws.roomCode);
        if (!room || typeof msg.p !== "object") return;
        for (const p of room.players) if (p !== ws) send(p, { t: "relay", p: msg.p });
        break;
      }
    }
  });

  ws.on("close", () => leave(ws));
  ws.on("error", () => leave(ws));
});

// Heartbeat + stale room cleanup
setInterval(() => {
  for (const ws of wss.clients) {
    if (!ws.isAlive) {
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
  const now = Date.now();
  for (const [code, room] of rooms) {
    if (now - room.createdAt > ROOM_TTL_MS) {
      for (const p of room.players) p.close(1000, "room expired");
      rooms.delete(code);
    }
  }
}, 15000);

console.log(`Jungle Run server listening on ws://0.0.0.0:${PORT}`);
module.exports = { wss, server, board };
