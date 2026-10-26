#!/usr/bin/env python3
"""Queries OSV using public Maven coordinates only; does not upload project source."""
import json, pathlib, zipfile, urllib.request
root=pathlib.Path(__file__).resolve().parents[1]
repo=root/'.m2/repository'
with zipfile.ZipFile(root/'target/apartment-search-0.1.0.jar') as jar:
 names=[pathlib.Path(n).name for n in jar.namelist() if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')]
queries=[]; packages=[]; missing=[]
for name in names:
 matches=list(repo.rglob(name))
 if name.startswith('spring-boot-jarmode-tools-'):
  package='org.springframework.boot:spring-boot-jarmode-tools'
  version=name.removeprefix('spring-boot-jarmode-tools-').removesuffix('.jar')
  packages.append({'name':package,'version':version})
  queries.append({'package':{'name':package,'ecosystem':'Maven'},'version':version})
  continue
 if len(matches)!=1: missing.append(name);continue
 parts=matches[0].relative_to(repo).parts
 package='.'.join(parts[:-3])+':'+parts[-3]
 packages.append({'name':package,'version':parts[-2]})
 queries.append({'package':{'name':package,'ecosystem':'Maven'},'version':parts[-2]})
req=urllib.request.Request('https://api.osv.dev/v1/querybatch',data=json.dumps({'queries':queries}).encode(),headers={'Content-Type':'application/json'})
with urllib.request.urlopen(req,timeout=60) as r: results=json.load(r)['results']
report={'packagesChecked':len(packages),'unmapped':missing,'findings':[dict(p,vulnerabilities=r.get('vulns',[])) for p,r in zip(packages,results) if r.get('vulns')]}
(root/'docs/dependency-scan-2026-10-26.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))

if missing:raise SystemExit(2)
if report["findings"]:raise SystemExit(1)
