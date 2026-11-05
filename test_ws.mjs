import crypto from 'crypto';
import http from 'http';
import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const SockJS = require('sockjs-client');
const { Client } = require('@stomp/stompjs');

function b64u(buf){return Buffer.from(buf).toString('base64').replace(/=/g,'').replace(/\+/g,'-').replace(/\//g,'_');}
function jwt(sub){const h=b64u(JSON.stringify({alg:'HS256',typ:'JWT'}));const now=Math.floor(Date.now()/1000);const p=b64u(JSON.stringify({sub,iat:now,exp:now+86400}));const sig=crypto.createHmac('sha256','super-secret-key-for-jwt-token-generation-must-be-long-enough-1234567890').update(h+'.'+p).digest();return h+'.'+p+'.'+b64u(sig);}
function req(method,path,body,token){
  return new Promise((resolve,reject)=>{
    const data=body?JSON.stringify(body):null;
    const headers={'Content-Type':'application/json'};
    if(token) headers.Authorization='Bearer '+token;
    const r=http.request({hostname:'127.0.0.1',port:8080,path,method,headers,timeout:15000},(res)=>{
      let b='';res.on('data',d=>b+=d);res.on('end',()=>resolve({status:res.statusCode,body:b}));
    });
    r.on('error',reject);
    if(data) r.write(data);
    r.end();
  });
}

(async()=>{
  const out=[];
  const uniq=Date.now();
  const u1='ХостW'+uniq, u2='УчастникW'+uniq;
  const ids={};
  for(const u of [u1,u2]){
    const r=await req('POST','/api/auth/register',{username:u,password:'Password123!'});
    ids[u]=JSON.parse(r.body).user.id;
  }
  const t=await req('POST','/api/teams',{name:'ТестВР2'+uniq%100000,description:'x'},jwt(u1));
  const teamId=JSON.parse(t.body).id;
  const inv=await req('POST','/api/teams/'+teamId+'/invite',{userId:ids[u2]},jwt(u1));
  await req('POST','/api/invitations/'+JSON.parse(inv.body).id+'/accept',null,jwt(u2));
  const games=JSON.parse((await req('GET','/api/games',null,jwt(u1))).body));
  const gameId=games[0].id;
  const start=await req('POST','/api/team-games/start',{teamId,gameId},jwt(u1));
  const sessionId=JSON.parse(start.body).sessionId;
  await req('POST','/api/team-games/'+sessionId+'/join',null,jwt(u2));
  out.push('session='+sessionId);
  // subscribe u2 to the room
  const received=[];
  const c=new Client({
    webSocketFactory:()=>new SockJS('http://127.0.0.1:8080/ws'),
    connectHeaders:{Authorization:'Bearer '+jwt(u2)},
    reconnectDelay:0,
    onConnect:()=>{
      c.subscribe('/topic/team_session_'+sessionId,(m)=>{
        try {
          const p=JSON.parse(m.body);
          received.push({type:p.type, answer:p.answer, userId:p.userId, penaltySeconds:p.penaltySeconds, levelCompleted:p.levelCompleted});
        } catch(e) { received.push({raw:m.body}); }
      });
      out.push('u2 subscribed');
    },
    onStompError:(f)=>out.push('STOMP ERROR: '+(f.headers?.message||f.body)),
  });
  c.activate();
  await new Promise(r=>setTimeout(r,2000));
  const st=await req('GET','/api/team-games/'+sessionId+'/state',null,jwt(u2));
  const state=JSON.parse(st.body);
  const q=state.game.levels[state.currentLevelIndex].questions[0];
  out.push('question='+q.id);
  // submit correct answer from host
  const ans=await req('POST','/api/team-games/'+sessionId+'/answer',{sessionId,questionId:q.id,answer:'Яблоко'},jwt(u1));
  out.push('answer by host = '+ans.status);
  await new Promise(r=>setTimeout(r,2000));
  // hint from u2
  const hint=await req('POST','/api/team-games/'+sessionId+'/hint',{sessionId,questionId:q.id,hintIndex:0},jwt(u2));
  out.push('hint by u2 = '+hint.status);
  await new Promise(r=>setTimeout(r,2000));
  out.push('EVENTS received by u2:');
  for(const e of received) out.push('  '+JSON.stringify(e));
  c.deactivate();
  const fs=require('fs');
  fs.writeFileSync('C:/projects/DemoProject/ws_t3.txt',out.join('\n'));
})().catch(e=>{require('fs').writeFileSync('C:/projects/DemoProject/ws_t3.txt','ERR '+e.message);});
