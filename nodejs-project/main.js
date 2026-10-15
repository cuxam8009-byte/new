'use strict'
// MC Bot Manager — Node.js chạy ngay trong app Android (nodejs-mobile), không cần server ngoài.
// Argv: node main.js <port> <token> <thư-mục-dữ-liệu>

const os = require('os')
try {
  // Android 11+ có thể chặn os.networkInterfaces() -> bọc lại để không làm sập thư viện
  const orig = os.networkInterfaces
  os.networkInterfaces = function () { try { return orig.call(os) } catch (e) { return {} } }
} catch (e) { /* bỏ qua */ }

const http = require('http')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const PORT = parseInt(process.argv[2], 10) || 3000
const TOKEN = process.argv[3] || 'dev'
const DATA = process.argv[4] || path.join(__dirname, 'data')
const PAGE = path.join(__dirname, 'public', 'index.html')
const CFG_FILE = path.join(DATA, 'config.json')
const LOG_FILE = path.join(DATA, 'bot.log')
fs.mkdirSync(DATA, { recursive: true })

// ---------- nạp bedrock-protocol ----------
let bedrock = null
let loadError = ''
let Versions = {}
let CURRENT_VERSION = ''
try { bedrock = require('bedrock-protocol') } catch (e) { loadError = 'Không nạp được bedrock-protocol: ' + e.message }
try {
  const o = require('bedrock-protocol/src/options')
  Versions = o.Versions || {}
  CURRENT_VERSION = o.CURRENT_VERSION || ''
} catch (e) { /* không có danh sách phiên bản thì để người dùng tự gõ */ }

// ---------- trạng thái chung ----------
const events = []
const listeners = new Set()
const dirtySnap = new Set()
let dirtyOnline = false
let config = null
let logBytes = 0
const meta = { seed: null, world: null }
const rts = new Map() // id bot -> trạng thái chạy

function broadcast (obj) {
  const d = 'data: ' + JSON.stringify(obj) + '\n\n'
  for (const r of listeners) { try { r.write(d) } catch (e) { /* client đã đóng */ } }
}

function writeLog (line) {
  try {
    if (logBytes > 1500000) {
      try { fs.renameSync(LOG_FILE, LOG_FILE + '.old') } catch (e) { /* bỏ qua */ }
      logBytes = 0
    }
    logBytes += Buffer.byteLength(line)
    fs.appendFile(LOG_FILE, line, () => {})
  } catch (e) { /* bỏ qua */ }
}

function push (type, text, opt = {}) {
  const ev = { t: Date.now(), type, text: String(text), who: opt.who, bot: opt.bot }
  events.push(ev)
  const keep = config ? config.options.keepLogLines : 300
  while (events.length > keep) events.shift()
  const line = (opt.bot ? `{${opt.bot}} ` : '') + (opt.who ? `<${opt.who}> ` : '') + ev.text
  console.log(`[${type}] ${line}`)
  if (config && config.options.writeLogFile) writeLog(`[${new Date(ev.t).toISOString()}] [${type}] ${line}\n`)
  broadcast({ ev })
}

process.on('uncaughtException', (e) => push('err', 'Lỗi không bắt được: ' + (e && e.message)))
process.on('unhandledRejection', (e) => push('err', 'Promise lỗi: ' + (e && e.message)))

// ---------- cấu hình ----------
const S = (v, d = '') => (typeof v === 'string' ? v.trim() : d)
const N = (v, d, lo, hi) => { const n = Math.round(Number(v)); return Number.isFinite(n) ? Math.min(hi, Math.max(lo, n)) : d }
const B = (v, d) => (typeof v === 'boolean' ? v : d)
const newId = () => crypto.randomBytes(4).toString('hex')

function newBotCfg (name) {
  return {
    id: newId(),
    name,
    enabled: true,
    useGlobal: true,
    server: { host: '', port: 0, version: '' },
    joinCommands: [],
    autoMessage: { enabled: false, text: '', everySec: 300 }
  }
}

