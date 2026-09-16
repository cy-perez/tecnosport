// Sonda de recolección: el tramo que ningún ADR contempla, de punta a punta.
//
// `docs/13` §2.2 lo dejó con dos cuerpos incompatibles y §6.4 resolvió cuál es el bueno leyendo
// la documentación. Falta ejercerlo: cobertura → programar → consultar.
//
// Solo tres transportadoras admiten recolección por API —Coordinadora, Inter Rapidísimo y
// Servientrega—; 99 minutos y Envía la piden por soporte (docs/13 §6.4). Por eso emite con
// Coordinadora, que además es la más barata de las tres.
//
// **Emitir cuesta saldo y por eso hay que pedirlo con EMITIR=1.** Cobertura, programación y
// consulta son gratis.
//
// Uso:
//   node tools/sonda-recoleccion.mjs                 cotiza y dice qué haría
//   EMITIR=1 node tools/sonda-recoleccion.mjs        emite y hace el ciclo entero
//   ENVIO=<id> node tools/sonda-recoleccion.mjs      reusa un envío ya emitido
//   VER_PICKUP=<id> node tools/sonda-recoleccion.mjs relee una recolección

import { readFileSync, existsSync } from 'node:fs';

const RAIZ = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const PAUSA_MS = 600;
const TOPE = Number(process.env.TOPE || 8000); // techo de gasto por guía, en pesos
const CARRIERS_CON_API = ['coordinadora', 'servientrega', 'interrapidisimo'];

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
    cuerpo = texto.slice(0, 400);
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

const recorta = (o, n = 900) => JSON.stringify(o).slice(0, n);

if (process.env.VER_PICKUP) {
  const r = await llamar(`/api/v1/pickups/${process.env.VER_PICKUP}`, { headers: H });
  console.log(`${r.estado} ${recorta(r.cuerpo, 1200)}`);
  process.exit(0);
}

const saldo = await llamar('/api/v1/finance/credits', { headers: H });
console.log(`Saldo: ${recorta(saldo.cuerpo)}`);

// ---------- 1. envío del que colgar la recolección ----------
let envioId = process.env.ENVIO;

if (!envioId) {
  const NONCE = Date.now().toString().slice(-6);
  const ciudad = {
    country_code: 'CO',
    postal_code: '05001',
    area_level1: 'Antioquia',
    area_level2: 'Medellín',
  };
  const cot = await llamar('/api/v1/quotations', {
    method: 'POST',
    headers: H,
    body: JSON.stringify({
      quotation: {
        address_from: ciudad,
        address_to: ciudad,
        parcels: [{ length: 20, width: 15, height: 2, weight: 0.1, declared_amount: 10000 }],
      },
    }),
  });
  if (cot.estado >= 400) {
    console.log(`Cotización falló: ${cot.estado} ${recorta(cot.cuerpo)}`);
    process.exit(1);
  }
  let q = cot.cuerpo;
  for (let i = 0; i < 10 && !q.is_completed; i++) {
    q = (await llamar(`/api/v1/quotations/${cot.cuerpo.id}`, { headers: H })).cuerpo;
  }

  console.log(`\n=== Cotización ${q.id} ===`);
  for (const t of q.rates || []) {
    const conApi = CARRIERS_CON_API.includes(t.provider_name) ? ' · recolección por API' : '';
    console.log(
      `  ${t.provider_name}/${t.provider_service_code} → ${t.status} · ${t.total ?? '—'}` +
        ` · pickup ${t.pickup} · pickup_via_support ${t.pickup_via_support}` +
        ` · pickup_ocurre ${t.pickup_ocurre} · pickup_package_min ${t.pickup_package_min}${conApi}`,
    );
  }

  // CARRIER=<nombre> fuerza una transportadora; si no, la más barata de las que recogen por API.
  const candidatas = (q.rates || [])
    .filter((t) => t.success && CARRIERS_CON_API.includes(t.provider_name))
    .filter((t) => !process.env.CARRIER || t.provider_name === process.env.CARRIER)
    .sort((a, b) => Number(a.total) - Number(b.total));
  const tarifa = candidatas[0];
  if (!tarifa) {
    console.log('\nNinguna transportadora con recolección por API cotizó. No se emite nada.');
    process.exit(0);
  }
  if (Number(tarifa.total) > TOPE) {
    console.log(`\nLa más barata con API vale ${tarifa.total}, sobre el tope ${TOPE}. No se emite.`);
    process.exit(0);
  }
  if (process.env.EMITIR !== '1') {
    console.log(
      `\nHabría emitido con ${tarifa.provider_name} por ${tarifa.total}.` +
        ' Para hacerlo: EMITIR=1 node tools/sonda-recoleccion.mjs',
    );
    process.exit(0);
  }

  const direccion = (extra) => ({
    country_code: 'CO',
    postal_code: '05001',
    area_level1: 'Antioquia',
    area_level2: 'Medellín',
    ...extra,
  });
  const envio = await llamar('/api/v1/shipments', {
    method: 'POST',
    headers: H,
    body: JSON.stringify({
      shipment: {
        rate_id: tarifa.id,
        unique_shipment: true,
        // `POST /pickups` sobre una guía con esta dirección incompleta responde
        // `422 base: "Shipper address2 not valid: null"`. En el envío los únicos campos de
        // dirección que quedaban vacíos eran `apartment_number` y `area_level3`: van llenos.
        address_from: direccion({
          street1: 'Cra. 26C # 38B-31',
          street_number: '38B-31',
          apartment_number: '401',
          area_level3: 'La Milagrosa',
          name: 'TecnoSport',
          company: 'TecnoSport',
          phone: '3138816711',
          email: 'contacto@tecnosport.co',
          reference: 'Edificio, cuarto piso',
        }),
        address_to: direccion({
          street1: `Calle 50 # 40-20 ${NONCE}`,
          name: 'Comprador de prueba',
          company: 'Comprador de prueba',
          phone: '3001234567',
          email: 'comprador@example.com',
          reference: 'Sin indicaciones adicionales',
        }),
        packages: [
          { package_number: '1', package_content: 'Accesorios de tecnología', package_type: '4G' },
        ],
      },
    }),
  });
  console.log(`\n=== POST /shipments (${tarifa.provider_name}, ${tarifa.total}) → ${envio.estado}`);
  if (envio.estado >= 400) {
    console.log(recorta(envio.cuerpo));
    process.exit(1);
  }
  envioId = envio.cuerpo?.data?.id;
  console.log(`envío ${envioId}`);
}

