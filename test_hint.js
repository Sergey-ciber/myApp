const crypto = require('crypto');
const http = require('http');
function b64u(buf) { return Buffer.from(buf).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_'); }
function jwt(sub) {
  const h = b64u(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const now = Math.floor(Date.now() / 1000);
  const p = b64u(JSON.stringify({ sub: String(sub), iat: now, exp: now + 86400 }));
  const sig = crypto.createHmac('sha256', 'super-secret-key-for-jwt-token-generation-must-be-long-enough-1234567890').update(h + '.' + p).digest();
  return h + '.' + p + '.' + b64u(sig);
}
function req(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers.Authorization = 'Bearer ' + token;
    const r = http.request({ hostname: '127.0.0.1', port: 8080, path: path, method: method, headers: headers, timeout: 15000 }, (res) => {
      let b = '';
      res.on('data', d => b += d);
      res.on('end', () => resolve({ status: res.statusCode, body: b }));
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}
(async () => {
  const uniq = Date.now();
  const u1 = 'ТестХ' + uniq;
  const r1 = await req('POST', '/api/auth/register', { username: u1, password: 'Password123!' });
  const userId = JSON.parse(r1.body).user.id;
  const t = await req('POST', '/api/teams', { name: 'Ком' + (uniq % 100000), description: 'x' }, jwt(userId));
  const teamId = JSON.parse(t.body).id;
  const games = JSON.parse((await req('GET', '/api/games', null, jwt(userId))).body);
  const gameId = games[0].id;
  const start = await req('POST', '/api/team-games/start', { teamId: teamId, gameId: gameId }, jwt(userId));
  const sessionId = JSON.parse(start.body).sessionId;
  const st = await req('GET', '/api/team-games/' + sessionId + '/state', null, jwt(userId));
  const q = JSON.parse(st.body).game.levels[0].questions[0];
  const h = await req('POST', '/api/team-games/' + sessionId + '/hint', { sessionId: sessionId, questionId: q.id, hintIndex: 0 }, jwt(userId));
  const a = await req('POST', '/api/team-games/' + sessionId + '/answer', { sessionId: sessionId, questionId: q.id, answer: 'Яблоко' }, jwt(userId));
  const st2 = await req('GET', '/api/team-games/' + sessionId + '/state', null, jwt(userId));
  const s2 = JSON.parse(st2.body);
  const fs = require('fs');
  fs.writeFileSync('C:/projects/DemoProject/hint_test.txt', 'hint:' + h.status + '\nanswer:' + a.status + '\npenalty:' + s2.penaltyTime + '\nsolved:' + Object.keys(s2.solved || {}).length);
})();