function defaults () {
  const bots = ['Ilovefemboy', 'Chat Gpt'].map((n) => { const b = newBotCfg(n); b.id = newId(); return b })
  return {
    server: { host: 'gamma.pikamc.vn', port: 25692, version: '1.26.51', offline: true },
    ui: { setupDone: false, batAsked: false },
    options: {
      staggerSec: 8, autoReconnect: true, reconnectBaseSec: 20, reconnectMaxSec: 120,
      connectTimeoutSec: 30, autoStart: true, keepLogLines: 300, writeLogFile: false
    },
    bots
  }
}

function cleanServer (s, d) {
  s = s || {}
  return {
    host: S(s.host, d.host).slice(0, 253),
    port: N(s.port, d.port, 1, 65535),
    version: S(s.version, d.version).slice(0, 20),
    offline: B(s.offline, d.offline)
  }
}

function cleanUi (u, d) {
  u = u || {}
  return { setupDone: B(u.setupDone, d.setupDone), batAsked: B(u.batAsked, d.batAsked) }
}

function cleanOptions (o, d) {
  o = o || {}
  return {
    staggerSec: N(o.staggerSec, d.staggerSec, 0, 600),
    autoReconnect: B(o.autoReconnect, d.autoReconnect),
    reconnectBaseSec: N(o.reconnectBaseSec, d.reconnectBaseSec, 1, 3600),
    reconnectMaxSec: N(o.reconnectMaxSec, d.reconnectMaxSec, 1, 7200),
    connectTimeoutSec: N(o.connectTimeoutSec, d.connectTimeoutSec, 5, 300),
    autoStart: B(o.autoStart, d.autoStart),
    keepLogLines: N(o.keepLogLines, d.keepLogLines, 50, 2000),
    writeLogFile: B(o.writeLogFile, d.writeLogFile)
  }
}

function cleanBot (b) {
  if (!b || typeof b !== 'object') return null
  const name = S(b.name).slice(0, 32)
  if (!name) return null
  const sv = b.server || {}
  const am = b.autoMessage || {}
  return {
    id: /^[a-f0-9]{8}$/.test(b.id) ? b.id : newId(),
    name,
    enabled: B(b.enabled, true),
    useGlobal: B(b.useGlobal, true),
    server: { host: S(sv.host).slice(0, 253), port: N(sv.port, 0, 0, 65535), version: S(sv.version).slice(0, 20) },
    joinCommands: (Array.isArray(b.joinCommands) ? b.joinCommands : []).slice(0, 20)
      .map((c) => ({ text: S(c && c.text).slice(0, 200), delaySec: N(c && c.delaySec, 3, 0, 3600) }))
      .filter((c) => c.text),
    autoMessage: { enabled: B(am.enabled, false), text: S(am.text).slice(0, 200), everySec: N(am.everySec, 300, 10, 86400) }
  }
}

function cleanConfig (raw) {
  const d = defaults()
  raw = raw && typeof raw === 'object' ? raw : {}
  const cfg = { server: cleanServer(raw.server, d.server), options: cleanOptions(raw.options, d.options), ui: cleanUi(raw.ui, d.ui), bots: [] }
  const names = new Set()
  const ids = new Set()
  const src = Array.isArray(raw.bots) ? raw.bots : d.bots
  for (const b of src.slice(0, 50)) {
    const nb = cleanBot(b)
    if (!nb || names.has(nb.name.toLowerCase())) continue
    while (ids.has(nb.id)) nb.id = newId()
    names.add(nb.name.toLowerCase())
    ids.add(nb.id)
    cfg.bots.push(nb)
  }
  return cfg
}

function loadConfig () {
  try {
    const raw = JSON.parse(fs.readFileSync(CFG_FILE, 'utf8'))
    // Cấu hình bản cũ chưa có khối ui = người dùng cũ: không bắt làm lại hướng dẫn, vẫn tự chạy bot như trước
    if (raw && typeof raw === 'object' && !raw.ui) raw.ui = { setupDone: true, batAsked: true }
    return cleanConfig(raw)
  } catch (e) { return cleanConfig(null) }
}

