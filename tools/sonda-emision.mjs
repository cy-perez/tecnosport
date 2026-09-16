// Sonda de emisión: ¿el 422 de 99 minutes era el valor declarado mal puesto?
//
// El 15 de septiembre, con el valor declarado viajando en el campo que Skydropx ignora
// (docs/13 §6.4), `POST /shipments` respondía `422 declared_amount: "Valor declarado es
// obligatorio"` con una tarifa de 99 minutes y `202` con una de Servientrega. La hipótesis es
// que el envío hereda el valor declarado del bulto de la cotización, que iba vacío, y que 99
// minutes lo exige donde Servientrega lo tolera.
//
// **Esta sonda SÍ gasta saldo si la hipótesis es cierta**: un 422 es gratis, un 202 emite una
// guía real y la cobra. Por eso cotiza con el valor declarado en el mínimo (10.000), para que la
// tarifa sea la más barata posible, y **se niega a emitir** si la tarifa supera TOPE.
//
// De paso pide el catálogo de tipos de empaque, que es gratis y es el dato que falta para
// `package_type`.
//
// Uso:  node tools/sonda-emision.mjs

import { readFileSync, existsSync } from 'node:fs';

const RAIZ = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const PAUSA_MS = 600;
const TOPE = 15000; // no emitir nada por encima de esto

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

// VER_ENVIO=<id> relee un envío ya creado: estados, guía y etiqueta por paquete.
if (process.env.VER_ENVIO) {
  const { cuerpo } = await llamar(`/api/v1/shipments/${process.env.VER_ENVIO}`, { headers: H });
  const a = cuerpo.data?.attributes ?? {};
  console.log(
    `workflow_status: ${a.workflow_status} · payment_status: ${a.payment_status}` +
      ` · master_tracking_number: ${JSON.stringify(a.master_tracking_number)}` +
      ` · total: ${a.total} · error_detail: ${JSON.stringify(a.error_detail)}`,
  );
  for (const inc of cuerpo.included ?? []) {
    if (inc.type !== 'package') continue;
    const p = inc.attributes ?? {};
    console.log(
      `  paquete ${inc.id}\n    package_type: ${p.package_type} · declared_amount: ${p.declared_amount}` +
        `\n    tracking_number: ${JSON.stringify(p.tracking_number)} · tracking_status: ${p.tracking_status}` +
        `\n    label_url: ${JSON.stringify(p.label_url)}`,
    );
  }
  process.exit(0);
}

// ---------- gratis: el catálogo que falta para package_type ----------
const empaques = await llamar('/api/v1/shipments/packagings?per_page=20', { headers: H });
console.log('=== Tipos de empaque (package_type) ===');
console.log(`meta: ${JSON.stringify(empaques.cuerpo?.meta)}`);
for (const e of empaques.cuerpo?.data ?? []) {
  console.log(`  ${String(e.code).padEnd(6)} ${e.name}`);
}
if (process.env.SOLO_CATALOGO === '1') {
  process.exit(0);
}

const saldoAntes = await llamar('/api/v1/finance/credits', { headers: H });
console.log(`\nSaldo antes: ${JSON.stringify(saldoAntes.cuerpo)}`);

// ---------- cotización mínima, dentro de Medellín ----------
const NONCE = Date.now().toString().slice(-6);
const DIRECCION_DESTINO = {
  country_code: 'CO',
  postal_code: '05001',
  area_level1: 'Antioquia',
  area_level2: 'Medellín',
  street1: `Calle 50 # 40-20 ${NONCE}`,
  name: 'Comprador de prueba',
  company: 'Comprador de prueba',
  phone: '3001234567',
  email: 'comprador@example.com',
  reference: 'Sin indicaciones adicionales',
};
const DIRECCION_ORIGEN = {
  country_code: 'CO',
  postal_code: '05001',
  area_level1: 'Antioquia',
  area_level2: 'Medellín',
  street1: 'Cra. 26C # 38B-31, apto. 401, La Milagrosa',
  name: 'TecnoSport',
  company: 'TecnoSport',
  phone: '3138816711',
  email: 'contacto@tecnosport.co',
  reference: 'Apartamento 401',
};

const cotizacion = await llamar('/api/v1/quotations', {
  method: 'POST',
  headers: H,
  body: JSON.stringify({
    quotation: {
      address_from: {
        country_code: 'CO',
        postal_code: '05001',
        area_level1: 'Antioquia',
        area_level2: 'Medellín',
      },
      address_to: {
        country_code: 'CO',
        postal_code: '05001',
        area_level1: 'Antioquia',
        area_level2: 'Medellín',
      },
      parcels: [{ length: 20, width: 15, height: 2, weight: 0.1, declared_amount: 10000 }],
    },
  }),
});
if (cotizacion.estado >= 400) {
  console.log(`Cotización falló: ${cotizacion.estado} ${JSON.stringify(cotizacion.cuerpo)}`);
  process.exit(1);
}

let q = cotizacion.cuerpo;
for (let i = 0; i < 10 && !q.is_completed; i++) {
  q = (await llamar(`/api/v1/quotations/${cotizacion.cuerpo.id}`, { headers: H })).cuerpo;
}
console.log(`\n=== Cotización ${q.id} (declarado 10.000) ===`);
for (const t of q.rates || []) {
  console.log(
    `  ${t.provider_name}/${t.provider_service_code} → ${t.status} · ${t.total ?? '—'}` +
      ` · creation_type ${t.shipment_creation_type}`,
  );
}

const tarifa = (q.rates || []).find((t) => t.provider_name === 'ninetynineminutes' && t.success);
if (!tarifa) {
  console.log('\n99 minutes no cotizó esta vez. Sin tarifa no hay prueba; no se emite nada.');
  process.exit(0);
}
if (Number(tarifa.total) > TOPE) {
  console.log(`\nLa tarifa vale ${tarifa.total}, por encima del tope de ${TOPE}. No se emite.`);
  process.exit(0);
}

// ---------- la prueba: emitir con esa tarifa ----------
// Emitir cuesta dinero de verdad, así que hay que pedirlo a propósito. Sin EMITIR=1 la sonda
// llega hasta aquí, dice qué habría hecho y para.
if (process.env.EMITIR !== '1') {
  console.log(
    `\nHabría emitido con ${tarifa.provider_name} por ${tarifa.total}.` +
      ' Para hacerlo de verdad: EMITIR=1 node tools/sonda-emision.mjs',
  );
  process.exit(0);
}

console.log(`\n=== POST /shipments con la tarifa de 99 minutes (${tarifa.total}) ===`);
const envio = await llamar('/api/v1/shipments', {
  method: 'POST',
  headers: H,
  body: JSON.stringify({
    shipment: {
      rate_id: tarifa.id,
      unique_shipment: true,
      address_from: DIRECCION_ORIGEN,
      address_to: DIRECCION_DESTINO,
      packages: [
        {
          package_number: '1',
          package_content: 'Accesorios de tecnología',
          package_type: '4G',
        },
      ],
    },
  }),
});
console.log(`Estado: ${envio.estado}`);
console.log(JSON.stringify(envio.cuerpo).slice(0, 1200));

const saldoDespues = await llamar('/api/v1/finance/credits', { headers: H });
console.log(`\nSaldo después: ${JSON.stringify(saldoDespues.cuerpo)}`);
