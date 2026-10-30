#!/usr/bin/env python3
"""Sequential local Compose collection. No API credentials are read by this script."""
import argparse
import csv
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]

def months(start, end):
    def parse(s):
        if not re.fullmatch(r'\d{6}', s):
            raise ValueError('YYYYMM 형식 필요')
        return dt.date(int(s[:4]), int(s[4:]), 1)
    first, last = parse(start), parse(end)
    if first < dt.date(2006, 1, 1) or last > dt.date.today().replace(day=1):
        raise ValueError('지원하지 않는 기간')
    count = (last.year-first.year)*12+last.month-first.month+1
    if not 1 <= count <= 12:
        raise ValueError('기간은 1~12개월')
    return [f'{(first.year*12+first.month-1+i)//12:04d}{(first.month-1+i)%12+1:02d}' for i in range(count)]

def catalog(path):
    with path.open() as source:
        rows = list(csv.DictReader(source, delimiter='\t'))
    codes = set()
    for r in rows:
        if not re.fullmatch(r'\d{5}', r['code']) or r['code'] in codes:
            raise ValueError('지역 코드 오류/중복')
        if r['status'] not in ('ready', 'review'):
            raise ValueError('잘못된 상태')
        if any(not re.fullmatch(r'[가-힣A-Za-z ]{1,50}', r[k]) for k in ('province','district')):
            raise ValueError('지역 명칭 오류')
        codes.add(r['code'])
    if not rows:
        raise ValueError('빈 목록')
    return rows

def pending(rows, periods, state):
    return [(r,m) for r in rows if r['status']=='ready' for m in periods
            if state.get(r['code']+'/'+m,{}).get('status') != 'success']

def save(path, state):
    tmp = path.with_suffix('.tmp')
    with tmp.open('w') as out:
        os.chmod(tmp, 0o600)
        json.dump(state,out,ensure_ascii=False,indent=2)
        out.flush(); os.fsync(out.fileno())
    tmp.replace(path)

def outcome(code, month, returncode, output):
    pattern = rf'MOLIT 성공: 지역 {code}, 계약월 {month[:4]}-{month[4:]}, 저장 (\d+)건, 해제 제외 (\d+)건'
    match = re.search(pattern, output)
    if returncode != 0 or not match:
        return {'status':'failed'}
    return {'status':'success','saved':int(match[1]),'cancelled':int(match[2])}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--from',dest='start',default='202607');p.add_argument('--to',default='202609')
    p.add_argument('--catalog',type=Path,default=ROOT/'config/molit-regions.tsv')
    p.add_argument('--env-file',type=Path,default=ROOT/'.env.docker')
    p.add_argument('--state-dir',type=Path,default=ROOT/'data/collection-2026q3')
    p.add_argument('--limit',type=int,default=10,help='최대 지역·월 구간 수 (API 페이지 수가 아님)')
    p.add_argument('--delay',type=float,default=2)
    p.add_argument('--execute',action='store_true')
    a=p.parse_args()
    if a.limit < 1 or a.delay < 1: p.error('limit>=1, delay>=1 필요')
    rows=catalog(a.catalog);periods=months(a.start,a.to)
    a.state_dir.mkdir(parents=True,exist_ok=True)
    with (a.state_dir/'lock').open('w') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        path=a.state_dir/'progress.json'
        fingerprint=hashlib.sha256(a.catalog.read_bytes()+str(a.env_file.resolve()).encode()).hexdigest()
        state=json.loads(path.read_text()) if path.exists() else {'catalog_sha256':fingerprint,'segments':{}}
        if state['catalog_sha256']!=fingerprint:raise ValueError('목록이 변경됨: 별도 state-dir 사용 필요')
        todo=pending(rows,periods,state['segments'])
        blocked=sum(r['status']=='review' for r in rows)
        print(f'전체 지역 {len(rows)}, 검토 보류 {blocked}, 미완료 {len(todo)}구간, 이번 실행 최대 {a.limit}구간',flush=True)
        if not a.execute:return
        if not a.env_file.is_file():raise ValueError('환경 파일 없음')
        env=dict(os.environ,PATH='/Applications/Docker.app/Contents/Resources/bin:'+os.environ.get('PATH',''))
        base=['docker','compose','--env-file',str(a.env_file.resolve()),'-f',str(ROOT/'compose.yaml')]
        for r,m in todo[:a.limit]:
            key=r['code']+'/'+m
            state['segments'][key]={'status':'running'};save(path,state)
            cmd=base+['run','--rm','importer','--molit.import-on-start=true',f"--molit.regions={r['code']}:{r['province']}:{r['district']}",f'--molit.month={m}','--molit.from=','--molit.to=']
            try:
                proc=subprocess.run(cmd,env=env,cwd=ROOT,capture_output=True,text=True,timeout=900)
                result=outcome(r['code'],m,proc.returncode,proc.stdout+proc.stderr)
            except subprocess.TimeoutExpired:
                # Compose child may survive: stop and inspect before retrying.
                result={'status':'failed','reason':'timeout: 실행 중 importer 확인 필요'}
            result['at']=dt.datetime.now(dt.timezone.utc).isoformat()
            state['segments'][key]=result;save(path,state)
            print(key,result,flush=True)
            if result['status']!='success':raise SystemExit('실패 시 중단. 인증·한도·네트워크 확인 후 같은 명령으로 재개하세요.')
            time.sleep(a.delay)
        print('이번 실행 완료. 동일 명령은 성공 구간을 건너뜁니다.',flush=True)

if __name__=='__main__':main()