function saveConfig () {
  try {
    const tmp = CFG_FILE + '.tmp'
    fs.writeFileSync(tmp, JSON.stringify(config, null, 2))
    fs.renameSync(tmp, CFG_FILE)
  } catch (e) { push('err', 'Không lưu được cấu hình: ' + e.message) }
}

const cfgOf = (id) => config.bots.find((b) => b.id === id)
const nameTaken = (name, exceptId) => config.bots.some((b) => b.id !== exceptId && b.name.toLowerCase() === name.toLowerCase())
const ours = () => new Set(config.bots.map((b) => b.name))

function effServer (cfg) {
  const g = config.server
  if (cfg.useGlobal) return g
  return { host: cfg.server.host || g.host, port: cfg.server.port || g.port, version: cfg.server.version || g.version, offline: g.offline }
}

// ---------- tiện ích ----------
function xyz (v) {
  if (!v) return null
  const x = Number(v.x); const y = Number(v.y); const z = Number(v.z)
  if ([x, y, z].some(Number.isNaN)) return null
  return { x, y, z }
}
const fmt = (v) => `${v.x.toFixed(0)}, ${v.y.toFixed(0)}, ${v.z.toFixed(0)}`

function versionList () {
  const cmp = (a, b) => {
    const x = a.split('.').map(Number); const y = b.split('.').map(Number)
    for (let i = 0; i < Math.max(x.length, y.length); i++) { const d = (y[i] || 0) - (x[i] || 0); if (d) return d }
    return 0
  }
  return Object.keys(Versions).sort(cmp)
}

function rtOf (cfg) {
  let r = rts.get(cfg.id)
  if (!r) {
    r = {
      id: cfg.id, status: 'stopped', client: null, ready: false, desired: false, reconnecting: false,
      failCount: 0, gen: 0, players: new Map(), online: new Map(), pos: null, selfId: '', timers: [], retryTimer: null,
      since: 0, lastError: '', retryAt: 0
    }
    rts.set(cfg.id, r)
  }
  return r
}

const leader = () => {
  for (const b of config.bots) { const r = rts.get(b.id); if (r && r.ready) return r }
  return null
}

const noInfo = { since: 0, err: '', retryAt: 0 }
function botInfo (r) { return { since: r.since || 0, err: r.lastError || '', retryAt: r.retryAt || 0 } }

function setStatus (r, s) {
  r.status = s
  if (s === 'online') { r.since = Date.now(); r.retryAt = 0; r.lastError = '' }
  else if (s === 'connecting') { r.since = 0; r.retryAt = 0 }
  else if (s === 'stopped') { r.since = 0; r.retryAt = 0; r.lastError = '' }
  else if (s === 'lost') { r.since = 0 }
  broadcast({ botStatus: Object.assign({ id: r.id, status: s }, botInfo(r)) })
}

// Gợi ý tiếng Việt cho các lỗi hay gặp (chỉ là gợi ý, luôn kèm lỗi gốc)
function hint (msg) {
  const m = String(msg || '').toLowerCase()
  if (/etimedout|timed? ?out|timeout/.test(m)) return 'Server không phản hồi: kiểm tra địa chỉ, cổng, phiên bản; server có thể đang tắt hoặc chặn bot.'
  if (/enotfound|getaddrinfo|eai_again/.test(m)) return 'Không tìm thấy địa chỉ server: kiểm tra chính tả địa chỉ hoặc mạng.'
  if (/econnrefused/.test(m)) return 'Server từ chối kết nối: sai cổng hoặc server chưa mở.'
  if (/enetunreach|ehostunreach|network is unreachable/.test(m)) return 'Điện thoại đang không có mạng.'
  if (/xbox|xsts|\bmsa\b|authenticat/.test(m)) return 'Lỗi đăng nhập: server có thể đang bật/tắt xbox-auth khác với chế độ offline trong Cài đặt.'
  if (/white-?list|not on the list/.test(m)) return 'Bot chưa có trong whitelist của server.'
  if (/\bbanned\b/.test(m)) return 'Bot có thể đã bị cấm (ban) trên server.'
  if (/server is full|\bfull\b/.test(m)) return 'Server đang đầy người.'
  if (/version|protocol|outdated|unsupported/.test(m)) return 'Có thể sai phiên bản Minecraft: dùng nút "Kiểm tra server" rồi chọn đúng phiên bản.'
  return ''
}
const withHint = (msg) => { const h = hint(msg); return h ? msg + ' → ' + h : msg }

