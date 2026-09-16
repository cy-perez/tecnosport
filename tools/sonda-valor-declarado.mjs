// Sonda de un solo experimento: ¿dónde quiere Skydropx el valor declarado?
//
// La documentación (OpenAPI de sb-pro y el artículo "Cómo crear envíos con Inter Rapidísimo vía
// API") dice que `declared_amount` va DENTRO de cada `parcel`. Hoy el mapeador manda
// `declared_value` en el bulto y `declared_amount` al nivel de la cotización, y cuatro
// transportadoras responden como si el valor declarado no les llegara.
//
// Compara tres cuerpos idénticos salvo por ese campo y muestra, tarifa por tarifa, qué contesta
// cada transportadora. Cotizar no consume saldo: esta sonda no emite ninguna guía.
//
// Uso:  node tools/sonda-valor-declarado.mjs
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

// Origen real de despacho, el de application.yml.
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
  { etiqueta: 'Bogotá', postal_code: '11001', area_level1: 'Bogotá, D.C.', area_level2: 'Bogotá' },
  { etiqueta: 'Medellín', postal_code: '05001', area_level1: 'Antioquia', area_level2: 'Medellín' },
];

const VALOR_DECLARADO = 250000;
const BULTO = { length: 30, width: 25, height: 10, weight: 1.0 };

// El proveedor deduplica cotizaciones por contenido: sin algo que cambie, una corrida nueva
// devolvería la respuesta congelada de una vieja. El nonce es el mismo para las tres variantes,
// así que siguen siendo comparables entre sí.
const NONCE = process.env.NONCE || Date.now().toString().slice(-6);

const VARIANTES = [
  {
    clave: 'A · declared_amount en el bulto',
    parcel: { ...BULTO, declared_amount: VALOR_DECLARADO },
  },
  {
    clave: 'B · declared_value en el bulto (lo que manda hoy el mapeador)',
    parcel: { ...BULTO, declared_value: VALOR_DECLARADO },
  },
  {
    clave: 'C · los dos campos en el bulto',
    parcel: { ...BULTO, declared_amount: VALOR_DECLARADO, declared_value: VALOR_DECLARADO },
  },
  {
    clave: 'D · solo en el bulto, sin declared_amount de cotización',
    parcel: { ...BULTO, declared_amount: VALOR_DECLARADO },
    sinDeclaradoDeCotizacion: true,
  },
  {
    clave: 'E · en el bulto, por debajo del mínimo (8.000)',
    parcel: { ...BULTO, declared_amount: 8000 },
    declaradoDeCotizacion: 8000,
  },
];

function cuerpo(destino, variante) {
  const quotation = {
    address_from: ORIGEN,
    address_to: {
      country_code: 'CO',
      postal_code: destino.postal_code,
      area_level1: destino.area_level1,
      area_level2: destino.area_level2,
      street1: `Calle 50 # 40-20 ${NONCE}`,
    },
    parcels: [variante.parcel],
  };
  if (!variante.sinDeclaradoDeCotizacion) {
    quotation.declared_amount = variante.declaradoDeCotizacion ?? VALOR_DECLARADO;
  }
  return { quotation };
}

async function cotizar(bearer, destino, variante) {
  const cabeceras = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${bearer}`,
  };
  const creada = await llamar('/api/v1/quotations', {
    method: 'POST',
    headers: cabeceras,
    body: JSON.stringify(cuerpo(destino, variante)),
  });
  if (creada.estado >= 400) {
    return { error: `${creada.estado} ${JSON.stringify(creada.cuerpo).slice(0, 220)}` };
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
  if (resultado.error) {
    console.log(`    ERROR ${resultado.error}`);
    return;
  }
  console.log(`    cotización ${resultado.id} (is_completed: ${resultado.completa})`);
  if (!resultado.rates.length) {
    console.log('    sin tarifas');
    return;
  }
  for (const r of resultado.rates) {
    const quien = `${r.provider_name}/${r.provider_service_code ?? r.provider_service_name ?? '?'}`;
    const precio = r.total ? `total ${r.total}` : '—';
    const texto = (e) =>
      typeof e === 'string' ? e : JSON.stringify(e).replace(/[{}"]/g, '').replace(/,/g, ', ');
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
console.log(`Host ${URL_BASE} · valor declarado ${VALOR_DECLARADO} · nonce ${NONCE}\n`);

for (const destino of DESTINOS) {
  console.log(`=== Medellín → ${destino.etiqueta} ===`);
  for (const variante of VARIANTES) {
    console.log(`  ${variante.clave}`);
    pintar(await cotizar(bearer, destino, variante));
  }
  console.log('');
}
