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
  const u1 = 'ХостJ'+uniq, u2 = 'УчастникJ'+uniq;
  const ids = {};
  for (const u of [u1,u2]) {
    const r = await req('POST','/api/auth/register',{username:u,password:'Password123!'});
    ids[u] = JSON.parse(r.body).user.id;
  }
  const t1 = await req('POST','/api/auth/login',{username:u1,password:'Password123!'});
  const token1 = JSON.parse(t1.body).token;
  const t2 = await req('POST','/api/auth/login',{username:u2,password:'Password123!'});
  const token2 = JSON.parse(t2.body).token;
  const team = await req('POST','/api/teams',{name:'КомJ'+(uniq%10000),description:'x'},token1);
  const teamId = JSON.parse(team.body).id;
  const inv = await req('POST','/api/teams/'+teamId+'/invite',{userId:ids[u2]},token1);
  await req('POST','/api/invitations/'+JSON.parse(inv.body).id+'/accept',null,token2);
  const gamesR = await req('GET','/api/games',null,token1);
  const games = JSON.parse(gamesR.body);
  const gameId = games[0].id;
  const start = await req('POST','/api/team-games/start',{teamId,gameId},token1);
  const sessionId = JSON.parse(start.body).sessionId;
  const join = await req('POST','/api/team-games/'+sessionId+'/join',null,token2);
  const js = JSON.parse(join.body);
  const fs = require('fs');
  fs.writeFileSync('C:/projects/DemoProject/join_test.txt', 'status:'+join.status+'\nhasGame:'+(!!js.game)+'\nlevels:'+(js.game&&js.game.levels?js.game.levels.length:'null')+'\ncur:'+js.currentLevelIndex);
})();
