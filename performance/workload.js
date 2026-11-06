import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
const lookup=new Trend('product_lookup',true),creation=new Trend('product_creation',true),order=new Trend('order_placement',true),limiter=new Trend('rate_limiter',true),denied=new Counter('limit_denied');
export const options={vus:5,duration:__ENV.DURATION||'15s',summaryTrendStats:['avg','med','p(95)','p(99)','max'],thresholds:{http_req_failed:['rate<0.01'],http_req_duration:['p(95)<500','p(99)<1000'],checks:['rate>0.99']}};
http.setResponseCallback(http.expectedStatuses(200,201,429));
export default function(){
 const headers={Authorization:`Bearer ${__ENV.TOKEN}`,'Content-Type':'application/json'};
 const choose=__ITER%10;let response;
 if(choose<4){response=http.get(`${__ENV.BASE}/api/v1/products/${__ENV.PRODUCT}`,{headers,tags:{name:'lookup'}});lookup.add(response.timings.duration);check(response,{'lookup OK':r=>r.status===200});}
 else if(choose<6){response=http.post(`${__ENV.BASE}/api/v1/products`,JSON.stringify({name:'load product',price:2.5,stock:10}),{headers,tags:{name:'create'}});creation.add(response.timings.duration);check(response,{'create OK':r=>r.status===201});}
 else if(choose<9){headers['Idempotency-Key']=`${__ENV.RUN}-${__VU}-${__ITER}`;response=http.post(`${__ENV.BASE}/api/v1/orders`,JSON.stringify({productId:__ENV.PRODUCT,quantity:1}),{headers,tags:{name:'order'}});order.add(response.timings.duration);check(response,{'order OK':r=>r.status===201});}
 else {response=http.post(`${__ENV.BASE}/api/v1/limits/check`,null,{headers,tags:{name:'limiter'}});limiter.add(response.timings.duration);if(response.status===429)denied.add(1);check(response,{'limit contract':r=>r.status===200||r.status===429});}
 sleep(0.05);
}
export function handleSummary(data){return {[__ENV.RESULT]:JSON.stringify(data,null,2)};}
