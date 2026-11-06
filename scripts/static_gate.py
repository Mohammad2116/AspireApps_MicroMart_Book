#!/usr/bin/env python3
import xml.etree.ElementTree as E
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
# Exact contextual reviews, not global category suppression. Raw XML remains deliverable.
reviewed={('SPRING_CSRF_PROTECTION_DISABLED','book.companion.security.SecurityConfig'):'Bearer-only API; no cookie authentication; filter tests enforce access.',('HRS_REQUEST_PARAMETER_TO_HTTP_HEADER','book.companion.web.RequestCorrelation'):'Full-match ASCII [A-Za-z0-9_-]{1,64}, otherwise generated UUID; no control characters accepted.'}
rows=[];blocked=[]
for module in ['domain','application']:
 p=root/module/'target/spotbugsXml.xml'
 if not p.exists():raise SystemExit('Missing static report: '+str(p))
 for b in E.parse(p).getroot().findall('BugInstance'):
  cls=b.find('Class').get('classname');row={'type':b.get('type'),'class':cls,'category':b.get('category'),'priority':int(b.get('priority')),'review':reviewed.get((b.get('type'),cls))};rows.append(row)
  if row['category']=='SECURITY' and row['priority']<=2 and not row['review']:blocked.append(row)
print(json.dumps({'findings':rows,'blocking':blocked},indent=2))
if blocked:raise SystemExit(1)
