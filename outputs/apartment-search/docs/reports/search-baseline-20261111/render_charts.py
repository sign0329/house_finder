"""Generate report charts from plans.json. Requires matplotlib; no DB access."""
import json
import statistics
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib import font_manager

ROOT = Path(__file__).resolve().parent
DATA = json.loads((ROOT / 'plans.json').read_text())
CASES = ['all_recent', 'apartment_keyword', 'deep_page']
LABELS = ['전국 최신순 첫 페이지', '아파트 이름 검색', '깊은 페이지 · 12만 건 건너뜀']
COLORS = ['#2563eb', '#059669', '#d97706']
font = next((name for name in ['Apple SD Gothic Neo', 'Malgun Gothic', 'Noto Sans CJK KR', 'NanumGothic']
             if any(f.name == name for f in font_manager.fontManager.ttflist)), None)
if font is None:
    raise RuntimeError('한글 글꼴을 설치하세요: Noto Sans CJK KR 또는 NanumGothic')
plt.rcParams.update({'font.family': font, 'font.size': 11, 'svg.fonttype': 'path'})
fig, axes = plt.subplots(1, 2, figsize=(14, 5.8), gridspec_kw={'width_ratios': [1.1, 1]})
fig.patch.set_facecolor('#ffffff')
fig.suptitle('거래 검색 성능 기준 측정 | 695,777건', fontsize=20, fontweight='bold', x=.06, ha='left')
fig.text(.06, .88, '로컬 PostgreSQL 17.11 · 검색 유형별 3회 측정 · 2026-11-11', color='#475569')
ax = axes[0]
for i, case in enumerate(CASES):
    rows = sorted([r for r in DATA if r['case'] == case], key=lambda r: r['run'])
    assert len(rows) == 3
    for part, delta, color in [('count', -.18, '#94a3b8'), ('list', .18, '#2563eb')]:
        value = statistics.median(r[part]['Execution Time'] for r in rows)
        ax.barh(i + delta, value, height=.30, color=color, label={'count': '전체 건수 조회', 'list': '목록 조회'}[part] if i == 0 else None)
        ax.text(value + 5, i + delta, f'{value:.1f}', va='center', fontsize=10)
    axes[1].plot([r['run'] for r in rows], [r['list']['Execution Time'] for r in rows], marker='o', linewidth=2, color=COLORS[i], label=LABELS[i])
ax.set_yticks(range(3), LABELS)
ax.invert_yaxis()
ax.set_xlim(0, 320)
ax.set_xlabel('실행 시간(밀리초)')
ax.set_title('쿼리별 실행 시간 중앙값', loc='left', pad=14, fontweight='bold')
ax.legend(loc='upper right', frameon=False)
ax = axes[1]
ax.set_title('목록 조회의 회차별 실행 시간', loc='left', pad=14, fontweight='bold')
ax.set_xticks([1, 2, 3], ['1회', '2회', '3회'])
ax.set_ylim(0, 550)
ax.set_ylabel('실행 시간(밀리초)')
ax.legend(loc='upper right', frameon=False, fontsize=9)
for ax in axes:
    ax.spines[['top', 'right']].set_visible(False)
    ax.grid(axis='x' if ax is axes[0] else 'y', alpha=.18)
    ax.set_axisbelow(True)
fig.text(.06, .05, 'SQL 실행 시간만 측정 · 웹 응답 시간 및 AWS 성능과 다름 · 캐시 초기화 없음 · 백분위수 및 처리량 미측정', fontsize=10, color='#475569')
fig.subplots_adjust(left=.22, right=.97, top=.75, bottom=.20, wspace=.35)
fig.savefig(ROOT / 'search-baseline.png', dpi=180, facecolor='white')
fig.savefig(ROOT / 'search-baseline.svg', facecolor='white')
svg = ROOT / 'search-baseline.svg'
svg.write_text('\n'.join(line.rstrip() for line in svg.read_text().splitlines()) + '\n')
plt.close(fig)
