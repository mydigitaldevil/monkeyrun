// Jungle Run multiplayer relay.
// Pairs two players into a room (private code or quick match) and forwards game messages
// between them. All gameplay runs on the phones; the server never simulates anything.
//
// Client → server: {t:"create",name} | {t:"join",code,name} | {t:"quick",name} | {t:"relay",p:{...}}
// Server → client: {t:"room",code,role} | {t:"peer_joined"} | {t:"peer_left"} | {t:"error",msg} | {t:"relay",p:{...}}

const { WebSocketServer } = require("ws");

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

const wss = new WebSocketServer({ port: PORT, maxPayload: MAX_MSG_BYTES });

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
module.exports = { wss };