// ---------- 1b. esperar a que el envío exista de verdad ----------
// La respuesta 202 llega con workflow_status `in_progress` y sin guía: la transportadora
// todavía no ha contestado. Pedir la cobertura de recolección antes de eso devuelve
// `422 {"success": false, "message": null}`, que no dice nada.
for (let i = 0; i < 20; i++) {
  const { cuerpo } = await llamar(`/api/v1/shipments/${envioId}`, { headers: H });
  const a = cuerpo.data?.attributes ?? {};
  console.log(
    `  envío: ${a.workflow_status} · guía ${JSON.stringify(a.master_tracking_number)}` +
      `${a.error_detail ? ' · error ' + JSON.stringify(a.error_detail) : ''}`,
  );
  if (a.workflow_status !== 'in_progress' && a.workflow_status !== 'pending') break;
  await dormir(5000);
}

// ---------- 2. cobertura de fechas ----------
console.log(`\n=== GET /pickups/coverage (envío ${envioId}) ===`);
const cobertura = await llamar(`/api/v1/pickups/coverage?shipment_id=${envioId}`, { headers: H });
console.log(`${cobertura.estado} ${recorta(cobertura.cuerpo, 1000)}`);

let fechas = cobertura.cuerpo?.pickupDates || cobertura.cuerpo?.data?.pickupDates || [];

// La cobertura viene respondiendo 422 con `message: null` para todo envío que se le pase, viva o
// muerta la guía. Programar no la exige: FORZAR=1 arma una ventana del próximo día hábil y
// llama igual, que es la forma de saber si el 422 es de la cobertura o de la recolección entera.
if (!fechas.length && process.env.FORZAR === '1') {
  const d = new Date();
  do {
    d.setDate(d.getDate() + 1);
  } while (d.getDay() === 0 || d.getDay() === 6);
  const dia = d.toISOString().slice(0, 10);
  fechas = [{ date: dia, startHour: '08:00:00', endHour: '12:00:00' }];
  console.log(`\nSin cobertura; se fuerza una ventana para el ${dia}.`);
}

if (!fechas.length) {
  console.log('\nSin fechas de cobertura: no hay con qué programar. Repite con FORZAR=1. Fin.');
  process.exit(0);
}

// ---------- 3. programar ----------
const f = fechas[0];
const desde = `${f.date}T${f.startHour}-05:00`;
const hasta = `${f.date}T${f.endHour}-05:00`;
console.log(`\n=== POST /pickups (${desde} → ${hasta}) ===`);
const pickup = await llamar('/api/v1/pickups', {
  method: 'POST',
  headers: H,
  body: JSON.stringify({
    pickup: {
      reference_shipment_id: envioId,
      packages: 1,
      // El OpenAPI declara `["number", "string"]`, pero 0.1 responde
      // `422 total_weight: "debe ser un entero"`. Va en kilos y entero.
      total_weight: 1,
      scheduled_from: desde,
      scheduled_to: hasta,
    },
  }),
});
console.log(`${pickup.estado} ${recorta(pickup.cuerpo, 1400)}`);

// ---------- 4. consultar ----------
const pickupId = pickup.cuerpo?.data?.id;
if (pickupId) {
  console.log(`\n=== GET /pickups/${pickupId} ===`);
  const leida = await llamar(`/api/v1/pickups/${pickupId}`, { headers: H });
  console.log(`${leida.estado} ${recorta(leida.cuerpo, 1200)}`);
}

const saldoFinal = await llamar('/api/v1/finance/credits', { headers: H });
console.log(`\nSaldo final: ${recorta(saldoFinal.cuerpo)}`);
