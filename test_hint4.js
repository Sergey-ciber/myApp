const http = require('http');
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
  const fs = require('fs');
  const reg = JSON.parse(r1.body);
  const token = reg.token;
  const userId = reg.user.id;
  const t = await req('POST', '/api/teams', { name: 'Ком' + (uniq % 100000), description: 'x' }, token);
  const teamId = JSON.parse(t.body).id;
  const gamesR = await req('GET', '/api/games', null, token);
  const games = JSON.parse(gamesR.body);
  const gameId = games[0].id;
  const start = await req('POST', '/api/team-games/start', { teamId: teamId, gameId: gameId }, token);
  fs.writeFileSync('C:/projects/DemoProject/hint_test4.txt', 'start:'+start.status+'\n'+start.body.slice(0,250));
  if (start.status !== 200) return;
  const sessionId = JSON.parse(start.body).sessionId;
  const st = await req('GET', '/api/team-games/' + sessionId + '/state', null, token);
  fs.appendFileSync('C:/projects/DemoProject/hint_test4.txt', '\nstate:'+st.status);
  const state = JSON.parse(st.body);
  const q = state.game.levels[0].questions[0];
  const h = await req('POST', '/api/team-games/' + sessionId + '/hint', { sessionId: sessionId, questionId: q.id, hintIndex: 0 }, token);
  const a = await req('POST', '/api/team-games/' + sessionId + '/answer', { sessionId: sessionId, questionId: q.id, answer: 'Яблоко' }, token);
  const st2 = await req('GET', '/api/team-games/' + sessionId + '/state', null, token);
  const s2 = JSON.parse(st2.body);
  fs.appendFileSync('C:/projects/DemoProject/hint_test4.txt', '\nhint:'+h.status+'\nanswer:'+a.status+'\npenalty:'+s2.penaltyTime+'\nsolved:'+Object.keys(s2.solved||{}).length);
})();
