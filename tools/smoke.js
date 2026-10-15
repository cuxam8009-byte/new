// Kiểm tra nhanh: bedrock-protocol còn chạy được sau khi dọn bớt node_modules không.
// Chạy trong thư mục nodejs-project:  node ../tools/smoke.js <phiên-bản-mc>
const path = require('path')
const req = require('module').createRequire(path.join(process.cwd(), 'noop.js'))
const bedrock = req('bedrock-protocol')
const { Versions, CURRENT_VERSION } = req('bedrock-protocol/src/options')
const want = process.argv[2]
const version = Versions[want] ? want : CURRENT_VERSION
console.log('smoke: phiên bản', version, '| tổng số phiên bản hỗ trợ:', Object.keys(Versions).length)
const c = bedrock.createClient({
  host: '127.0.0.1', port: 1, username: 'smoke', version,
  offline: true, skipPing: true, raknetBackend: 'jsp-raknet', connectTimeout: 1500
})
c.on('error', () => {})
setTimeout(() => { try { c.close() } catch (e) { /* bỏ qua */ } console.log('smoke: OK'); process.exit(0) }, 3500)
