const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../src/main/resources/static/js/app.js'),'utf8');
async function screen(server,unauthorized=false){
 let click;const calls=[],redirects=[];
 const button={dataset:{key:'apt',id:'1'},setAttribute(){},addEventListener(event,fn){click=fn;}};
 vm.runInNewContext(source,{
  document:{getElementById(){return null;},querySelector(selector){return {content:selector.includes('bookmark-user')?'alice':selector.includes('csrf-header')?'X-CSRF-TOKEN':'csrf-fixture'};},querySelectorAll(){return [button];}},
  window:{location:{assign(url){redirects.push(url);},reload(){}},addEventListener(){}},setTimeout,
  fetch:async(url,options)=>{
   calls.push({url,...options});if(unauthorized)return {status:401,ok:false};
   if(options.method==='POST')server.items=[{id:7,key:'apt',tradeId:1,name:'아파트',location:'서울'}];
   if(options.method==='DELETE')server.items=[];
   return {status:options.method==='DELETE'?204:200,ok:true,json:async()=>({username:'alice',items:server.items.map(x=>({...x}))})};
  }
 });
 await new Promise(setImmediate);return {click:()=>click(),calls,redirects,button};
}
test('expired session redirects and cannot issue bookmark mutation',async()=>{
 const view=await screen({items:[]},true);await view.click();assert.deepEqual(view.redirects,['/login']);assert.equal(view.calls.filter(c=>c.method!=='GET').length,0);
});
test('save is server backed, visible to another view, and deleted with CSRF',async()=>{
 const server={items:[]};const view=await screen(server);await view.click();assert.equal(server.items.length,1);
 const post=view.calls.find(c=>c.method==='POST');assert.equal(post.headers['X-CSRF-TOKEN'],'csrf-fixture');assert.deepEqual(JSON.parse(post.body),{tradeId:1});
 const another=await screen(server);assert.equal(another.button.textContent,'★');await another.click();assert.equal(server.items.length,0);assert.equal(another.calls.find(c=>c.method==='DELETE').url,'/api/account/bookmarks/7');
});
