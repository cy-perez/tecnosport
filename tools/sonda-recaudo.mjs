// Sonda de contraentrega: ¿está activo el servicio, y qué transportadoras lo cubren?
//
// La medición del 11 de septiembre —"sobrevivir a una cotización con recaudo es la señal de
// cobertura"— se hizo cuando la única tarifa viva era 99 minutes y cuando el valor declarado
// viajaba en el campo equivocado (docs/13 §6.4). Hay que rehacerla.
//
// Dos preguntas, y son distintas:
//   1. ¿La cuenta tiene habilitada la contraentrega? La documentación dice que
//      `cash_on_delivery` "solo está disponible cuando la feature está habilitada", y que
//      `on_delivery_amount` es el monto a cobrar (valor declarado + flete si aplica). Si vuelve
//      `null` con el recaudo pedido, el servicio no está activo.
//   2. ¿Qué transportadoras lo admiten? Las que no, se caen con restricciones propias.
//
// Cotizar no consume saldo: esta sonda no emite ninguna guía.
//
// Y una tercera, que apareció el 17 de septiembre y es la que decide un ADR:
//   3. ¿`recipient_pays_shipping` hace algo? La cotización lo **acepta y lo devuelve en
//      `true``, y aun así `on_delivery_amount` sigue siendo el valor declarado pelado, sin
//      flete. Quedan dos lecturas: o el flete se suma al emitir —el "si aplica" de la
//      documentación— o el campo no hace nada. Distinguirlas cuesta una emisión, así que antes
//      hay que saber si el objeto del envío expone siquiera esos campos: si no los expone,
//      emitir tampoco contestaría y no hay por qué gastar el saldo. Eso es `VER_ENVIO`.
//
// Cotizar no consume saldo: esta sonda no emite ninguna guía.
//
// Uso:  node tools/sonda-recaudo.mjs        (NONCE=... reusa la caché de Skydropx)
//       VER_ENVIO=<id> node tools/sonda-recaudo.mjs   busca los campos de contraentrega en un
//                                                     envío ya emitido, sin gastar nada

import { readFileSync, existsSync } from 'node:fs';

const RAIZ = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const PAUSA_MS = 600;
const INTENTOS_SONDEO = 10;

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
const CLIENT_ID = env.SKYDROPX_CLIENT_ID;
const CLIENT_SECRET = env.SKYDROPX_CLIENT_SECRET;

if (!CLIENT_ID || !CLIENT_SECRET || /pendiente_de_configurar/.test(CLIENT_ID)) {
  console.error('Faltan SKYDROPX_CLIENT_ID / SKYDROPX_CLIENT_SECRET en .env.local.');
  process.exit(1);
}

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

const ORIGEN = {
  country_code: 'CO',
  postal_code: '05001',
  area_level1: 'Antioquia',
  area_level2: 'Medellín',
  street1: 'Cra. 26C # 38B-31, apto. 401, La Milagrosa',
  name: 'TecnoSport',
  phone: '3138816711',
};

const DESTINOS = [
  { etiqueta: 'Medellín', postal_code: '05001', area_level1: 'Antioquia', area_level2: 'Medellín' },
  { etiqueta: 'Bogotá', postal_code: '11001', area_level1: 'Bogotá, D.C.', area_level2: 'Bogotá' },
];

const VALOR_DECLARADO = 250000;
const BULTO = { length: 30, width: 25, height: 10, weight: 1.0, declared_amount: VALOR_DECLARADO };
const NONCE = process.env.NONCE || Date.now().toString().slice(-6);

const VARIANTES = [
  { clave: 'sin recaudo (referencia)', extra: {} },
  { clave: 'con recaudo', extra: { cash_on_delivery: true } },
  {
    clave: 'con recaudo + flete al destinatario',
    extra: { cash_on_delivery: true, recipient_pays_shipping: true },
  },
];

async function cotizar(bearer, destino, variante) {
  const cabeceras = { 'Content-Type': 'application/json', Authorization: `Bearer ${bearer}` };
  const cuerpo = {
    quotation: {
      address_from: ORIGEN,
      address_to: {
        country_code: 'CO',
        postal_code: destino.postal_code,
        area_level1: destino.area_level1,
        area_level2: destino.area_level2,
        street1: `Calle 50 # 40-20 ${NONCE}`,
      },
      parcels: [BULTO],
      ...variante.extra,
    },
  };
  const creada = await llamar('/api/v1/quotations', {
    method: 'POST',
    headers: cabeceras,
    body: JSON.stringify(cuerpo),
  });
  if (creada.estado >= 400) {
    return { error: `${creada.estado} ${JSON.stringify(creada.cuerpo).slice(0, 220)}` };
  }
  let ultima = creada.cuerpo;
  for (let i = 0; i < INTENTOS_SONDEO && !ultima.is_completed; i++) {
    ultima = (await llamar(`/api/v1/quotations/${creada.cuerpo.id}`, { headers: cabeceras })).cuerpo;
  }
  return { respuesta: ultima };
}