function clearTimers (r) {
  for (const t of r.timers) { clearTimeout(t); clearInterval(t) }
  r.timers = []
}

function snapshot (r) {
  return { bot: r.pos, list: [...r.players.values()].map((p) => ({ name: p.name, x: p.pos.x, y: p.pos.y, z: p.pos.z, t: p.t })) }
}

function onlineList () {
  const m = new Map()
  const mine = ours()
  for (const r of rts.values()) for (const n of r.online.values()) m.set(n, { name: n, ours: mine.has(n) })
  return [...m.values()].sort((a, c) => a.name.localeCompare(c.name))
}

setInterval(() => {
  for (const id of dirtySnap) {
    const r = rts.get(id)
    if (r) broadcast({ snap: { id, data: snapshot(r) } })
  }
  dirtySnap.clear()
  if (dirtyOnline) { dirtyOnline = false; broadcast({ online: onlineList() }) }
}, 1000)

setInterval(() => { for (const r of listeners) { try { r.write(':\n\n') } catch (e) { /* bỏ qua */ } } }, 25000)

function publicState () {
  return {
    config,
    st: Object.fromEntries(config.bots.map((b) => [b.id, rts.get(b.id) ? rts.get(b.id).status : 'stopped'])),
    info: Object.fromEntries(config.bots.map((b) => [b.id, rts.get(b.id) ? botInfo(rts.get(b.id)) : noInfo])),
    versions: versionList(),
    currentVersion: CURRENT_VERSION,
    meta,
    loadError,
    app: { node: process.versions.node, dataDir: DATA }
  }
}
const broadcastState = () => broadcast({ state: publicState() })

// ---------- bot ----------
function sendChat (r, msg) {
  if (!r || !r.client || !r.ready) return false
  const cfg = cfgOf(r.id)
  try {
    r.client.queue('text', {
      needs_translation: false,
      category: 'authored',
      chat: 'chat',
      whisper: 'whisper',
      announcement: 'announcement',
      type: 'chat',
      source_name: r.client.username || (cfg && cfg.name) || '',
      message: msg,
      xuid: '',
      platform_chat_id: '',
      has_filtered_message: false
    })
  } catch (e) {
    push('err', 'Gửi tin lỗi: ' + e.message, { bot: cfg && cfg.name })
    return false
  }
  push('chat', msg, { who: cfg ? cfg.name : '' })
  return true
}

function clientOptions (cfg, srv) {
  const o = {
    host: srv.host,
    port: srv.port,
    username: cfg.name,
    version: srv.version,
    offline: srv.offline,
    skipPing: true,
    raknetBackend: 'jsp-raknet', // bản JS thuần, chạy được trên Android
    connectTimeout: config.options.connectTimeoutSec * 1000
  }
  if (!srv.offline) {
    // Chế độ online: đăng nhập Microsoft bằng mã thiết bị, token lưu trong thư mục dữ liệu
    const dir = path.join(DATA, 'auth', cfg.name.replace(/[^\w.-]/g, '_'))
    try { fs.mkdirSync(dir, { recursive: true }) } catch (e) { /* bỏ qua */ }
    o.profilesFolder = dir
    o.onMsaCode = (d) => push('sys', `Đăng nhập Microsoft: mở ${d.verification_uri} và nhập mã ${d.user_code}`, { bot: cfg.name })
  }
  return o
}

