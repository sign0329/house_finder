"""Generate report charts from plans.json. Requires matplotlib; no DB access."""
import json
import statistics
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent
DATA = json.loads((ROOT / 'plans.json').read_text())
CASES = ['all_recent', 'apartment_keyword', 'deep_page']
LABELS = ['Nationwide / first page', 'Apartment keyword', 'Deep page / offset 120,000']
COLORS = ['#2563eb', '#059669', '#d97706']
plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 11, 'svg.fonttype': 'path'})
fig, axes = plt.subplots(1, 2, figsize=(14, 5.8), gridspec_kw={'width_ratios': [1.1, 1]})
fig.patch.set_facecolor('#ffffff')
fig.suptitle('Search SQL baseline | 695,777 trades', fontsize=20, fontweight='bold', x=.06, ha='left')
fig.text(.06, .88, 'Local PostgreSQL 17.11 · 3 observations per scenario · 2026-11-11', color='#475569')
ax = axes[0]
for i, case in enumerate(CASES):
    rows = sorted([r for r in DATA if r['case'] == case], key=lambda r: r['run'])
    assert len(rows) == 3
    for part, delta, color in [('count', -.18, '#94a3b8'), ('list', .18, '#2563eb')]:
        value = statistics.median(r[part]['Execution Time'] for r in rows)
        ax.barh(i + delta, value, height=.30, color=color, label=part.upper() if i == 0 else None)
        ax.text(value + 5, i + delta, f'{value:.1f}', va='center', fontsize=10)
    axes[1].plot([r['run'] for r in rows], [r['list']['Execution Time'] for r in rows], marker='o', linewidth=2, color=COLORS[i], label=LABELS[i])
ax.set_yticks(range(3), LABELS)
ax.invert_yaxis()
ax.set_xlim(0, 320)
ax.set_xlabel('Execution time (ms)')
ax.set_title('Median by SQL operation', loc='left', pad=14, fontweight='bold')
ax.legend(loc='upper right', frameon=False)
ax = axes[1]
ax.set_title('List query: all three observations', loc='left', pad=14, fontweight='bold')
ax.set_xticks([1, 2, 3], ['Run 1', 'Run 2', 'Run 3'])
ax.set_ylim(0, 550)
ax.set_ylabel('Execution time (ms)')
ax.legend(loc='upper right', frameon=False, fontsize=9)
for ax in axes:
    ax.spines[['top', 'right']].set_visible(False)
    ax.grid(axis='x' if ax is axes[0] else 'y', alpha=.18)
    ax.set_axisbelow(True)
fig.text(.06, .05, 'SQL execution only — not HTTP latency or AWS performance. Cache was not reset; no p95 or throughput claim.', fontsize=10, color='#475569')
fig.subplots_adjust(left=.22, right=.97, top=.75, bottom=.20, wspace=.35)
fig.savefig(ROOT / 'search-baseline.png', dpi=180, facecolor='white')
fig.savefig(ROOT / 'search-baseline.svg', facecolor='white')
plt.close(fig)