const texto = (e) =>
  typeof e === 'string' ? e : JSON.stringify(e).replace(/[{}"]/g, '').replace(/,/g, ', ');

function pintar(resultado) {
  if (resultado.error) {
    console.log(`    ERROR ${resultado.error}`);
    return;
  }
  const r = resultado.respuesta;
  console.log(
    `    id ${r.id} · cash_on_delivery: ${r.cash_on_delivery}` +
      ` · recipient_pays_shipping: ${r.recipient_pays_shipping}` +
      ` · on_delivery_amount: ${JSON.stringify(r.on_delivery_amount)}`,
  );
  for (const t of r.rates || []) {
    const quien = `${t.provider_name}/${t.provider_service_code ?? '?'}`;
    const viva = t.success === true;
    const errores = Array.isArray(t.error_messages)
      ? t.error_messages.map(texto).join(' | ').slice(0, 170)
      : '';
    console.log(
      `    ${viva ? '✓' : '·'} ${quien.padEnd(34)} ${String(t.status).padEnd(24)}` +
        `${t.total ? 'total ' + t.total : '—'}${errores ? `\n        ${errores}` : ''}`,
    );
  }
}

const { cuerpo: tok } = await llamar('/api/v1/oauth/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    grant_type: 'client_credentials',
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
  }),
});
const bearer = tok.access_token;
if (!bearer) throw new Error('No autenticó.');

// RELEER=<id> vuelve a leer una cotización ya creada, para ver si una tarifa que quedó en
// `pending` con la cotización ya `is_completed` termina resolviéndose después.
if (process.env.RELEER) {
  const { cuerpo } = await llamar(`/api/v1/quotations/${process.env.RELEER}`, {
    headers: { Authorization: `Bearer ${bearer}` },
  });
  console.log(`is_completed: ${cuerpo.is_completed}`);
  for (const t of cuerpo.rates || []) {
    console.log(
      `  ${t.provider_name}/${t.provider_service_code} → ${t.status}` +
        ` · success ${t.success} · ${t.total ?? '—'}`,
    );
  }
  process.exit(0);
}

// VER_ENVIO=<id>: relee un envío ya creado y reporta **toda** clave que hable de contraentrega,
// a cualquier profundidad y también dentro de `included` —que es donde este proveedor guarda lo
// que uno busca en `attributes` (docs/13 §6.14)—. Por v1: `GET /api/v2/shipments/{id}` no existe.
if (process.env.VER_ENVIO) {
  const { estado, cuerpo } = await llamar(`/api/v1/shipments/${process.env.VER_ENVIO}`, {
    headers: { Authorization: `Bearer ${bearer}` },
  });
  console.log(`GET /api/v1/shipments/${process.env.VER_ENVIO} → ${estado}`);
  const hallazgos = [];
  (function recorrer(nodo, camino) {
    if (nodo === null || typeof nodo !== 'object') return;
    for (const [clave, valor] of Object.entries(nodo)) {
      const aqui = camino ? `${camino}.${clave}` : clave;
      if (/delivery|cash|cod|collect/i.test(clave)) {
        hallazgos.push(`  ${aqui} = ${JSON.stringify(valor)}`);
      }
      recorrer(valor, aqui);
    }
  })(cuerpo, '');
  if (hallazgos.length) {
    console.log(`Campos de contraentrega en el envío (${hallazgos.length}):`);
    for (const linea of hallazgos) console.log(linea);
  } else {
    console.log(
      'El envío NO expone ningún campo de contraentrega. Emitir no contestaría la pregunta 3.',
    );
  }
  process.exit(0);
}

const saldo = await llamar('/api/v1/finance/credits', {
  headers: { Authorization: `Bearer ${bearer}` },
});
console.log(`Saldo: ${JSON.stringify(saldo.cuerpo)} · valor declarado ${VALOR_DECLARADO}\n`);

for (const destino of DESTINOS) {
  console.log(`=== Medellín → ${destino.etiqueta} ===`);
  for (const variante of VARIANTES) {
    console.log(`  ${variante.clave}`);
    pintar(await cotizar(bearer, destino, variante));
  }
  console.log('');
}
