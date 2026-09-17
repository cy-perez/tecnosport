// Sonda de un solo experimento: ¿existe un TECHO del valor declarado, y dónde?
//
// El piso lo cerró ADR-0035 con un 422 medido ("El valor declarado debe ser mayor o igual a
// 10000"). El techo, en cambio, solo tenía una fuente y era débil: el formulario de cotización del
// panel acota el valor declarado entre 10.000 y 5.000.000 (docs/13 §6.5). Un campo de un
// formulario web no prueba que la API valide lo mismo, y de eso depende si una variante de más de
// cinco millones tumba la cotización entera como la tumbaba un cable de 8.000.
//
// Dos preguntas, y la segunda importa tanto como la primera:
//   1. ¿La API rechaza por encima de 5.000.000, y en qué número exactamente?
//   2. ¿El límite es POR BULTO o POR COTIZACIÓN? El mínimo resultó ser por bulto, y eso fue lo
//      que convirtió un cable barato en un pedido sin envío. Dos bultos de 3.000.000 responden
//      esa pregunta sin ambigüedad: suman seis millones y ninguno pasa del tope por su cuenta.
//
// Cotizar NO consume saldo: esta sonda no emite ninguna guía. Gasta cuota (2 peticiones/segundo).
//
// Uso:  node tools/sonda-techo-declarado.mjs
//
// Las credenciales las lee de .env.local / .env (SKYDROPX_URL_BASE, SKYDROPX_CLIENT_ID,
// SKYDROPX_CLIENT_SECRET). No las imprime nunca.

import { readFileSync, existsSync } from 'node:fs';

const RAIZ = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const PAUSA_MS = 600; // el proveedor admite 2 peticiones por segundo
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

async function token() {
  const { estado, cuerpo } = await llamar('/api/v1/oauth/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      grant_type: 'client_credentials',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
    }),
  });
  if (estado !== 200 || !cuerpo.access_token) {
    throw new Error(`No autenticó (${estado}): ${JSON.stringify(cuerpo).slice(0, 200)}`);
  }
  return cuerpo.access_token;
}

// El origen real de despacho, tal como lo manda el mapeador hoy — barrio incluido, que es el
// campo cuya ausencia rompió la recolección durante dos sesiones (docs/13 §6.10).
const ORIGEN = {
  country_code: 'CO',
  postal_code: '05001',
  area_level1: 'Antioquia',
  area_level2: 'Medellín',
  area_level3: 'La Milagrosa',
  street1: 'Cra. 26C # 38B-31, apto. 401, La Milagrosa',
  name: 'TecnoSport',
  phone: '3138816711',
};

const DESTINO = {
  country_code: 'CO',
  postal_code: '11001',
  area_level1: 'Bogotá, D.C.',
  area_level2: 'Bogotá',
};

// Un celular: caja pequeña y liviana. Lo que se mueve entre escalones es el declarado, nada más.
const BULTO = { length: 20, width: 15, height: 8, weight: 0.5 };

// El proveedor deduplica cotizaciones por contenido, así que sin algo que cambie una corrida
// nueva devolvería la respuesta congelada de una vieja. Cada escalón ya difiere en el declarado;
// el nonce es para que la sonda se pueda repetir mañana y siga midiendo, no recordando.
const NONCE = process.env.NONCE || Date.now().toString().slice(-6);

const ESCALONES = [
  { clave: 'control · 1.000.000 en un bulto', declarados: [1_000_000] },
  { clave: '4.999.999 · justo debajo del tope del panel', declarados: [4_999_999] },
  { clave: '5.000.000 · el tope exacto del panel', declarados: [5_000_000] },
  { clave: '5.000.001 · justo encima', declarados: [5_000_001] },
  { clave: '6.000.000 · un celular de gama alta', declarados: [6_000_000] },
  { clave: '20.000.000 · bien arriba, por si el tope real es otro', declarados: [20_000_000] },
  {
    clave: 'DOS bultos de 3.000.000 · ¿el límite es por bulto o por cotización?',
    declarados: [3_000_000, 3_000_000],
  },
];

function cuerpo(escalon) {
  return {
    quotation: {
      address_from: ORIGEN,
      address_to: { ...DESTINO, street1: `Calle 50 # 40-20 ${NONCE}` },
      parcels: escalon.declarados.map((declarado) => ({ ...BULTO, declared_amount: declarado })),
    },
  };
}

async function cotizar(bearer, escalon) {
  const cabeceras = { 'Content-Type': 'application/json', Authorization: `Bearer ${bearer}` };
  const creada = await llamar('/api/v1/quotations', {
    method: 'POST',
    headers: cabeceras,
    body: JSON.stringify(cuerpo(escalon)),
  });
  // Un 4xx aquí es la respuesta que la sonda vino a buscar: la validación de entrada, que es
  // donde vive el mínimo. Si el techo existe, tiene que aparecer en este mismo sitio.
  if (creada.estado >= 400) {
    return { rechazo: `${creada.estado} ${JSON.stringify(creada.cuerpo).slice(0, 260)}` };
  }
  const id = creada.cuerpo.id;
  let ultima = creada.cuerpo;
  for (let i = 0; i < INTENTOS_SONDEO && !ultima.is_completed; i++) {
    const sondeo = await llamar(`/api/v1/quotations/${id}`, { headers: cabeceras });
    ultima = sondeo.cuerpo;
  }
  return { id, completa: Boolean(ultima.is_completed), rates: ultima.rates || [] };
}

function pintar(resultado) {
  if (resultado.rechazo) {
    console.log(`    RECHAZADA EN LA ENTRADA · ${resultado.rechazo}`);
    return;
  }
  console.log(`    cotización ${resultado.id} (is_completed: ${resultado.completa})`);
  if (!resultado.rates.length) {
    console.log('    sin tarifas');
    return;
  }
  const texto = (e) =>
    typeof e === 'string' ? e : JSON.stringify(e).replace(/[{}"]/g, '').replace(/,/g, ', ');
  for (const r of resultado.rates) {
    const quien = `${r.provider_name}/${r.provider_service_code ?? r.provider_service_name ?? '?'}`;
    const precio = r.total ? `total ${r.total}` : '—';
    const errores = Array.isArray(r.error_messages)
      ? r.error_messages.map(texto).join(' | ').slice(0, 220)
      : r.error_messages
        ? texto(r.error_messages).slice(0, 220)
        : '';
    const viva = r.status === 'price_found_internal' || r.status === 'price_found';
    console.log(
      `    ${viva ? '✓' : '·'} ${quien.padEnd(34)} ${String(r.status).padEnd(26)} ${precio}` +
        (errores ? `\n        ${errores}` : ''),
    );
  }
}

const bearer = await token();
console.log(`Host ${URL_BASE} · Medellín → Bogotá · nonce ${NONCE}`);
console.log('Ninguna guía se emite aquí: cotizar no consume saldo.\n');

for (const escalon of ESCALONES) {
  console.log(`=== ${escalon.clave} ===`);
  pintar(await cotizar(bearer, escalon));
  console.log('');
}
