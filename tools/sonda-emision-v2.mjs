// Sonda de emisión por v2, con el barrio en la cotización.
//
// Responde tres preguntas que el saldo tenía bloqueadas (docs/13 §6.6 y §6.7):
//
//   1. Qué forma tiene la respuesta de `POST /api/v2/shipments`. La documentación dice que
//      "siempre retorna un arreglo de envíos"; eso no se había medido nunca. Todo lo que hay
//      escrito de emisión salió de v1, que devuelve un objeto.
//   2. Si el envío hereda `area_level3` —el barrio— de la cotización. Por el envío no se puede
//      mandar: el esquema de `address_from` no lo declara y se descarta sin avisar (§6.7). Es el
//      campo que `POST /pickups` exige como "Shipper address2".
//   3. Si con el barrio heredado la recolección por fin se programa.
//
// Y una cuarta, del 17 de septiembre, que decide `ADR-0037`: **qué recauda la transportadora en
// la puerta.** La cotización acepta `recipient_pays_shipping` y lo devuelve en `true`, y aun así
// `on_delivery_amount` sigue siendo el valor declarado pelado, sin flete (docs/13 §6.5 y la
// remedición de §6.15). Quedan dos lecturas —el flete se suma al emitir, o el campo no hace
// nada— y solo se separan emitiendo con recaudo y releyendo el envío, que sí expone
// `on_delivery_amount`. Para eso están RECAUDO y FLETE_AL_DESTINATARIO.
//
// `GET /api/v2/shipments/{id}` **no existe** —404 con HTML, medido el 16 de septiembre de 2026—,
// así que releer es siempre por v1.
//
// **Emitir cuesta saldo.** Sin EMITIR=1 la sonda cotiza, dice qué habría hecho y para. Con tope
// de gasto por guía, y eligiendo la tarifa más barata que además recoja por API (`pickup: true`),
// que es la única con la que la recolección se puede ejercer.
//
// Uso:
//   node tools/sonda-emision-v2.mjs                    cotiza y para
//   EMITIR=1 node tools/sonda-emision-v2.mjs           emite una guía y la relee
//   EMITIR=1 BULTOS=2 node tools/sonda-emision-v2.mjs  multienvío: dos bultos, dos guías
//   EMITIR=1 RECOLECCION=1 node tools/sonda-emision-v2.mjs   además programa la recolección
//   VER_ENVIO=<id> node tools/sonda-emision-v2.mjs     relee un envío ya creado
//   SIN_PICKUP=1 TRANSPORTADORA=envia EMITIR=1 node tools/sonda-emision-v2.mjs
//                                                     emite con una transportadora que no recoge
//                                                     por API, cuando la recolección no es lo que
//                                                     se mide
//   RECAUDO=1 FLETE_AL_DESTINATARIO=1 DECLARADO=10000 EMITIR=1 node tools/sonda-emision-v2.mjs
//                                                     cotiza y emite con contraentrega, para ver
//                                                     si `on_delivery_amount` trae el flete
//
// Con RECAUDO=1 el filtro de `pickup` se levanta, y no por descuido: de las tarifas que
// sobreviven a una cotización con recaudo, Coordinadora tiene el contador de remisiones atascado y
// Servientrega se cae con `tariff_price_not_found`, así que solo quedan transportadoras que recogen
// por soporte. Exigir `pickup: true` dejaría la pregunta del recaudo sin ninguna tarifa con la que
// medirla.
//
// Este comentario decía que Envía "es la única que además puede emitir", y el 17 de septiembre eso
// dejó de ser cierto **solo con recaudo**: con él responde `Usuario o Password incorrecto at
// LABEL_NUMBER`, y sin él emite a la primera (medido el 18, docs/13 §6.17). O sea que con RECAUDO=1
// la única que hoy emite de verdad es 99 minutes, a 9.897.

import { readFileSync, existsSync } from 'node:fs';

