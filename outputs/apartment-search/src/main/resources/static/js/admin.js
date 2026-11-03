(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const endpoint = '/api/admin/collection-jobs';
  const statuses = {RUNNING:'실행 중', SUCCEEDED:'성공', FAILED:'실패', INTERRUPTED:'중단'};
  const failures = {FETCH_FAILED:'공공 API 호출 실패', STORE_FAILED:'저장 실패', EXECUTOR_BUSY:'다른 수집 실행 중', LEASE_EXPIRED:'작업 유효 시간 만료'};
  let token = '', jobs = [], busy = false, generation = 0;
  function message(text, error = false) { $('message').textContent = text; $('message').className = error ? 'error' : ''; }
  function disconnect() {
    generation++; token = ''; jobs = []; $('token').value = ''; $('jobs').replaceChildren(); $('coverage-rows').replaceChildren(); $('coverage-message').textContent = '조회할 지역과 기간을 입력하세요.';
    $('workspace').hidden = true; $('login-panel').hidden = false;
  }
  async function request(path = '', body) {
    const response = await fetch(endpoint + path, {method:body === undefined ? 'GET' : 'POST', cache:'no-store', credentials:'omit',
      headers:{'X-Collection-Token':token, ...(body === undefined ? {} : {'Content-Type':'application/json'})},
      ...(body === undefined ? {} : {body:JSON.stringify(body)})});
    if (!response.ok) {
      if (response.status === 401) disconnect();
      throw new Error(({400:'입력값을 확인하세요. 계약월은 현재 월까지 가능합니다.',401:'관리 토큰이 올바르지 않습니다.',404:'관리 기능이 비활성화됐거나 작업이 없습니다.',409:'실행 중이거나 최신 시도가 아닌 작업입니다. 새로고침 후 확인하세요.',503:'서버의 공공 API 인증키 설정이 필요합니다.'})[response.status] || '요청에 실패했습니다. 잠시 후 다시 시도하세요.');
    }
    return response.json();
  }
  function render() {
    $('count-all').textContent = jobs.length;
    $('count-running').textContent = jobs.filter(j => j.status === 'RUNNING').length;
    $('count-success').textContent = jobs.filter(j => j.status === 'SUCCEEDED').length;
    $('count-failed').textContent = jobs.filter(j => ['FAILED','INTERRUPTED'].includes(j.status)).length;
    const latest = new Map();
    for (const j of jobs) { const key = j.regionCode + ':' + j.month; latest.set(key, Math.max(latest.get(key) || 0, j.attempt)); }
    const filtered = jobs.filter(j => !$('status').value || j.status === $('status').value);
    $('empty').hidden = filtered.length > 0;
    $('empty').textContent = jobs.length ? '선택한 상태의 작업이 없습니다.' : '아직 수집 이력이 없습니다. 기존 거래를 복사한 기록은 여기에 표시되지 않습니다.';
    $('jobs').replaceChildren();
    for (const job of filtered) {
      const row = document.createElement('tr');
      const cell = text => { const td = document.createElement('td'); td.textContent = text; row.append(td); return td; };
      const location = cell(job.province + ' ' + job.district + ' (' + job.regionCode + ')');
      const id = document.createElement('small'); id.textContent = job.id; location.append(id);
      cell(job.month.slice(0,4) + '-' + job.month.slice(4));
      const badge = document.createElement('span'); badge.textContent = statuses[job.status] || job.status; badge.className = 'badge ' + (statuses[job.status] ? job.status : ''); cell('').append(badge);
      cell(job.attempt + '회'); cell(job.saved + ' / ' + job.cancelled);
      const time = cell((job.startedAt || '').replace('T',' ')); const end = document.createElement('small'); end.textContent = job.finishedAt ? job.finishedAt.replace('T',' ') : '—'; time.append(end);
      cell(failures[job.failureType] || '—');
      const action = cell('');
      if (['FAILED','INTERRUPTED'].includes(job.status) && job.attempt === latest.get(job.regionCode + ':' + job.month)) {
        const button = document.createElement('button'); button.textContent = '재시도'; button.className = 'secondary';
        button.addEventListener('click', () => mutate('/' + encodeURIComponent(job.id) + '/retry', {}, '이 작업을 다시 수집할까요?')); action.append(button);
      }
      $('jobs').append(row);
    }
  }
  async function refresh() {
    const current = generation;
    const result = await request('?limit=100');
    if (current !== generation) return;
    jobs = result; render(); $('workspace').hidden = false; $('login-panel').hidden = true;
  }
  async function run(action) {
    if (busy) return; busy = true;
    document.querySelectorAll('button').forEach(b => b.disabled = true);
    try { await action(); } catch (e) { message(e instanceof TypeError ? '서버에 연결할 수 없습니다. 네트워크를 확인하세요.' : e.message, true); }
    finally { busy = false; document.querySelectorAll('button').forEach(b => b.disabled = b.dataset.running === 'true'); }
  }
  function mutate(path, body, question) {
    if (busy || !window.confirm(question)) return;
    run(async () => {
      await request(path, body); message('수집 요청을 접수했습니다. 새로고침으로 진행 상태를 확인하세요.');
      try { await refresh(); } catch (e) { message('요청은 접수됐지만 목록 갱신에 실패했습니다. 재요청하지 말고 새로고침하세요.', true); }
    });
  }
  $('coverage-form').addEventListener('submit', event => {
    event.preventDefault();
    const params = Object.fromEntries(new FormData(event.target));
    params.from = params.from.replace('-',''); params.to = params.to.replace('-','');
    run(async () => {
      $('coverage-rows').replaceChildren(); $('coverage-message').textContent = '현황을 조회하고 있습니다.';
      try {
        const result = await request('/coverage?' + new URLSearchParams(params));
        let sum = 0;
        for (const item of result) {
          sum += item.tradeCount;
          const row = document.createElement('tr');
          const label = item.status === 'SUCCEEDED' && item.tradeCount === 0 ? '수집 성공 · 0건' :
            ({NOT_COLLECTED:'미수집', DATA_WITHOUT_HISTORY:'데이터 있음 · 이력 없음'})[item.status] || statuses[item.status] || item.status;
          for (const value of [item.regionCode,item.month.slice(0,4)+'-'+item.month.slice(4),item.tradeCount.toLocaleString(),label,item.lastSucceededAt ? item.lastSucceededAt.replace('T',' ') : '확인 불가']) {
            const cell = document.createElement('td'); cell.textContent = value; row.append(cell);
          }
          const cell = document.createElement('td'); const button = document.createElement('button');
          button.className = 'secondary'; button.textContent = '수집 준비';
          button.disabled = item.status === 'RUNNING'; button.dataset.running = String(item.status === 'RUNNING');
          button.addEventListener('click', () => {
            const form = $('collect-form');
            if (form.elements.regionCode.value !== item.regionCode) { form.elements.province.value = ''; form.elements.district.value = ''; }
            form.elements.regionCode.value = item.regionCode; form.elements.month.value = item.month.slice(0,4)+'-'+item.month.slice(4);
            form.scrollIntoView({behavior:'smooth',block:'center'}); form.elements.province.focus();
            message('공식 목록 기준 시도·시군구를 확인한 뒤 수집 요청을 눌러주세요.');
          });
          cell.append(button); row.append(cell); $('coverage-rows').append(row);
        }
        $('coverage-message').textContent = params.regionCode + ' · ' + result.length + '개월 · 저장 ' + sum.toLocaleString() + '건 (조회 시점 기준)';
        message('데이터 현황을 조회했습니다. 수집 후 다시 조회하면 갱신됩니다.');
      } catch(e) { $('coverage-message').textContent = '조회에 실패했습니다. 입력값과 연결 상태를 확인하세요.'; throw e; }
    });
  });
  $('login-form').addEventListener('submit', event => { event.preventDefault(); run(async () => { token = $('token').value; $('token').value = ''; await refresh(); message('연결됐습니다.'); }); });
  $('logout').addEventListener('click', () => { disconnect(); message('연결을 해제했습니다.'); });
  $('refresh').addEventListener('click', () => run(async () => { await refresh(); message('최근 이력을 갱신했습니다.'); }));
  $('status').addEventListener('change', render);
  $('collect-form').addEventListener('submit', event => { event.preventDefault(); const data = Object.fromEntries(new FormData(event.target)); data.month = data.month.replace('-',''); for (const key of Object.keys(data)) data[key] = data[key].trim(); mutate('', data, data.province + ' ' + data.district + ' ' + data.month + ' 거래를 수집할까요?'); });
  const now = new Date(); document.querySelectorAll('input[type="month"]').forEach(input => input.max = now.getFullYear() + '-' + String(now.getMonth()+1).padStart(2,'0'));
  window.addEventListener('pagehide', disconnect);
})();