function onSpawn (r, gen) {
  const cfg = cfgOf(r.id)
  if (!cfg) return
  r.timers.push(setTimeout(() => {
    if (r.gen !== gen || leader() !== r) return
    const l = onlineList()
    push('sys', `Đang online (${l.length}): ${l.map((x) => x.name).join(', ') || 'không có ai'}`)
  }, 2500))
  for (const c of cfg.joinCommands) {
    r.timers.push(setTimeout(() => { if (r.gen === gen) sendChat(r, c.text) }, c.delaySec * 1000))
  }
  const am = cfg.autoMessage
  if (am.enabled && am.text) {
    r.timers.push(setInterval(() => { if (r.gen === gen) sendChat(r, am.text) }, am.everySec * 1000))
  }
}

function connect (r) {
  const cfg = cfgOf(r.id)
  if (!cfg || !r.desired) return
  if (!bedrock) { push('err', loadError || 'Thiếu thư viện bedrock-protocol', { bot: cfg.name }); r.desired = false; setStatus(r, 'stopped'); r.lastError = loadError || 'Thiếu thư viện bedrock-protocol'; broadcast({ botStatus: Object.assign({ id: r.id, status: 'stopped' }, botInfo(r)) }); return }

  const gen = ++r.gen
  const alive = () => r.gen === gen
  const name = cfg.name
  clearTimers(r)
  r.reconnecting = false
  r.ready = false
  r.players.clear()
  r.online.clear()
  r.pos = null
  dirtySnap.add(r.id)
  dirtyOnline = true
  setStatus(r, 'connecting')

  const retry = () => {
    if (!alive() || r.reconnecting) return
    r.reconnecting = true
    r.ready = false
    clearTimers(r)
    try { if (r.client) r.client.close() } catch (e) { /* bỏ qua */ }
    if (!r.desired) return
    const o = config.options
    if (!r.lastError) r.lastError = 'Server đã đóng kết nối'
    if (!o.autoReconnect) { setStatus(r, 'lost'); push('sys', 'Mất kết nối (tự kết nối lại đang tắt)', { bot: name }); return }
    r.failCount++
    const base = Math.min(o.reconnectBaseSec * r.failCount, o.reconnectMaxSec)
    const wait = base + Math.round(base * 0.15 * Math.random()) // lệch nhẹ để nhiều bot không dồn vào cùng lúc
    r.retryAt = Date.now() + wait * 1000
    setStatus(r, 'lost')
    push('sys', `Thử lại sau ${wait}s...`, { bot: name })
    r.retryTimer = setTimeout(() => { if (alive() && r.desired) connect(r) }, wait * 1000)
  }

  let c
  try {
    c = bedrock.createClient(clientOptions(cfg, effServer(cfg)))
  } catch (e) {
    // Lỗi cấu hình (vd phiên bản không hỗ trợ) thì thử lại cũng vô ích -> dừng hẳn
    push('err', 'Không tạo được kết nối: ' + withHint(e.message), { bot: name })
    r.desired = false
    r.client = null
    setStatus(r, 'stopped')
    r.lastError = withHint(e.message)
    broadcast({ botStatus: Object.assign({ id: r.id, status: 'stopped' }, botInfo(r)) })
    return
  }
  r.client = c

  c.on('join', () => { if (alive()) push('sys', 'Đã vào server', { bot: name }) })

  c.on('spawn', () => {
    if (!alive()) return
    r.ready = true
    r.failCount = 0
    setStatus(r, 'online')
    push('sys', 'Đã spawn', { bot: name })
    onSpawn(r, gen)
  })

  c.on('start_game', (p) => {
    if (!alive()) return
    meta.seed = String(p.seed)
    meta.world = p.world_name
    broadcast({ seed: meta.seed, world: meta.world })
    r.selfId = String(p.runtime_entity_id)
    r.pos = xyz(p.player_position)
    dirtySnap.add(r.id)
    push('sys', `World: ${p.world_name} | Seed: ${p.seed} | Dimension: ${p.dimension}`, { bot: name })
  })

  c.on('player_list', (p) => {
    if (!alive()) return
    const rec = p.records
    const type = rec && rec.type
    const list = (rec && rec.records) || (Array.isArray(rec) ? rec : [])
    for (const x of list) {
      const id = String(x.uuid)
      if (type === 'remove') r.online.delete(id)
      else if (x.username) r.online.set(id, x.username)
    }
    dirtyOnline = true
  })

  c.on('text', (p) => {
    if (!alive() || leader() !== r) return
    if ((p.type === 'chat' || p.type === 'whisper') && ours().has(p.source_name)) return
    if (p.type === 'chat') push('chat', p.message, { who: p.source_name })
    else if (p.type === 'whisper') push('chat', '(thì thầm) ' + p.message, { who: p.source_name })
    else if (p.type === 'translation') push('sys', p.message + ' ' + (p.parameters || []).join(', '))
    else push('sys', `[${p.type}] ${p.message}`)
  })

  c.on('add_player', (p) => {
    if (!alive()) return
    const pos = xyz(p.position)
    if (!pos || p.username === name) return
    r.players.set(String(p.runtime_id), { name: p.username, pos, uid: String(p.unique_id ?? p.entity_unique_id ?? ''), t: Date.now() })
    dirtySnap.add(r.id)
    push('sys', `${p.username} xuất hiện gần bot (${fmt(pos)})`, { bot: name })
  })

  c.on('move_player', (p) => {
    if (!alive()) return
    const pos = xyz(p.position)
    if (!pos) return
    const id = String(p.runtime_id)
    if (id === r.selfId) { r.pos = pos; dirtySnap.add(r.id); return }
    const e = r.players.get(id)
    if (e) { e.pos = pos; e.t = Date.now(); dirtySnap.add(r.id) }
  })

  c.on('remove_entity', (p) => {
    if (!alive()) return
    const id = String(p.entity_id_self ?? p.entity_unique_id ?? '')
    for (const [k, e] of r.players) {
      if (e.uid === id || k === id) {
        r.players.delete(k)
        dirtySnap.add(r.id)
        push('sys', `${e.name} rời khỏi vùng của bot`, { bot: name })
      }
    }
  })

  c.on('kick', (reason) => {
    if (!alive()) return
    const why = String((reason && (reason.message || reason.reason)) || JSON.stringify(reason))
    r.lastError = 'Bị kick: ' + withHint(why)
    push('err', r.lastError, { bot: name })
  })
  c.on('error', (e) => {
    if (!alive()) return
    const msg = String((e && e.message) || e)
    r.lastError = withHint(msg)
    push('err', 'Lỗi: ' + r.lastError, { bot: name })
    retry()
  })
  c.on('close', () => { if (alive()) retry() })
}

