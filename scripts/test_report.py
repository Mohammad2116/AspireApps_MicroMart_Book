#!/usr/bin/env python3
"""Collect test case outcomes only; intentionally omit environment properties and log payloads."""
from pathlib import Path
import xml.etree.ElementTree as E
import json,argparse,datetime
parser=argparse.ArgumentParser(); parser.add_argument('--output',default='reports/test-results.json'); args=parser.parse_args()
root=Path(__file__).resolve().parents[1]; suites=[]
for module in ['domain','application']:
 for kind in ['surefire','failsafe']:
  for path in sorted((root/module/'target'/f'{kind}-reports').glob('TEST-*.xml')):
   data=E.parse(path).getroot(); cases=[]
   for case in data.findall('testcase'):
    status=next((t for t in ['failure','error','skipped'] if case.find(t) is not None),'passed')
    cases.append({'name':case.get('name'),'status':status,'seconds':float(case.get('time','0'))})
   suites.append({'name':data.get('name'),'module':module,'kind':kind, 'tests':int(data.get('tests','0')),'failures':int(data.get('failures','0')),'errors':int(data.get('errors','0')),'skipped':int(data.get('skipped','0')),'seconds':float(data.get('time','0')),'cases':cases})
report={'generated_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'note':'Outcomes from latest XML files; run clean verify to avoid stale results. Does not certify CI or deployment.','totals':{k:sum(s[k] for s in suites) for k in ['tests','failures','errors','skipped']},'suites':suites}
p=root/args.output;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report['totals']))
if not suites: raise SystemExit('No test evidence found')
