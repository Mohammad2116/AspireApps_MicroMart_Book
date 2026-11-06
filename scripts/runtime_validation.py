#!/usr/bin/env python3
"""Linux local-only runtime, outage, monitoring and repeated k6 validation.
Requires Docker, Python cryptography and the tested image. Never uses external accounts.
"""
import argparse,base64,hashlib,http.server,json,os,pathlib,secrets,socket,subprocess,tempfile,threading,time,urllib.request,urllib.error,platform,shutil
from cryptography.hazmat.primitives.asymmetric import rsa,padding
from cryptography.hazmat.primitives import hashes
R=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--image',default='book-companion:excellence');p.add_argument('--k6',required=True);p.add_argument('--output',default='reports/runtime');args=p.parse_args()
OUT=(R/args.output).resolve();OUT.mkdir(parents=True,exist_ok=True)
report={'started_at':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'platform':platform.platform(),'cpu_count':os.cpu_count(),'application_limits':{'cpus':2,'memory':'768m','pids':256},'checks':[],'performance':[]}
names=[];secret_values=[];prefix='book-qa-'+secrets.token_hex(4);server=None
PG='postgres@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777'
REDIS='redis@sha256:6ba19c50b56777f5c11c58eff8ddb69d0d5f27ba131cffb63f180ccfd43c66af'
KAFKA='apache/kafka@sha256:4ceccc577f03f51f6af8dbfda55194d0d892f4fa7913ffbded567ce3895622ed'
def run(cmd,check=True,timeout=120):
 r=subprocess.run(cmd,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
 if check and r.returncode:raise RuntimeError('Command failed: '+cmd[0]+' '+cmd[1]+'; '+r.stderr[-400:])
 return r

def docker(*cmd,**kw):return run(['docker',*cmd],**kw)
def port():
 with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def wait(fn,seconds=90):
 until=time.monotonic()+seconds;last='not ready'
 while time.monotonic()<until:
  try:
   value=fn()
   if value:return value
  except Exception as e:last=type(e).__name__
  time.sleep(.25)
 raise AssertionError('Condition timed out: '+last)
def check(name,condition,detail=None):
 assert condition,name
 report['checks'].append({'name':name,'passed':True,'detail':detail})
 print(name,flush=True)
def start(name,image,env=None,options=(),command=()):
 full=prefix+'-'+name;names.append(full)
 cmd=['run','-d','--name',full,'--label','book.qa='+prefix,'--network','host',*options]
 for k,v in (env or {}).items():cmd+=['-e',k+'='+str(v)]
 docker(*cmd,image,*command);return full

def request(url,token=None,data=None,method=None,headers=None):
 h=dict(headers or {})
 if token:h['Authorization']='Bearer '+token
 if data is not None:h['Content-Type']='application/json'
 req=urllib.request.Request(url,data=None if data is None else json.dumps(data).encode(),headers=h,method=method)
 try:
  with urllib.request.urlopen(req,timeout=12) as r:return r.status,r.read().decode(),dict(r.headers)
 except urllib.error.HTTPError as e:return e.code,e.read().decode(),dict(e.headers)
def b64(b):return base64.urlsafe_b64encode(b).rstrip(b'=').decode()
keys={k:rsa.generate_private_key(public_exponent=65537,key_size=2048) for k in ['old','new','unknown']};visible=['old']
def jwk(k):
 pub=keys[k].public_key().public_numbers();return {'kty':'RSA','kid':k,'alg':'RS256','use':'sig','n':b64(pub.n.to_bytes((pub.n.bit_length()+7)//8,'big')),'e':b64(pub.e.to_bytes((pub.e.bit_length()+7)//8,'big'))}
class Issuer(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  value={'keys':[jwk(k) for k in visible]} if self.path=='/jwks' else {'issuer':ISSUER,'jwks_uri':ISSUER+'/jwks','id_token_signing_alg_values_supported':['RS256']}
  body=json.dumps(value).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(body)
 def log_message(self,*a):pass

def token(k='old',tenant='qa',scope='products:read products:write',audience='orders-api'):
 claims={'iss':ISSUER,'aud':audience,'sub':'runtime-user','tenant':tenant,'scope':scope,'iat':int(time.time()),'exp':int(time.time())+3600}
 if audience is None:claims.pop('aud',None)
 body=b64(json.dumps({'alg':'RS256','kid':k}).encode())+'.'+b64(json.dumps(claims).encode());result=body+'.'+b64(keys[k].sign(body.encode(),padding.PKCS1v15(),hashes.SHA256()));secret_values.append(result);return result

def sql(query):return docker('exec',pg,'psql','-U','book','-p',str(dbport),'-d','book','-Atc',query).stdout.strip()
try:
 report['image_id']=docker('image','inspect','--format','{{.Id}}',args.image).stdout.strip()
 report['docker_version']=docker('version','--format','{{.Server.Version}}').stdout.strip()
 report['host_memory_kib']=int(next(x.split()[1] for x in pathlib.Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemTotal:')))
 report['cpu_model']=next(x.split(':',1)[1].strip() for x in pathlib.Path('/proc/cpuinfo').read_text().splitlines() if x.startswith('model name'))
 dbport,rport,kport,cport,aport,pp,gp=[port() for _ in range(7)]
 server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Issuer);ISSUER='http://127.0.0.1:'+str(server.server_address[1]);threading.Thread(target=server.serve_forever,daemon=True).start()
 password=secrets.token_urlsafe(24);secret_values.append(password)
 pg=start('pg',PG,{'POSTGRES_DB':'book','POSTGRES_USER':'book','POSTGRES_PASSWORD':password},['--memory','512m','--cpus','2'],['postgres','-p',str(dbport),'-c','listen_addresses=127.0.0.1'])
 redis=start('redis',REDIS,options=['--memory','128m','--cpus','1'],command=['redis-server','--bind','127.0.0.1','--port',str(rport)])
 kafka=start('kafka',KAFKA,{'KAFKA_NODE_ID':1,'KAFKA_PROCESS_ROLES':'broker,controller','KAFKA_LISTENERS':f'PLAINTEXT://127.0.0.1:{kport},CONTROLLER://127.0.0.1:{cport}','KAFKA_ADVERTISED_LISTENERS':f'PLAINTEXT://127.0.0.1:{kport}','KAFKA_CONTROLLER_QUORUM_VOTERS':f'1@127.0.0.1:{cport}','KAFKA_CONTROLLER_LISTENER_NAMES':'CONTROLLER','KAFKA_LISTENER_SECURITY_PROTOCOL_MAP':'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT','KAFKA_INTER_BROKER_LISTENER_NAME':'PLAINTEXT','KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR':1,'KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS':0},['--memory','1g','--cpus','2'])
 wait(lambda:docker('exec',pg,'pg_isready','-U','book','-p',str(dbport),check=False).returncode==0)
 wait(lambda:docker('exec',kafka,'/opt/kafka/bin/kafka-topics.sh','--bootstrap-server',f'127.0.0.1:{kport}','--list',check=False,timeout=15).returncode==0)
 env={'DB_URL':f'jdbc:postgresql://127.0.0.1:{dbport}/book','DB_USER':'book','DB_PASSWORD':password,'REDIS_HOST':'127.0.0.1','REDIS_PORT':rport,'KAFKA_BOOTSTRAP_SERVERS':f'127.0.0.1:{kport}','JWT_ISSUER':ISSUER,'JWT_AUDIENCE':'orders-api','RELAY_ENABLED':'true','CONSUMER_ENABLED':'true','KAFKA_AUTO_CREATE':'true','SERVER_ADDRESS':'127.0.0.1','SERVER_PORT':aport,'JAVA_TOOL_OPTIONS':'-XX:MaxRAMPercentage=65'}
 app=start('app',args.image,env,['--read-only','--tmpfs','/tmp:rw,noexec,nosuid,size=128m','--cap-drop','ALL','--security-opt','no-new-privileges','--pids-limit','256','--memory','768m','--cpus','2'])
 base=f'http://127.0.0.1:{aport}';auth=token();ops=token(scope='ops:read')
 wait(lambda:request(base+'/actuator/health/readiness')[0]==200)
 wait(lambda:docker('inspect','--format','{{.State.Health.Status}}',app).stdout.strip()=='healthy')
 check('container_ready_and_docker_health',True)
 check('non_root_read_only',docker('inspect','--format','{{.Config.User}} {{.HostConfig.ReadonlyRootfs}}',app).stdout.strip()=='10001:10001 true')
 check('unauthenticated_denied',request(base+'/api/products')[0]==401)
 check('missing_audience_denied',request(base+'/api/products',token(audience=None))[0]==401)
 check('wrong_audience_denied',request(base+'/api/products',token(audience='wrong'))[0]==401)
 check('insufficient_scope_denied',request(base+'/api/products',ops)[0]==403)
 visible[:]=['old','new'];newauth=token('new')
 check('new_jwk_rotation',request(base+'/api/products',newauth)[0]==200)
 check('old_jwk_overlap',request(base+'/api/products',auth)[0]==200)
 check('unknown_key_denied',request(base+'/api/products',token('unknown'))[0]==401)
 status,body,_=request(base+'/api/v1/products',auth,{'name':'runtime inventory','price':2.50,'stock':100000});check('postgres_product_create',status==201);product=json.loads(body)['id']
 check('cross_tenant_denied',request(base+'/api/v1/products/'+product,token(tenant='other'))[0]==404)
 def place(key):return request(base+'/api/v1/orders',auth,{'productId':product,'quantity':1},headers={'Idempotency-Key':key})
 status,body,_=place('first');check('order_created',status==201);oid=json.loads(body)['id'];wait(lambda:sql(f"select count(*) from analytics where order_id='{oid}'")=='1')
 check('real_kafka_outbox_effect',True)
 status,body,_=place('first');check('idempotent_replay',status==201 and json.loads(body)['id']==oid)
 check('redis_rate_endpoint',request(base+'/api/v1/limits/check',auth,method='POST')[0]==200)
 docker('pause',redis)
 try:check('redis_outage_fails_closed',request(base+'/api/v1/limits/check',auth,method='POST')[0]==503)
 finally:docker('unpause',redis)
 wait(lambda:request(base+'/api/v1/limits/check',auth,method='POST')[0]==200);check('redis_reconnect',True)
 docker('stop','-t','10',kafka)
 status,body,_=place('broker-down');check('order_durable_during_kafka_outage',status==201);pending=json.loads(body)['id']
 wait(lambda:int(sql("select coalesce(max(attempts),0) from outbox where published_at is null"))>=1)
 check('outbox_retained_during_outage',sql(f"select count(*) from outbox where aggregate_id='{pending}' and published_at is null")=='1')
 docker('start',kafka);wait(lambda:sql(f"select count(*) from analytics where order_id='{pending}'")=='1',120);check('broker_restart_recovers_pending_order',True)
 docker('stop','-t','10',pg)
 check('postgres_outage_readiness_fails',request(base+'/actuator/health/readiness')[0]==503)
 check('postgres_outage_liveness_survives',request(base+'/actuator/health/liveness')[0]==200)
 docker('start',pg);wait(lambda:request(base+'/actuator/health/readiness')[0]==200);check('postgres_restart_recovers',True)
 trace='0123456789abcdef0123456789abcdef'
 check('trace_context_request',request(base+'/api/products',auth,headers={'traceparent':'00-'+trace+'-0123456789abcdef-01','X-Request-ID':'runtime-check'})[0]==200)
 # Prometheus and Grafana are disposable local containers; generated bearer token never enters evidence.
 with tempfile.TemporaryDirectory(prefix='book-ops-') as tmp:
  tmp=pathlib.Path(tmp);tmp.chmod(0o755)
  (tmp/'token').write_text(ops);(tmp/'token').chmod(0o444)
  config=f'global:\n  scrape_interval: 1s\nrule_files: [/etc/book/alerts.yml]\nscrape_configs:\n  - job_name: book\n    metrics_path: /actuator/prometheus\n    authorization:\n      type: Bearer\n      credentials_file: /etc/book/token\n    static_configs:\n      - targets: [127.0.0.1:{aport}]\n'
  (tmp/'prometheus.yml').write_text(config);shutil.copy(R/'ops/alerts.yml',tmp/'alerts.yml')
  for f in tmp.iterdir():f.chmod(0o444)
  prom=start('prometheus','prom/prometheus@sha256:63805ebb8d2b3920190daf1cb14a60871b16fd38bed42b857a3182bc621f4996',options=['--memory','256m','--cpus','1','-v',str(tmp)+':/etc/book:ro'],command=['--config.file=/etc/book/prometheus.yml','--web.listen-address=127.0.0.1:'+str(pp)])
  query=f'http://127.0.0.1:{pp}/api/v1/query?query=up'
  wait(lambda:any(x['value'][1]=='1' for x in json.loads(request(query)[1])['data']['result']))
  check('prometheus_scrape_authenticated',True)
  _,metrics,_=request(base+'/actuator/prometheus',ops);(OUT/'metrics.prom').write_text(metrics)
  check('operational_metrics_present',all(s in metrics for s in ['book_outbox_pending','book_outbox_oldest_seconds','book_redis_errors_total','hikaricp_connections_active','kafka_consumer']))
  rules=docker('run','--rm','-v',str(tmp)+':/etc/book:ro','--entrypoint','promtool','prom/prometheus@sha256:63805ebb8d2b3920190daf1cb14a60871b16fd38bed42b857a3182bc621f4996','check','rules','/etc/book/alerts.yml');(OUT/'alert-validation.txt').write_text(rules.stdout);check('prometheus_rules_valid',True)
  grafpass=secrets.token_urlsafe(24);secret_values.append(grafpass)
  graf=start('grafana','grafana/grafana@sha256:6ac590e7cabc2fbe8d7b8fc1ce9c9f0582177b334e0df9c927ebd9670469440f',{'GF_SERVER_HTTP_ADDR':'127.0.0.1','GF_SERVER_HTTP_PORT':gp,'GF_SECURITY_ADMIN_PASSWORD':grafpass,'GF_ANALYTICS_REPORTING_ENABLED':'false','GF_ANALYTICS_CHECK_FOR_UPDATES':'false'},['--memory','256m','--cpus','1'])
  gurl=f'http://127.0.0.1:{gp}';wait(lambda:request(gurl+'/api/health')[0]==200)
  gh={'Authorization':'Basic '+base64.b64encode(('admin:'+grafpass).encode()).decode()}
  check('grafana_datasource',request(gurl+'/api/datasources',data={'name':'Book Prometheus','uid':'book-prometheus','type':'prometheus','url':f'http://127.0.0.1:{pp}','access':'proxy'},headers=gh)[0]==200)
  check('grafana_dashboard_import',request(gurl+'/api/dashboards/db',data={'dashboard':json.loads((R/'ops/grafana-dashboard.json').read_text()),'overwrite':True},headers=gh)[0]==200)
  check('grafana_dashboard_readback',request(gurl+'/api/dashboards/uid/book-companion',headers=gh)[0]==200)
  # Warm-up and three independently identified runs; raw summaries retain sample sizes and quantiles.
  for iteration in range(4):
   label='warmup' if iteration==0 else 'run-'+str(iteration);duration='5s' if iteration==0 else '15s';dest=OUT/(label+'.json')
   perfenv=os.environ.copy();perfenv.update({'BASE':base,'TOKEN':auth,'PRODUCT':product,'RUN':prefix+'-'+label,'RESULT':str(dest),'DURATION':duration})
   samples=[];stop_sample=threading.Event();started=time.monotonic()
   def sample():
    while not stop_sample.is_set():
     try:
      stats=docker('stats','--no-stream','--format','{{json .}}',app,pg,kafka).stdout
      _,text,_=request(base+'/actuator/prometheus',ops)
      selected=[line for line in text.splitlines() if line.startswith(('hikaricp_connections_active','hikaricp_connections_pending','book_outbox_pending','book_outbox_oldest_seconds'))]
      samples.append({'elapsed_seconds':time.monotonic()-started,'docker':[json.loads(x) for x in stats.splitlines()],'pool_and_backlog':selected})
     except Exception as e:samples.append({'sampling_error':type(e).__name__})
     stop_sample.wait(1)
   sampler=threading.Thread(target=sample,daemon=True);sampler.start()
   proc=subprocess.run([str(pathlib.Path(args.k6).resolve()),'run','--quiet',str(R/'performance/workload.js')],env=perfenv,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120)
   stop_sample.set();sampler.join(20)
   (OUT/(label+'-resources.json')).write_text(json.dumps(samples,indent=2))
   report['performance'].append({'run':label,'exit_code':proc.returncode,'duration':duration,'vus':5,'summary':dest.name})
   (OUT/(label+'-console.txt')).write_text(proc.stdout+proc.stderr)
   if iteration:
    check('performance_'+label,proc.returncode==0)
    ages=[float(line.rsplit(' ',1)[1]) for sample in samples for line in sample.get('pool_and_backlog',[]) if line.startswith('book_outbox_oldest_seconds ')]
    check('bounded_backlog_'+label,bool(ages) and all(0<=age<5 for age in ages),{'maximum_sampled_age_seconds':max(ages) if ages else None,'budget_seconds':5})
  wait(lambda:sql("select count(*) from outbox where status in ('PENDING','CLAIMED')")=='0',120)
  check('workload_outbox_drained',True)
  check('workload_business_effect_count',sql('select count(*) from orders')==sql('select count(*) from analytics'))
  report['orders_after_workload']=int(sql('select count(*) from orders'))
 # A real logical backup is restored to a separate local database; no production data is used.
 dump=subprocess.run(['docker','exec',pg,'pg_dump','-U','book','-p',str(dbport),'-d','book','-Fc'],stdout=subprocess.PIPE,check=True).stdout
 docker('exec',pg,'createdb','-U','book','-p',str(dbport),'restored')
 subprocess.run(['docker','exec','-i',pg,'pg_restore','-U','book','-p',str(dbport),'-d','restored'],input=dump,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
 restored=docker('exec',pg,'psql','-U','book','-p',str(dbport),'-d','restored','-Atc','select count(*) from orders').stdout.strip()
 check('logical_backup_restore_preserves_orders',restored==sql('select count(*) from orders'))
 docker('restart','-t','15',app);wait(lambda:request(base+'/actuator/health/readiness')[0]==200)
 check('app_restart_preserves_order',request(base+'/api/orders/'+oid,auth)[0]==200)
 starttime=time.monotonic();docker('stop','-t','15',app);elapsed=time.monotonic()-starttime
 exitcode=docker('inspect','--format','{{.State.ExitCode}}',app).stdout.strip();logs=docker('logs',app).stdout
 check('graceful_sigterm',exitcode in ['0','143'] and elapsed<15 and 'Graceful shutdown complete' in logs,{'exit_code':exitcode,'seconds':elapsed})
 check('sensitive_values_absent_from_logs',not any(v in logs for v in secret_values))
 check('trace_context_in_json_logs',trace in logs and 'runtime-check' in logs)
 # Only safe operational application logs after successful secret scan.
 (OUT/'application.log').write_text(logs)
 report['status']='passed'
except Exception as e:
 report['status']='failed';report['failure']=type(e).__name__+': '+str(e)
 for name in names:
  if name.endswith('-app'):
   log=docker('logs',name,check=False).stdout
   for v in secret_values:log=log.replace(v,'[REDACTED]')
   (OUT/'application-failure.log').write_text(log)
 raise
finally:
 if server:server.shutdown()
 for name in reversed(names):docker('rm','-f',name,check=False)
 report['finished_at']=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime());(OUT/'runtime-results.json').write_text(json.dumps(report,indent=2)+'\n')