function startBot (id) {
  const cfg = cfgOf(id)
  if (!cfg) return
  const r = rtOf(cfg)
  if (r.status === 'online' || r.status === 'connecting') { r.desired = true; return }
  r.desired = true
  r.failCount = 0
  clearTimeout(r.retryTimer)
  connect(r)
}

function stopBot (id) {
  const r = rts.get(id)
  if (!r) return
  r.desired = false
  r.gen++ // vô hiệu hóa mọi sự kiện của kết nối cũ
  clearTimers(r)
  clearTimeout(r.retryTimer)
  const c = r.client
  r.client = null
  r.ready = false
  if (c) { try { c.close() } catch (e) { /* bỏ qua */ } }
  r.players.clear()
  r.online.clear()
  r.pos = null
  dirtySnap.add(id)
  dirtyOnline = true
  setStatus(r, 'stopped')
}

function restartBot (id) {
  stopBot(id)
  setTimeout(() => startBot(id), 800)
}

function startMany (list) {
  const gap = config.options.staggerSec * 1000
  list.forEach((b, i) => setTimeout(() => startBot(b.id), i * gap))
}

// ---------- lệnh từ giao diện ----------
const fail = (error) => ({ ok: false, error })
const withTimeout = (p, ms) => Promise.race([p, new Promise((resolve, reject) => setTimeout(() => reject(new Error('hết thời gian chờ')), ms))])

