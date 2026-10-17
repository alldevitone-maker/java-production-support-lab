const demo = import.meta.env.VITE_LAB_MODE === 'demo';
let memorySession;
function session() {
  try {
    let value = sessionStorage.getItem('payops-session-v2');
    if (!value) {value = crypto.randomUUID();sessionStorage.setItem('payops-session-v2',value);}
    return value;
  } catch {return memorySession ||= crypto.randomUUID();}
}
export async function request(path, {method='GET',body,key}={}) {
  if (demo) return (await import('./demo.js')).request(path,{method,body,key});
  const response = await fetch(`${import.meta.env.VITE_API_BASE || ''}/api${path}`,{
    method, headers:{'Content-Type':'application/json','X-Lab-Session':session(),
      ...(key?{'Idempotency-Key':key}:{}),'X-Correlation-Id':`corr-${crypto.randomUUID()}`},
    ...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(15000)
  });
  const data=await response.json();
  if(!response.ok&&!data.payment)throw new Error(data.message||data.error||`HTTP ${response.status}`);
  return {data,status:response.status};
}
export const isBrowserDemo=demo;
