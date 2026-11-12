"""로컬 전후 측정 결과를 그린다. DB 접속 없음; matplotlib 필요."""
import json
import statistics
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib import font_manager
ROOT=Path(__file__).resolve().parent
DATA=json.loads((ROOT/'plans.json').read_text())['measurements']
font = next((name for name in ['Apple SD Gothic Neo', 'Malgun Gothic', 'Noto Sans CJK KR', 'NanumGothic']
             if any(f.name == name for f in font_manager.fontManager.ttflist)), None)
if font is None:
    raise RuntimeError('한글 글꼴을 설치하세요: Noto Sans CJK KR 또는 NanumGothic')
plt.rcParams.update({'font.family': font, 'font.size': 11, 'svg.fonttype': 'path'})

fig,axes=plt.subplots(1,2,figsize=(14,6))
fig.suptitle('최신순 인덱스 추가 전후 | 거래 695,777건',fontsize=20,fontweight='bold',x=.05,ha='left')
fig.text(.05,.88,'로컬 PostgreSQL 17.11 · 조건별 전후 각각 3회 · 중앙값 · 2026-11-12',color='#475569')
labels=['전국 최신순 첫 페이지','아파트 이름 검색','깊은 페이지 · 12만 건 건너뜀']
cases=['all_recent','apartment_keyword','deep_page']
for ax,part,title in zip(axes,['list','count'],['목록 조회 실행 시간','전체 건수 조회 실행 시간']):
 for i,case in enumerate(cases):
  for phase,delta,color,label in [('before',-.18,'#94a3b8','추가 전'),('after',.18,'#2563eb','추가 후')]:
   value=statistics.median(r[part]['Execution Time'] for r in DATA if r['phase']==phase and r['case']==case)
   ax.barh(i+delta,value,height=.30,color=color,label=label if i==0 else None)
   ax.text(value+(3 if part=='list' else .7),i+delta,f'{value:.3f}',va='center',fontsize=10)
 ax.set_yticks(range(3),labels if part=='list' else [])
 ax.invert_yaxis();ax.set_xlim(0,330 if part=='list' else 75)
 ax.set_title(title,loc='left',fontweight='bold',pad=12)
 ax.set_xlabel('실행 시간(밀리초)');ax.spines[['top','right']].set_visible(False)
 ax.grid(axis='x',alpha=.18);ax.set_axisbelow(True)
 ax.legend(loc='lower right',frameon=False)
fig.text(.05,.05,'웹 응답 시간·AWS 성능 아님 · 캐시 초기화 없음 · 건수 조회는 개선되지 않음 · 인덱스 약 21MiB 추가',fontsize=10,color='#475569')
fig.subplots_adjust(left=.21,right=.97,top=.76,bottom=.20,wspace=.16)
fig.savefig(ROOT/'comparison.png',dpi=180,facecolor='white')
fig.savefig(ROOT/'comparison.svg',facecolor='white')
f=ROOT/'comparison.svg';f.write_text('\n'.join(l.rstrip() for l in f.read_text().splitlines())+'\n')
plt.close(fig)
