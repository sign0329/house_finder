(() => {
 document.getElementById('search-sort')?.addEventListener('change',event=>event.target.form.requestSubmit());
 const owner=document.querySelector('meta[name="bookmark-user"]')?.content||'';
 const endpoint='/api/account/bookmarks';let entries=[],pending=false,ready=false;
 const toast=text=>{const el=document.getElementById('toast');if(!el)return;el.textContent=text;el.classList.add('show');setTimeout(()=>el.classList.remove('show'),2500);};
 async function request(path='',method='GET',body){
  const headers={};if(method!=='GET'){
   headers[document.querySelector('meta[name="csrf-header"]').content]=document.querySelector('meta[name="csrf-token"]').content;
   if(body)headers['Content-Type']='application/json';
  }
  const res=await fetch(endpoint+path,{method,headers,credentials:'same-origin',cache:'no-store',...(body?{body:JSON.stringify(body)}:{})});
  if(res.status===401){window.location.assign('/login');throw new Error('다시 로그인해주세요.');}
  if(!res.ok)throw new Error(({403:'로그인 상태가 바뀌었어요. 새로고침해주세요.',404:'현재 조회할 수 없는 거래 또는 관심 항목입니다.',409:'관심 아파트는 최대 200개까지 저장할 수 있어요.'})[res.status]||'요청을 처리하지 못했어요. 잠시 후 다시 시도해주세요.');
  return res.status===204?null:res.json();
 }
 function render(){
  const count=document.getElementById('saved-count');if(count)count.textContent=ready?entries.length:'—';
  document.querySelectorAll('.bookmark').forEach(b=>{const on=entries.some(x=>x.key===b.dataset.key);b.textContent=on?'★':'☆';b.disabled=pending||!ready;b.setAttribute('aria-pressed',String(on));b.setAttribute('aria-label',on?'관심 아파트 해제':'관심 아파트 등록');});
  const list=document.getElementById('saved-list');if(!list)return;list.replaceChildren();
  document.getElementById('saved-empty').hidden=!ready||entries.length>0;
  const status=document.getElementById('saved-status');if(status)status.textContent=ready?'최대 200개 · 계정에 저장된 목록입니다.':'관심 목록을 불러오는 중입니다.';
  for(const item of entries){
   const card=document.createElement('article');card.className='saved-card';
   const location=document.createElement('p');location.textContent=item.location;
   const name=document.createElement(item.tradeId?'a':'strong');name.textContent=item.name;
   if(item.tradeId)name.href='/trades/'+Number(item.tradeId);
   const remove=document.createElement('button');remove.className='secondary';remove.textContent='관심 해제';remove.disabled=pending;
   remove.onclick=()=>change(()=>request('/'+item.id,'DELETE'));
   card.append(location,name);
   if(!item.tradeId){const missing=document.createElement('p');missing.textContent='현재 조회 가능한 거래가 없습니다.';card.append(missing);}
   card.append(remove);list.append(card);
  }
 }
 async function load(){
  const result=await request();
  if(result.username!==owner){window.location.reload();throw new Error('계정이 변경됐습니다.');}
  entries=result.items;ready=true;render();
 }
 async function change(action){
  if(!owner){window.location.assign('/login');return;}
  if(pending||!ready)return;pending=true;render();
  try{
   await action();
   try{await load();toast('관심 목록을 업데이트했어요.');}
   catch(e){ready=false;throw new Error('저장 요청은 완료됐지만 목록을 불러오지 못했어요. 새로고침해주세요.');}
  }catch(e){toast(e.message||'요청에 실패했어요.');}
  finally{pending=false;render();if(!ready){const status=document.getElementById('saved-status');if(status)status.textContent='목록 갱신 실패. 페이지를 새로고침해주세요.';}}
 }
 document.querySelectorAll('.bookmark').forEach(b=>b.addEventListener('click',()=>{
  const existing=entries.find(x=>x.key===b.dataset.key);
  return change(()=>existing?request('/'+existing.id,'DELETE'):request('','POST',{tradeId:Number(b.dataset.id)}));
 }));
 render();
 if(owner)load().catch(e=>{toast(e.message||'목록을 불러오지 못했어요.');const status=document.getElementById('saved-status');if(status)status.textContent='목록 조회 실패. 페이지를 새로고침해주세요.';});
 window.addEventListener('pageshow',event=>{if(event.persisted)window.location.reload();});
})();