const RAIZ = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const PAUSA_MS = 600;
const TOPE_POR_GUIA = 15000;

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
const BULTOS = Number(env.BULTOS || 1);
const dormir = (ms) => new Promise((r) => setTimeout(r, ms));
const recorta = (valor, n = 900) => JSON.stringify(valor).slice(0, n);

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

/** Relee un envío y cuenta lo que trae: estados, guías, etiquetas y —lo que importa— el barrio. */
async function releer(id) {
  const { cuerpo } = await llamar(`/api/v1/shipments/${id}`, { headers: H });
  const a = cuerpo.data?.attributes ?? {};
  console.log(
    `  ${id}\n    workflow_status: ${a.workflow_status} · payment_status: ${a.payment_status}` +
      ` · carrier_name: ${a.carrier_name} · service_code: ${a.service_code}` +
      `\n    master_tracking_number: ${JSON.stringify(a.master_tracking_number)}` +
      ` · total: ${a.total} · dias: ${a.estimated_delivery_days}` +
      ` · error_detail: ${JSON.stringify(a.error_detail)}` +
      // Lo que mide la cuarta pregunta. Un envío emitido sin recaudo los trae en `null`, que es
      // cómo se comprobó que el campo existe y que el dato, cuando lo haya, es de fiar.
      `\n    on_delivery_amount: ${JSON.stringify(a.on_delivery_amount)}` +
      ` · on_delivery_status: ${JSON.stringify(a.on_delivery_status)}`,
  );
  for (const inc of cuerpo.included ?? []) {
    const p = inc.attributes ?? {};
    if (inc.type === 'package') {
      console.log(
        `    paquete ${inc.id}\n      tracking_number: ${JSON.stringify(p.tracking_number)}` +
          ` · tracking_status: ${p.tracking_status} · declared_amount: ${p.declared_amount}` +
          `\n      label_url: ${p.label_url ? p.label_url.slice(0, 60) + '…' : 'null'}`,
      );
    }
    if (inc.type === 'address') {
      console.log(
        `    dirección ${p.address_type}: area_level3 ${JSON.stringify(p.area_level3)}` +
          ` · apartment_number ${JSON.stringify(p.apartment_number)}` +
          ` · postal_code ${p.postal_code}`,
      );
    }
  }
  return a.workflow_status;
}

if (env.VER_ENVIO) {
  console.log('=== Envío ===');
  await releer(env.VER_ENVIO);
  process.exit(0);
}

const saldoAntes = await llamar('/api/v1/finance/credits', { headers: H });
console.log(`Saldo antes: ${recorta(saldoAntes.cuerpo)}`);

// ---------- cotización, con el barrio en los dos extremos ----------
const NONCE = Date.now().toString().slice(-6);
const BARRIO_ORIGEN = 'La Milagrosa';
const BARRIO_DESTINO = 'Boston';
// DECLARADO baja el valor declarado para que una medición cueste menos: el piso del proveedor es
// 10.000 por bulto (adr/0035) y con él la diferencia entre "recauda el declarado" y "recauda el
// declarado más el flete" queda imposible de confundir.
const DECLARADO = Number(env.DECLARADO || 120000);
const PARCEL = { length: 20, width: 15, height: 5, weight: 0.5, declared_amount: DECLARADO };
const CON_RECAUDO = env.RECAUDO === '1';
const FLETE_AL_DESTINATARIO = env.FLETE_AL_DESTINATARIO === '1';

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
        area_level3: BARRIO_ORIGEN,
      },
      address_to: {
        country_code: 'CO',
        postal_code: '05001',
        area_level1: 'Antioquia',
        area_level2: 'Medellín',
        area_level3: BARRIO_DESTINO,
        street1: `Calle 50 # 40-20 ${NONCE}`,
      },
      parcels: Array.from({ length: BULTOS }, () => ({ ...PARCEL })),
      // Los dos booleanos del recaudo, que es donde vive toda la contraentrega: el monto no se
      // declara en ninguna parte, sale calculado en `on_delivery_amount` (docs/13 §6.4).
      ...(CON_RECAUDO ? { cash_on_delivery: true } : {}),
      ...(FLETE_AL_DESTINATARIO ? { recipient_pays_shipping: true } : {}),
    },
  }),
});
if (cotizacion.estado >= 400) {
  console.log(`Cotización falló: ${cotizacion.estado} ${recorta(cotizacion.cuerpo)}`);
  process.exit(1);
}

