(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const endpoint = '/api/admin/collection-jobs';
  const statuses = {RUNNING:'실행 중', SUCCEEDED:'성공', FAILED:'실패', INTERRUPTED:'중단'};
  const failures = {FETCH_FAILED:'공공 API 호출 실패', STORE_FAILED:'저장 실패', EXECUTOR_BUSY:'다른 수집 실행 중', LEASE_EXPIRED:'작업 유효 시간 만료'};
  let token = '', jobs = [], busy = false, generation = 0;
  function message(text, error = false) { $('message').textContent = text; $('message').className = error ? 'error' : ''; }
  function disconnect() {
    generation++; token = ''; jobs = []; $('token').value = ''; $('jobs').replaceChildren();
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
    finally { busy = false; document.querySelectorAll('button').forEach(b => b.disabled = false); }
  }
  function mutate(path, body, question) {
    if (busy || !window.confirm(question)) return;
    run(async () => {
      await request(path, body); message('수집 요청을 접수했습니다. 새로고침으로 진행 상태를 확인하세요.');
      try { await refresh(); } catch (e) { message('요청은 접수됐지만 목록 갱신에 실패했습니다. 재요청하지 말고 새로고침하세요.', true); }
    });
  }
  $('login-form').addEventListener('submit', event => { event.preventDefault(); run(async () => { token = $('token').value; $('token').value = ''; await refresh(); message('연결됐습니다.'); }); });
  $('logout').addEventListener('click', () => { disconnect(); message('연결을 해제했습니다.'); });
  $('refresh').addEventListener('click', () => run(async () => { await refresh(); message('최근 이력을 갱신했습니다.'); }));
  $('status').addEventListener('change', render);
  $('collect-form').addEventListener('submit', event => { event.preventDefault(); const data = Object.fromEntries(new FormData(event.target)); data.month = data.month.replace('-',''); for (const key of Object.keys(data)) data[key] = data[key].trim(); mutate('', data, data.province + ' ' + data.district + ' ' + data.month + ' 거래를 수집할까요?'); });
  const now = new Date(); document.querySelector('[name="month"]').max = now.getFullYear() + '-' + String(now.getMonth()+1).padStart(2,'0');
  window.addEventListener('pagehide', disconnect);
})();
