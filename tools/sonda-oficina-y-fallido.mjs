// Sonda de los dos pendientes que quedaban de Skydropx y que sí se pueden medir sin gastar saldo.
//
// 1. **La entrega en oficina.** `docs/13` §6.2 la dio por imposible con este argumento: "las cuatro
//    transportadoras que tienen oficinas son exactamente las cuatro que no cotizan". Eso se midió
//    el 15 de septiembre, cuando cinco de las seis tarifas fallaban por el `declared_amount` mal
//    puesto — defecto **nuestro**, corregido ese mismo día (§6.4). Con las tarifas vivas, la
//    premisa hay que volver a mirarla: `office_delivery` y `office_pickup` se declaran por tarifa.
//
// 2. **¿`FALLIDO` es terminal?** `§6.9` lo dejó sin medir a la espera de "que una emisión real
//    vuelva a morir". Ya murieron cuatro y están en la cuenta: basta releerlas. Lo que decide la
//    pregunta es si un envío en `error` llega a tener número de guía — sin número no hay a qué
//    preguntarle el rastreo, y entonces ese estado no puede llegar nunca por el canal de la
//    conciliación.
//
// Todo es de lectura salvo la cotización, que no cuesta nada.
//
// Uso:
//   node tools/sonda-oficina-y-fallido.mjs

import { readFileSync, existsSync } from 'node:fs';

const RAIZ = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const PAUSA_MS = 600;

function cargarEntorno() {
  const valores = {};
  for (const archivo of ['.env', '.env.local']) {
    const ruta = `${RAIZ}/${archivo}`;
    if (!existsSync(ruta)) continue;
    for (const linea of readFileSync(ruta, 'utf8').split(/\r?\n/)) {
      const m = linea.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/);
      if (!m) continue;
      valores[m[1]] = m[2].trim().replace(/^["']|["']$/g, '');
    }
  }
  return { ...valores, ...process.env };
}

const env = cargarEntorno();
const URL_BASE = env.SKYDROPX_URL_BASE || 'https://sb-pro.skydropx.com';
const dormir = (ms) => new Promise((r) => setTimeout(r, ms));

async function llamar(ruta, opciones = {}) {
  await dormir(PAUSA_MS);
  const respuesta = await fetch(`${URL_BASE}${ruta}`, opciones);
  const texto = await respuesta.text();
  let cuerpo;
  try {
    cuerpo = JSON.parse(texto);
  } catch {
    cuerpo = texto.slice(0, 300);
  }
  return { estado: respuesta.status, cuerpo };
}

const { cuerpo: tok } = await llamar('/api/v1/oauth/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    grant_type: 'client_credentials',
    client_id: env.SKYDROPX_CLIENT_ID,
    client_secret: env.SKYDROPX_CLIENT_SECRET,
  }),
});
if (!tok.access_token) throw new Error('No autenticó.');
const H = { 'Content-Type': 'application/json', Authorization: `Bearer ${tok.access_token}` };

// ---------- 1. La entrega en oficina, con tarifas que sí cotizan ----------
const ciudad = (barrio) => ({
  country_code: 'CO',
  postal_code: '05001',
  area_level1: 'Antioquia',
  area_level2: 'Medellín',
  area_level3: barrio,
});

const cot = await llamar('/api/v1/quotations', {
  method: 'POST',
  headers: H,
  body: JSON.stringify({
    quotation: {
      address_from: ciudad('La Milagrosa'),
      address_to: ciudad('Boston'),
      parcels: [{ length: 20, width: 15, height: 2, weight: 0.1, declared_amount: 10000 }],
    },
  }),
});
let q = cot.cuerpo;
for (let i = 0; i < 20 && !q.is_completed; i++) {
  q = (await llamar(`/api/v1/quotations/${cot.cuerpo.id}`, { headers: H })).cuerpo;
}

console.log(`cotizacion ${cot.estado} · id ${q?.id} · completa ${q?.is_completed} · tarifas ${(q?.rates || []).length}`);
if (!q?.rates?.length) console.log(JSON.stringify(q).slice(0, 500));
console.log('=== 1. Entrega en oficina, por tarifa ===');
const vivas = [];
for (const t of q.rates || []) {
  const cotiza = t.success ? 'cotiza' : t.status;
  console.log(
    `  ${t.provider_name}/${t.provider_service_code} → ${cotiza} · ${t.total ?? '—'}` +
      ` · office_delivery ${t.office_delivery} · office_pickup ${t.office_pickup}`,
  );
  if (t.success) vivas.push(t);
}

// `office_points` exige `rate_id` (§6.2). Se pregunta por cada tarifa viva: antes solo se pudo
// preguntar por la de 99 minutes, que es la única que no tiene sucursales.
console.log('\n=== office_points por cada tarifa viva ===');
for (const t of vivas) {
  const r = await llamar(`/api/v1/office_points?rate_id=${t.id}`, { headers: H });
  const total = r.cuerpo?.meta?.total ?? r.cuerpo?.data?.length ?? '?';
  console.log(
    `  ${t.provider_name} → ${r.estado} · total ${total} ${
      r.estado >= 400 ? JSON.stringify(r.cuerpo).slice(0, 160) : ''
    }`,
  );
}

// ---------- 2. ¿Un envío muerto llega a tener guía? ----------
console.log('\n=== 2. Envíos en error: ¿tienen número de guía? ===');
const envios = await llamar('/api/v1/shipments?per_page=30', { headers: H });
const muertos = (envios.cuerpo?.data ?? []).filter(
  (s) => (s.attributes?.workflow_status ?? '') === 'error',
);
if (muertos.length === 0) {
  console.log('  (ninguno en error en la cuenta)');
}
for (const s of muertos) {
  const a = s.attributes ?? {};
  console.log(
    `  ${s.id} · ${a.carrier_name} · guía ${a.master_tracking_number ?? 'null'}` +
      ` · pago ${a.payment_status} · ${(a.error_detail ?? '').toString().slice(0, 90)}`,
  );
}

// Y si alguno tuviera guía, se le pregunta el rastreo: es la única forma de saber si `error`
// viaja también por ese canal o solo por el webhook.
for (const s of muertos) {
  const guia = s.attributes?.master_tracking_number;
  if (!guia) continue;
  const r = await llamar(
    `/api/v1/shipments/tracking?tracking_number=${guia}&carrier_name=${s.attributes.carrier_name}`,
    { headers: H },
  );
  console.log(`  rastreo de ${guia} → ${r.estado} ${JSON.stringify(r.cuerpo).slice(0, 300)}`);
}