let q = cotizacion.cuerpo;
for (let i = 0; i < 12 && !q.is_completed; i++) {
  q = (await llamar(`/api/v1/quotations/${cotizacion.cuerpo.id}`, { headers: H })).cuerpo;
}
console.log(`\n=== Cotización ${q.id} · ${BULTOS} bulto(s) ===`);
for (const t of q.rates || []) {
  console.log(
    `  ${String(t.provider_name).padEnd(20)} ${String(t.provider_service_code).padEnd(16)}` +
      ` ${String(t.status).padEnd(22)} total ${String(t.total ?? '—').padStart(8)}` +
      ` · pickup ${t.pickup} · ${t.shipment_creation_type}`,
  );
}
if (CON_RECAUDO) {
  console.log(
    `  recaudo → cash_on_delivery: ${q.cash_on_delivery}` +
      ` · recipient_pays_shipping: ${q.recipient_pays_shipping}` +
      ` · on_delivery_amount: ${JSON.stringify(q.on_delivery_amount)}` +
      ` (declarado ${DECLARADO} × ${BULTOS} bulto(s))`,
  );
}

// La más barata de las que además recogen por API: sin `pickup: true` la recolección no se puede
// ejercer, y es la mitad de lo que esta emisión viene a comprobar.
//
// TRANSPORTADORA=<provider_name> fuerza una. Hace falta porque **Coordinadora no puede emitir en
// este sandbox**: su contador de remisiones está atascado y devuelve siempre el mismo
// `codigo_remision` duplicado —el 15 de septiembre a las 22:30 y el 16 a las 18:35, el mismo
// número, `93202421647`—. Es la tarifa más barata y la que este selector elige solo, así que sin
// esta salida la sonda se estrella contra ella una y otra vez. La emisión se reembolsa, pero
// cuesta cinco minutos cada vez.
//
// SIN_PICKUP=1 levanta ese filtro cuando la recolección no es lo que se está midiendo. Nació el 18
// de septiembre de 2026 para la pregunta que dejó abierta §6.15: si Envía dejó de emitir por sus
// credenciales de etiqueta o solo cuando hay recaudo. Esa prueba es **Envía sin recaudo**, y Envía
// recoge solo por soporte (`pickup: false`), así que sin esta salida el filtro la descarta y la
// pregunta se queda sin poderse medir.
const candidatas = (q.rates || [])
  .filter((t) => t.success && (CON_RECAUDO || env.SIN_PICKUP === '1' || t.pickup) && t.total)
  .filter((t) => !env.TRANSPORTADORA || t.provider_name === env.TRANSPORTADORA)
  .sort((a, b) => Number(a.total) - Number(b.total));
const tarifa = candidatas[0];
if (!tarifa) {
  console.log(
    CON_RECAUDO
      ? '\nNinguna tarifa sobrevivió a la cotización con recaudo. No hay con qué medir; no se emite.'
      : '\nNinguna tarifa con `pickup: true` cotizó. Sin ella no hay prueba; no se emite.',
  );
  process.exit(0);
}
if (Number(tarifa.total) > TOPE_POR_GUIA * BULTOS) {
  console.log(`\nLa tarifa vale ${tarifa.total}, por encima del tope. No se emite.`);
  process.exit(0);
}

if (process.env.EMITIR !== '1') {
  console.log(
    `\nHabría emitido con ${tarifa.provider_name}/${tarifa.provider_service_code} por` +
      ` ${tarifa.total} (${tarifa.shipment_creation_type}).` +
      ' Para hacerlo de verdad: EMITIR=1 node tools/sonda-emision-v2.mjs',
  );
  process.exit(0);
}

