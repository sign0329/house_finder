(() => {
 const root=document.getElementById('apartment-map'), button=document.getElementById('show-map');
 if(!root || !button || !root.dataset.mapKey) return;
 const status=document.getElementById('map-status'), canvas=document.getElementById('map-canvas');
 button.addEventListener('click', () => {
  button.disabled=true; status.textContent='지도를 불러오는 중입니다…';
  let finished=false;
  const fail=() => { if(finished)return; finished=true; clearTimeout(timer); canvas.hidden=true;
   status.textContent='위치를 확인할 수 없습니다. 카카오맵에서 주소를 검색해 주세요.'; };
  const timer=setTimeout(fail,15000);
  const script=document.createElement('script');
  script.src='https://dapi.kakao.com/v2/maps/sdk.js?appkey='+encodeURIComponent(root.dataset.mapKey)+'&autoload=false&libraries=services';
  script.onerror=fail;
  script.onload=() => {
   if(finished)return;
   try { window.kakao.maps.load(() => {
    if(finished)return;
    try { const maps=window.kakao.maps;
     new maps.services.Geocoder().addressSearch(root.dataset.address,(results,code) => {
      if(finished)return;
      if(code!==maps.services.Status.OK || results.length!==1)return fail();
      const {x,y}=results[0], lat=Number(y), lng=Number(x);
      if(!x || !y || !Number.isFinite(lat) || !Number.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180)return fail();
      try { canvas.hidden=false;
       const position=new maps.LatLng(lat,lng), map=new maps.Map(canvas,{center:position,level:3});
       new maps.Marker({map,position});
       finished=true;clearTimeout(timer);status.textContent='등록된 주소의 검색 위치입니다.';
      } catch(e){fail();}
     },{analyze_type:maps.services.AnalyzeType.EXACT});
    } catch(e){fail();}
   }); } catch(e){fail();}
  };
  document.head.appendChild(script);
 },{once:true});
})();
