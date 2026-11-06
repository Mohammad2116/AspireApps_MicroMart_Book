#!/usr/bin/env python3
"""Fail on unreviewed HIGH/CRITICAL findings; never remove raw scanner results."""
import json,sys,datetime
from pathlib import Path
root=Path(__file__).resolve().parents[1]
policy=json.loads((root/'security/advisory-review.json').read_text())
exceptions={(x['id'],x['package']) for x in policy['exceptions']}
if datetime.date.today()>datetime.date.fromisoformat(policy['expires_on']):raise SystemExit('Security applicability review expired; re-review required')
failed=[];accepted=[]
if len(sys.argv)<2:raise SystemExit('At least one scanner JSON file is required')
for name in sys.argv[1:]:
 data=json.loads(Path(name).read_text())
 if 'SchemaVersion' not in data:raise SystemExit('Missing scanner results: '+name)
 for result in data.get('Results') or []:
  if result.get('Secrets'):failed += [('secret',x.get('RuleID')) for x in result['Secrets']]
  for v in result.get('Vulnerabilities',[]):
   if v['Severity'] not in ['HIGH','CRITICAL']:continue
   key=(v['VulnerabilityID'],v['PkgName'])
   (accepted if key in exceptions else failed).append(key)
print(json.dumps({'reviewed_applicability_exceptions':sorted(set(accepted)),'blocking_findings':sorted(set(failed))},indent=2))
if failed:raise SystemExit(1)
