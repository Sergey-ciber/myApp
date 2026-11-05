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
  const token = JSON.parse(r1.body).token;
  const t = await req('POST', '/api/teams', { name: 'Ком' + (uniq % 100000), description: 'x' }, token);
  const teamId = JSON.parse(t.body).id;
  const gamesR = await req('GET', '/api/games', null, token);
  const games = JSON.parse(gamesR.body);
  const gameId = games[0].id;
  const start = await req('POST', '/api/team-games/start', { teamId: teamId, gameId: gameId }, token);
  const sessionId = JSON.parse(start.body).sessionId;
  const fs = require('fs');
  fs.writeFileSync('C:/projects/DemoProject/test_nav.txt', 'started:'+sessionId+'\n');
  const active = await req('GET', '/api/team-games/active', null, token);
  fs.appendFileSync('C:/projects/DemoProject/test_nav.txt', 'active:'+active.status+' '+active.body.slice(0,80)+'\n');
  const state = await req('GET', '/api/team-games/' + sessionId + '/state', null, token);
  fs.appendFileSync('C:/projects/DemoProject/test_nav.txt', 'state:'+state.status);
})();
