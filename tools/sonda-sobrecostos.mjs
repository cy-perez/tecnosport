// Sonda de un solo experimento: ¿qué devuelve `GET /api/v1/finance/extra-charges`?
//
// Es el endpoint por el que la transportadora reliquida un envío —el sobrecosto por peso mal
// declarado que `docs/02-modelo-datos.md` menciona al prohibir inventar pesos— y hoy nadie lo
// mira. De él solo se sabe lo que dice el inventario de la API (`docs/13` §1, "cobros extra,
// paginados") y que el cuerpo del *webhook* de `extra_charges` trae `real_weight`,
// `original_weight` y `discrepancy_weight` (`docs/13` §6.4). **Su respuesta no se ha medido
// nunca**, y el panel de esta cuenta no ofrece ese evento entre los once que se pueden
// suscribir (`docs/13` §6.9): preguntar es la única vía.
//
// Solo lee. No emite ninguna guía y no consume saldo.
//
// Uso:  node tools/sonda-sobrecostos.mjs
//       VOLCAR=1 node tools/sonda-sobrecostos.mjs    (el cuerpo entero, para leer la forma)
//
// Por omisión imprime las **llaves** de cada cobro y los valores de un puñado de campos
// numéricos o de estado, no el objeto completo: un cobro extra cuelga de un envío, y un envío
// lleva los datos de quien compra (`docs/08-seguridad-legal.md`). `VOLCAR=1` existe para la
// primera lectura, cuando hay que ver la forma exacta; lo que se copie de ahí a la documentación
// va sin datos de nadie.
//
// Las credenciales las lee de .env.local / .env (SKYDROPX_URL_BASE, SKYDROPX_CLIENT_ID,
// SKYDROPX_CLIENT_SECRET). No las imprime nunca.

import { readFileSync, existsSync } from 'node:fs';

const RAIZ = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const PAUSA_MS = 600; // el proveedor admite 2 peticiones por segundo

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
const VOLCAR = process.env.VOLCAR === '1';

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

/** Los campos que sí se imprimen con su valor: números, estados e identificadores del cobro. */
const CAMPOS_SEGUROS = [
  'id',
  'type',
  'amount',
  'total',
  'currency',
  'status',
  'reason',
  'description',
  'concept',
  'real_weight',
  'original_weight',
  'discrepancy_weight',
  'volumetric_weight',
  'created_at',
  'updated_at',
  'charged_at',
  'shipment_id',
  'tracking_number',
  'carrier_name',
  'provider_name',
];

function describir(objeto, sangria = '    ') {
  if (objeto === null || typeof objeto !== 'object') {
    console.log(`${sangria}(no es un objeto) ${JSON.stringify(objeto).slice(0, 200)}`);
    return;
  }
  console.log(`${sangria}llaves: ${Object.keys(objeto).join(', ')}`);
  for (const campo of CAMPOS_SEGUROS) {
    if (campo in objeto && objeto[campo] !== null && typeof objeto[campo] !== 'object') {
      console.log(`${sangria}  ${campo} = ${JSON.stringify(objeto[campo])}`);
    }
  }
  for (const [clave, valor] of Object.entries(objeto)) {
    if (valor !== null && typeof valor === 'object' && !Array.isArray(valor)) {
      console.log(`${sangria}  ${clave}: { ${Object.keys(valor).join(', ')} }`);
    }
  }
}

/** Lo que trae la lista: puede venir en `data`, en la raíz, o con otro nombre. */
function elementos(cuerpo) {
  if (Array.isArray(cuerpo)) return cuerpo;
  if (Array.isArray(cuerpo?.data)) return cuerpo.data;
  for (const valor of Object.values(cuerpo ?? {})) {
    if (Array.isArray(valor)) return valor;
  }
  return [];
}

const bearer = await token();
const cabeceras = { Authorization: `Bearer ${bearer}` };
console.log(`Host ${URL_BASE}\n`);

// Primero el saldo, que ya se sabe que responde: si esto contesta y los cobros extra no, el
// problema es de la ruta y no del token ni del espacio `finance`. Sin esta línea, un 404 de
// abajo no se puede distinguir de unas credenciales sin permiso.
const saldo = await llamar('/api/v1/finance/credits', { headers: cabeceras });
console.log(`=== finance/credits (control) === ${saldo.estado}`);
console.log(`    ${JSON.stringify(saldo.cuerpo).slice(0, 200)}\n`);

// Las dos grafías plausibles, y nada más: guion es la que publica el inventario de la API, y el
// guion bajo es la que usan los tipos de evento del webhook. Probar diez rutas inventadas es lo
// que ya costó una sesión con el endpoint del saldo (`docs/13` §6.2).
const RUTAS = ['/api/v1/finance/extra-charges', '/api/v1/finance/extra_charges'];

for (const ruta of RUTAS) {
  const { estado, cuerpo } = await llamar(ruta, { headers: cabeceras });
  console.log(`=== GET ${ruta} === ${estado}`);
  if (estado >= 400) {
    console.log(`    ${JSON.stringify(cuerpo).slice(0, 300)}\n`);
    continue;
  }
  console.log(`    llaves del sobre: ${Object.keys(cuerpo ?? {}).join(', ') || '(arreglo suelto)'}`);
  const lista = elementos(cuerpo);
  console.log(`    elementos: ${lista.length}`);
  if (VOLCAR) {
    console.log(JSON.stringify(cuerpo, null, 2));
  } else {
    lista.slice(0, 3).forEach((elemento, i) => {
      console.log(`    --- cobro ${i + 1} ---`);
      describir(elemento);
      if (elemento?.attributes) {
        console.log('      attributes:');
        describir(elemento.attributes, '        ');
      }
    });
  }
  console.log('');
}
