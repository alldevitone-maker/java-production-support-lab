import fixture from '../../catalog/institutions.json';

// Explicit browser demo: no HTTP, SQL or Java process is claimed in this mode.
const payments=[];const keys=new Map();
const scenarios=[
  ['normal','Fluxo normal','Aceite, conector, liquidação e confirmação.'],
  ['duplicate-charge','Liquidação duplicada','Compare as duas liquidações.'],
  ['timeout-after-accept','Timeout após aceite','HTTP 504, mas a operação foi registrada.'],
  ['stale-status','Status desatualizado','Os eventos mostram liquidação, mas o status é PENDING.'],
  ['rounding-error','Divergência de centavos','Discrepância fictícia de um centavo entre solicitado e liquidado.'],
  ['missing-correlation','Correlação ausente no log','Identificador omitido no log simulado.'],
  ['wrong-http-status','Código HTTP incorreto','Rejeição com resposta HTTP 200.'],
  ['null-response','Resposta vazia','Resposta vazia e HTTP 502.'],
  ['retry-storm','Excesso de tentativas','Quatro tentativas sem backoff.'],
  ['duplicate-webhook','Webhook duplicado','O mesmo eventId é entregue duas vezes.'],
  ['out-of-order-events','Evento fora de ordem','Evento antigo chega após a liquidação.'],
  ['connector-unavailable','Conector indisponível','Tentativa falha com HTTP 503.']
].map(([id,title,hint])=>({id,title,hint}));
export async function request(path,{method='GET',body,key}={}) {
  if(path==='/catalog')return {status:200,data:{institutions:fixture.map(i=>({...i,connectorId:`connector-${i.id}`,connectorStatus:'READY'})),scenarios,runtime:'BROWSER_DEMO',simulated:true}};
  if(path==='/payments'&&method==='DELETE'){payments.length=0;keys.clear();return {status:200,data:{deleted:true}};}
  if(path==='/payments'&&method==='GET')return {status:200,data:[...payments].reverse()};
  if(path.startsWith('/payments/')&&method==='GET'){
    const payment=payments.find(p=>p.paymentId===path.slice(10));if(!payment)throw Error('Pagamento não encontrado.');return {status:200,data:payment};
  }
  if(path!=='/payments'||method!=='POST')throw Error('Endpoint desconhecido.');
  const hash=JSON.stringify(body),prior=keys.get(key);
  if(prior){if(prior.hash!==hash)throw Error('Chave de idempotência já usada com outro conteúdo.');return {status:200,data:{payment:prior.payment,replayed:true,simulated:true}};}
  if(payments.length>=100)throw Error('Limite de 100 simulações nesta aba. Limpe sua sessão.');
  const target=fixture.find(i=>i.id===body.destinationId),amount=Number(body.amount),scenario=body.scenario;
  if(!target||!fixture.some(i=>i.id===body.sourceId)||body.sourceId===body.destinationId||!Number.isFinite(amount)||amount<0.01||amount>1000000||Math.abs(amount*100-Math.round(amount*100))>1e-6||body.currency!=='BRL'||!scenarios.some(s=>s.id===scenario))throw Error('Confira instituições, cenário e valor.');
  const now=new Date(),random=()=>crypto.randomUUID().replaceAll('-','');
  const p={paymentId:crypto.randomUUID(),sourceId:body.sourceId,destinationId:body.destinationId,amount,settledAmount:amount,currency:'BRL',txid:'SIMTX'+random().slice(0,27),endToEndId:'E00000000'+now.toISOString().replace(/\D/g,'').slice(0,12)+random().slice(0,11),correlationId:'corr-'+crypto.randomUUID(),status:'SETTLED',scenario,createdAt:now.toISOString(),events:[],attempts:[],simulated:true};
  const event=(type,detail,delta=0,eventId=crypto.randomUUID())=>p.events.push({eventId,type,detail,at:new Date(now.getTime()+delta).toISOString(),sequence:p.events.length+1});
  event('PAYMENT_ACCEPTED','Solicitação fictícia aceita pelo simulador de navegador.');
  let status=201,error;
  const responseCode={'connector-unavailable':503,'timeout-after-accept':504,'null-response':502}[scenario]||200;
  for(let i=1;i<=(scenario==='retry-storm'?4:1);i++)p.attempts.push({connectorId:'connector-'+target.id,attempt:i,responseCode,latencyMs:target.latencyMs+({FAST:0,STANDARD:30,SLOW:120}[target.profile]||0),outcome:responseCode===200?'ACCEPTED':'SIMULATED_FAILURE'});
  if(scenario==='connector-unavailable'){p.status='FAILED';p.settledAmount=null;status=503;error='CONNECTOR_UNAVAILABLE';event('CONNECTOR_UNAVAILABLE','Conector fictício indisponível.',1);}
  else if(scenario==='null-response'){p.status='UNKNOWN';p.settledAmount=null;status=502;error='EMPTY_DOWNSTREAM_RESPONSE';event('DOWNSTREAM_EMPTY_RESPONSE','Resultado não confirmado.',1);}
  else if(scenario==='wrong-http-status'){p.status='REJECTED';p.settledAmount=null;status=200;error='SIMULATED_REJECTION';event('PAYMENT_REJECTED','Rejeição simulada com HTTP 200.',1);}
  else {
    event('CONNECTOR_ACCEPTED',`${target.name}: resposta fictícia, sem conexão bancária.`,1);event('PIX_SETTLED','Liquidação fictícia.',2);
    if(scenario==='duplicate-charge')event('PIX_SETTLED','Liquidação duplicada intencional.',3);
    if(scenario==='stale-status')p.status='PENDING';
    if(scenario==='out-of-order-events'){p.status='PENDING';event('PAYMENT_PENDING','Evento antigo aplicado depois da liquidação.',-60000);}
    if(scenario==='rounding-error'){p.settledAmount=Math.round((amount+(amount>0.01?-0.01:0.01))*100)/100;p.status='REVIEW';event('AMOUNT_DIVERGENCE',`Solicitado ${amount}; liquidado ${p.settledAmount}.`,4);}
    const id=crypto.randomUUID();event('WEBHOOK_DELIVERED','Confirmação fictícia.',5,id);if(scenario==='duplicate-webhook')event('WEBHOOK_DELIVERED','Entrega repetida com o mesmo eventId.',6,id);
    if(scenario==='timeout-after-accept'){status=504;error='GATEWAY_TIMEOUT';}
  }
  if(scenario==='missing-correlation')event('LOG_CORRELATION_MISSING','Correlação ausente apenas no log simulado.',7);
  payments.push(p);keys.set(key,{hash,payment:p});
  return {status,data:{payment:p,replayed:false,simulated:true,...(error?{error}:{})}};
}