// ---------- la emisión, por v2 ----------
const paquetes = Array.from({ length: BULTOS }, (_, i) => ({
  package_number: String(i + 1),
  package_content: 'Electrónica y accesorios',
  package_type: '4G',
}));
console.log(
  `\n=== POST /api/v2/shipments · ${tarifa.provider_name} · ${tarifa.total} ·` +
    ` ${tarifa.shipment_creation_type} ===`,
);
const envio = await llamar('/api/v2/shipments', {
  method: 'POST',
  headers: H,
  body: JSON.stringify({
    shipment: {
      rate_id: tarifa.id,
      // Llave de idempotencia por `rate_id`, cache de 96 h: es el remedio del 408 que crea la
      // guía y cobra igual (docs/13 §6.2).
      unique_shipment: true,
      sync_label_creation: false,
      address_from: {
        street1: 'Cra. 26C # 38B-31',
        apartment_number: '401',
        name: 'TecnoSport',
        company: 'TecnoSport',
        // Sin indicativo: `+57…` responde `400 phone no es válido` al emitir (§6.2).
        phone: '3138816711',
        email: 'contacto@tecnosport.co',
        reference: 'Edificio, cuarto piso',
      },
      address_to: {
        street1: `Calle 50 # 40-20 ${NONCE}`,
        name: 'Comprador de prueba',
        company: 'Comprador de prueba',
        phone: '3001234567',
        email: 'comprador@example.com',
        reference: 'Sin indicaciones adicionales',
      },
      packages: paquetes,
    },
  }),
});
console.log(`Estado: ${envio.estado}`);
console.log(`Cuerpo crudo: ${recorta(envio.cuerpo, 3000)}`);
if (envio.estado >= 400) process.exit(1);

// v2 promete un arreglo; se acepta también el objeto de v1 por si la promesa no se cumple.
const datos = Array.isArray(envio.cuerpo?.data) ? envio.cuerpo.data : [envio.cuerpo?.data];
const ids = datos.filter(Boolean).map((d) => d.id);
console.log(`\nEnvíos creados: ${ids.length} → ${ids.join(', ')}`);

// ---------- esperar el estado terminal ----------
// Tres estados no terminales medidos: `in_progress`, `pending` y `creation_waiting`. El último
// es el largo —dos minutos y pico— y no está en la documentación de Skydropx (§6.7).
const NO_TERMINALES = ['in_progress', 'pending', 'creation_waiting'];
for (let vuelta = 0; vuelta < 40; vuelta++) {
  console.log(`\n--- vuelta ${vuelta + 1} ---`);
  const estados = [];
  for (const id of ids) estados.push(await releer(id));
  if (!estados.some((e) => NO_TERMINALES.includes(e))) break;
  await dormir(5000);
}

const saldoDespues = await llamar('/api/v1/finance/credits', { headers: H });
console.log(`\nSaldo después: ${recorta(saldoDespues.cuerpo)}`);

if (process.env.RECOLECCION !== '1') process.exit(0);

// ---------- la recolección, sobre la guía viva ----------
const d = new Date();
do {
  d.setDate(d.getDate() + 1);
} while (d.getDay() === 0 || d.getDay() === 6);
const dia = d.toISOString().slice(0, 10);
for (const id of ids) {
  console.log(`\n=== POST /pickups (envío ${id}, ${dia} 08:00–12:00) ===`);
  const pickup = await llamar('/api/v1/pickups', {
    method: 'POST',
    headers: H,
    body: JSON.stringify({
      pickup: {
        reference_shipment_id: id,
        packages: 1,
        total_weight: 1,
        scheduled_from: `${dia}T08:00:00-05:00`,
        scheduled_to: `${dia}T12:00:00-05:00`,
      },
    }),
  });
  console.log(`${pickup.estado} ${recorta(pickup.cuerpo, 1400)}`);
  const pickupId = pickup.cuerpo?.data?.id;
  if (pickupId) {
    const leida = await llamar(`/api/v1/pickups/${pickupId}`, { headers: H });
    console.log(`GET /pickups/${pickupId} → ${leida.estado} ${recorta(leida.cuerpo, 1200)}`);
  }
}