async function handleCmd (m) {
  switch (m.cmd) {
    case 'saveSettings': {
      const before = JSON.stringify(config.server)
      const nextServer = cleanServer(m.server, config.server)
      if (!nextServer.host) return fail('Địa chỉ server đang trống')
      config.server = nextServer
      config.options = cleanOptions(m.options, config.options)
      saveConfig()
      if (before !== JSON.stringify(config.server)) {
        // đổi server/phiên bản -> kết nối lại các bot đang chạy dùng cấu hình chung
        const run = config.bots.filter((b) => b.useGlobal && rts.get(b.id) && rts.get(b.id).desired)
        run.forEach((b) => stopBot(b.id))
        startMany(run)
      }
      broadcastState()
      return { ok: true }
    }
    case 'addBot': {
      const name = S(m.name).slice(0, 32)
      if (!name) return fail('Tên bot đang trống')
      if (nameTaken(name)) return fail('Tên bot này đã có')
      if (config.bots.length >= 50) return fail('Tối đa 50 bot')
      const b = newBotCfg(name)
      b.id = newId()
      config.bots.push(b)
      saveConfig()
      broadcastState()
      return { ok: true, id: b.id }
    }
    case 'addBots': {
      const prefix = S(m.prefix).slice(0, 26)
      const count = N(m.count, 0, 0, 50)
      if (!prefix) return fail('Chưa nhập tên bot')
      if (!count) return fail('Số lượng bot không hợp lệ')
      if (config.bots.length + count > 50) return fail('Chỉ còn thêm được ' + (50 - config.bots.length) + ' bot (tối đa 50)')
      let made = 0
      for (let i = 1; made < count && i < 1000; i++) {
        const name = (prefix + i).slice(0, 32)
        if (nameTaken(name)) continue
        const b = newBotCfg(name)
        while (config.bots.some((x) => x.id === b.id)) b.id = newId()
        config.bots.push(b)
        made++
      }
      saveConfig()
      broadcastState()
      return { ok: true, added: made }
    }
    case 'setUi': {
      config.ui = cleanUi(Object.assign({}, config.ui, m.ui || {}), config.ui)
      saveConfig()
      broadcastState()
      return { ok: true }
    }
    case 'updateBot': {
      const cur = cfgOf(m.id)
      if (!cur) return fail('Không thấy bot')
      const nb = cleanBot({ ...cur, ...(m.patch || {}), id: cur.id })
      if (!nb) return fail('Tên bot đang trống')
      if (nameTaken(nb.name, cur.id)) return fail('Tên bot này đã có')
      const key = (b) => JSON.stringify([b.name, b.useGlobal, b.server, b.joinCommands, b.autoMessage])
      const changed = key(cur) !== key(nb)
      Object.assign(cur, nb)
      saveConfig()
      const r = rts.get(cur.id)
      if (changed && r && r.desired) restartBot(cur.id)
      broadcastState()
      return { ok: true }
    }
    case 'removeBot': {
      if (!cfgOf(m.id)) return fail('Không thấy bot')
      stopBot(m.id)
      rts.delete(m.id)
      config.bots = config.bots.filter((b) => b.id !== m.id)
      saveConfig()
      broadcastState()
      return { ok: true }
    }
    case 'startBot': startBot(m.id); return { ok: true }
    case 'stopBot': stopBot(m.id); return { ok: true }
    case 'restartBot': restartBot(m.id); return { ok: true }
    case 'startAll': startMany(config.bots); return { ok: true }
    case 'stopAll': config.bots.forEach((b) => stopBot(b.id)); return { ok: true }
    case 'send': {
      const msg = S(m.message).slice(0, 200)
      if (!msg) return fail('Tin nhắn đang trống')
      if (m.bot === 'all') {
        let n = 0
        for (const b of config.bots) if (sendChat(rts.get(b.id), msg)) n++
        return n ? { ok: true } : fail('Chưa có bot nào vào server')
      }
      return sendChat(rts.get(String(m.bot)), msg) ? { ok: true } : fail('Bot này chưa vào server')
    }
    case 'pingServer': {
      if (!bedrock) return fail(loadError || 'Thiếu thư viện bedrock-protocol')
      const host = S(m.host)
      const port = N(m.port, 19132, 1, 65535)
      if (!host) return fail('Chưa nhập địa chỉ server')
      try {
        const r = await withTimeout(bedrock.ping({ host, port, raknetBackend: 'jsp-raknet' }), 7000)
        return { ok: true, info: { motd: String(r.motd || r.name || ''), version: String(r.version || ''), protocol: r.protocol, online: r.playersOnline, max: r.playersMax, level: String(r.levelName || '') } }
      } catch (e) { return fail('Không ping được: ' + e.message) }
    }
    case 'importConfig': {
      let raw
      try { raw = JSON.parse(String(m.json)) } catch (e) { return fail('JSON không hợp lệ') }
      if (raw && typeof raw === 'object' && !raw.ui) raw.ui = config.ui
      config.bots.forEach((b) => stopBot(b.id))
      rts.clear()
      config = cleanConfig(raw)
      saveConfig()
      broadcastState()
      return { ok: true }
    }
    case 'clearLog': events.length = 0; broadcast({ cleared: true }); return { ok: true }
    default: return fail('Lệnh không hợp lệ')
  }
}

