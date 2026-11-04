(() => {
 document.getElementById('search-sort')?.addEventListener('change', event => event.target.form.requestSubmit());
 const owner=document.querySelector('meta[name="bookmark-user"]')?.content||'';
 const STORE='aptfolio.saved.molit.user.v1.'+encodeURIComponent(owner); let entries=[],pending=false;
 try {if(owner){const raw=JSON.parse(localStorage.getItem(STORE)||'[]');if(Array.isArray(raw))entries=raw.filter(x=>x&&typeof x.key==='string'&&Number.isSafeInteger(Number(x.id))&&Number(x.id)>0&&typeof x.name==='string'&&typeof x.location==='string');}}catch{}
 const toast=(text)=>{const el=document.getElementById('toast');if(!el)return;el.textContent=text;el.classList.add('show');setTimeout(()=>el.classList.remove('show'),2200);};
 const authorized=async()=>{
  if(!owner){window.location.assign('/login');return false;}
  try {
   const response=await fetch('/api/account/session',{credentials:'same-origin',cache:'no-store'});
   if(response.status===401||response.status===403){window.location.assign('/login');return false;}
   if(!response.ok)throw new Error();
   const current=await response.json();
   if(current.username!==owner){window.location.reload();return false;}
   return true;
  }catch{toast('로그인 상태를 확인할 수 없어요. 잠시 후 다시 시도해주세요.');return false;}
 };
 const change=async(action)=>{if(pending)return;pending=true;try{if(await authorized())action();}finally{pending=false;}};
 const persist=()=>{try{localStorage.setItem(STORE,JSON.stringify(entries));return true;}catch{toast('브라우저 저장 공간을 사용할 수 없어요.');return false;}};
 const render=()=>{
  const count=document.getElementById('saved-count');if(count)count.textContent=entries.length;
  document.querySelectorAll('.bookmark').forEach(b=>{const on=entries.some(x=>x.key===b.dataset.key);b.textContent=on?'★':'☆';b.setAttribute('aria-pressed',String(on));b.setAttribute('aria-label',on?'관심 아파트 해제':'관심 아파트 등록');});
  const list=document.getElementById('saved-list');if(!list)return;list.replaceChildren();document.getElementById('saved-empty').hidden=entries.length>0;
  entries.forEach(item=>{const card=document.createElement('article');card.className='saved-card';const location=document.createElement('p');location.textContent=item.location;const a=document.createElement('a');a.href='/trades/'+Number(item.id);a.textContent=item.name;const remove=document.createElement('button');remove.className='secondary';remove.textContent='관심 해제';remove.onclick=()=>change(()=>{const old=entries;entries=entries.filter(x=>x.key!==item.key);if(!persist())entries=old;render();});card.append(location,a,remove);list.append(card);});
 };
 document.querySelectorAll('.bookmark').forEach(b=>b.addEventListener('click',()=>change(()=>{const old=entries;const exists=entries.some(x=>x.key===b.dataset.key);entries=exists?entries.filter(x=>x.key!==b.dataset.key):[...entries,{key:b.dataset.key,id:b.dataset.id,name:b.dataset.name,location:b.dataset.location}];if(!persist())entries=old;else toast(exists?'관심 아파트에서 해제했어요.':'관심 아파트에 저장했어요.');render();})));render();
 window.addEventListener('pageshow',event=>{if(event.persisted)window.location.reload();});
})();
