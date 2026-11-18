const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {runInNewContext} = require('node:vm');
const source = readFileSync('src/main/resources/static/js/forms.js', 'utf8');
function setup(values, method='get') {
  const fields = Object.fromEntries(Object.entries(values).map(([key,value]) => [key,{value,error:'',setCustomValidity(message){this.error=message;}}]));
  const events={}, windowEvents={}, status={textContent:''}, button={disabled:false};
  const form={method,dataset:{},elements:{namedItem:name=>fields[name]},
    querySelector:()=>status,querySelectorAll:()=>[button],
    addEventListener:(name,fn)=>events[name]=fn,setAttribute(){},removeAttribute(){},
    reportValidity:()=>Object.values(fields).every(field=>!field.error)};
  runInNewContext(source,{TextEncoder,document:{querySelectorAll:()=>[form]},window:{addEventListener:(name,fn)=>windowEvents[name]=fn}});
  const submit=()=>{let prevented=false;events.submit({preventDefault(){prevented=true;}});return prevented;};
  return {fields,events,windowEvents,form,status,button,submit};
}
test('reversed price and date ranges block submission and recover after correction',()=>{
  const ui=setup({minPrice:'90000',maxPrice:'10000',from:'2026-10-01',to:'2026-09-01'});
  assert.equal(ui.submit(),true);assert.match(ui.fields.maxPrice.error,/최대 가격/);assert.match(ui.fields.to.error,/종료일/);
  ui.fields.maxPrice.value='90000';ui.fields.to.value='2026-10-01';ui.events.input();
  assert.equal(ui.submit(),false);
});
test('signup rejects mismatched confirmation and excessive UTF-8 byte length',()=>{
  const ui=setup({password:'가'.repeat(25),confirmPassword:'different'},'post');
  assert.equal(ui.submit(),true);assert.match(ui.fields.password.error,/72바이트/);assert.match(ui.fields.confirmPassword.error,/일치/);
  ui.fields.password.value='가'.repeat(24);ui.fields.confirmPassword.value=ui.fields.password.value;ui.events.input();
  assert.equal(ui.submit(),false);
});
test('request feedback prevents duplicate submission and resets on back navigation',()=>{
  const ui=setup({});assert.equal(ui.submit(),false);assert.equal(ui.button.disabled,true);
  assert.match(ui.status.textContent,/검색/);assert.equal(ui.submit(),true);
  ui.windowEvents.pageshow();assert.equal(ui.button.disabled,false);assert.equal(ui.status.textContent,'');assert.equal(ui.submit(),false);
});
test('comment limit counts Unicode characters, blocks 501 and recovers at 500',()=>{
  const events={}, formEvents={}, windowEvents={};
  const classes=()=>({toggle(name,on){this[name]=on;}});
  const count={classList:classes()}, button={disabled:false};
  const form={dataset:{},querySelector:s=>s==='#comment-count'?count:button,addEventListener:(n,f)=>formEvents[n]=f};
  const input={value:'가'.repeat(500),dataset:{commentLimit:'500'},form,classList:classes(),setAttribute(n,v){this[n]=v;},setCustomValidity(v){this.error=v;},addEventListener:(n,f)=>events[n]=f};
  runInNewContext(readFileSync('src/main/resources/static/js/comment-limit.js','utf8'),{document:{querySelectorAll:()=>[input]},window:{addEventListener:(n,f)=>windowEvents[n]=f}});
  assert.equal(button.disabled,false);
  input.value+='나';events.input();assert.equal(button.disabled,true);assert.equal(input.classList['comment-over-limit'],true);assert.match(count.textContent,/501/);
  let blocked=false;formEvents.submit({preventDefault(){blocked=true;}});assert.equal(blocked,true);
  input.value='😀'.repeat(500);events.input();assert.equal(button.disabled,false);assert.equal(input.error,'');assert.equal(input.classList['comment-over-limit'],false);
  form.dataset.pending='true';events.input();assert.equal(button.disabled,true);
});