// ---------- HTTP (chỉ nghe 127.0.0.1, bắt buộc có token) ----------
function readBody (req, limit, cb) {
  let body = ''
  let dead = false
  req.on('data', (d) => { body += d; if (body.length > limit && !dead) { dead = true; req.destroy() } })
  req.on('end', () => { if (!dead) cb(body) })
}

const server = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x')
  if (u.pathname === '/favicon.ico') { res.writeHead(204); return res.end() }
  if (u.searchParams.get('token') !== TOKEN) { res.writeHead(401); return res.end('Sai token') }
  const p = u.pathname

  if (p === '/') {
    try {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
      return res.end(fs.readFileSync(PAGE))
    } catch (e) { res.writeHead(500); return res.end('Thiếu file giao diện: ' + e.message) }
  }
  if (p === '/api/ping') { res.writeHead(200); return res.end('ok') }
  if (p === '/api/summary') {
    const online = [...rts.values()].filter((r) => r.ready).length
    res.writeHead(200, { 'Content-Type': 'application/json' })
    return res.end(JSON.stringify({ online, total: config.bots.length }))
  }
  if (p === '/events') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' })
    const snaps = {}
    for (const b of config.bots) { const r = rts.get(b.id); if (r) snaps[b.id] = snapshot(r) }
    res.write('data: ' + JSON.stringify({ init: true, state: publicState(), events, snaps, online: onlineList() }) + '\n\n')
    listeners.add(res)
    req.on('close', () => listeners.delete(res))
    return
  }
  if (p === '/api/cmd' && req.method === 'POST') {
    return readBody(req, 300000, (body) => {
      let m
      try { m = JSON.parse(body) } catch (e) { res.writeHead(400); return res.end('{"ok":false,"error":"Dữ liệu lỗi"}') }
      handleCmd(m).then((out) => {
        res.writeHead(200, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify(out))
      }).catch((e) => {
        res.writeHead(200, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify(fail('Lỗi nội bộ: ' + e.message)))
      })
    })
  }
  res.writeHead(404)
  res.end()
})

config = loadConfig()
saveConfig()
if (loadError) push('err', loadError)

server.on('error', (e) => push('err', 'Lỗi máy chủ nội bộ: ' + e.message))
server.listen(PORT, '127.0.0.1', () => {
  push('sys', `MC Bot Manager sẵn sàng (Node ${process.versions.node}, ${Object.keys(Versions).length} phiên bản Minecraft được hỗ trợ)`)
  if (config.options.autoStart) {
    // Lần đầu dùng: chưa chạy bot nào để tránh vào nhầm server mẫu trước khi người dùng nhập server của mình
    if (config.ui.setupDone) startMany(config.bots.filter((b) => b.enabled))
    else push('sys', 'Lần đầu dùng: bot chưa tự chạy. Vào tab Bot, làm theo hướng dẫn rồi bấm Chạy.')
  }
})
