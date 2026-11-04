const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../src/main/resources/static/js/app.js'),'utf8');
function screen(status,username='alice') {
 const writes=[];const redirects=[];let reloads=0,click;
 const button={dataset:{key:'apt',id:'1',name:'아파트',location:'서울'},setAttribute(){},addEventListener(event,fn){click=fn;}};
 vm.runInNewContext(source,{
  document:{getElementById(){return null;},querySelector(){return {content:'alice'};},querySelectorAll(){return [button];}},
  localStorage:{getItem(){return '[]';},setItem(...args){writes.push(args);}},
  window:{location:{assign(url){redirects.push(url);},reload(){reloads++;}},addEventListener(){}},
  fetch:async()=>({status,ok:status===200,json:async()=>({username})}),setTimeout
 });
 return {click:()=>click(),writes,redirects,reloads:()=>reloads};
}
test('expired sessions and switched users cannot mutate bookmarks',async()=>{
 const expired=screen(401);await expired.click();assert.equal(expired.writes.length,0);assert.deepEqual(expired.redirects,['/login']);
 const switched=screen(200,'bob');await switched.click();assert.equal(switched.writes.length,0);assert.equal(switched.reloads(),1);
});
test('authenticated owner stores and removes bookmarks under own key',async()=>{
 const own=screen(200);await own.click();assert.equal(own.writes[0][0],'aptfolio.saved.molit.user.v1.alice');assert.equal(JSON.parse(own.writes[0][1]).length,1);
 await own.click();assert.deepEqual(JSON.parse(own.writes[1][1]),[]);
});
