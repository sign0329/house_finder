const {test}=require('node:test');const assert=require('node:assert/strict');const vm=require('node:vm');const fs=require('node:fs');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/main/resources/static/js/detail-map.js'),'utf8');
function setup(results){let click,script,markers=0;const status={},canvas={hidden:true};const button={addEventListener:(e,f)=>click=f};
 const nodes={'apartment-map':{dataset:{mapKey:'test',address:'주소'}},'show-map':button,'map-status':status,'map-canvas':canvas};
 const maps={load:f=>f(),services:{Status:{OK:'OK'},AnalyzeType:{EXACT:'EXACT'},Geocoder:class{addressSearch(a,cb){cb(results,'OK');}}},LatLng:class{},Map:class{},Marker:class{constructor(){markers++;}}};
 vm.runInNewContext(source,{document:{getElementById:id=>nodes[id],createElement:()=>({}),head:{appendChild:s=>script=s}},window:{kakao:{maps}},setTimeout:()=>1,clearTimeout:()=>{}});
 return {click:()=>click(),load:()=>script.onload(),error:()=>script.onerror(),get script(){return script},get markers(){return markers},canvas,status};}
test('SDK loads only on click and unique coordinates create one marker',()=>{const s=setup([{x:'127',y:'37'}]);assert.equal(s.script,undefined);s.click();s.load();assert.equal(s.markers,1);assert.equal(s.canvas.hidden,false);});
test('zero, ambiguous and invalid coordinates never create a marker',()=>{for(const rows of [[],[{x:'127',y:'37'},{x:'128',y:'38'}],[{x:'NaN',y:'37'}]]){const s=setup(rows);s.click();s.load();assert.equal(s.markers,0);assert.equal(s.canvas.hidden,true);}});
test('SDK failure preserves fallback and ignores late success',()=>{const s=setup([{x:'127',y:'37'}]);s.click();s.error();s.load();assert.equal(s.markers,0);assert.match(s.status.textContent,/카카오맵/);});
