// Sonda de cobertura: ¿a cuántos de los 1122 municipios de Colombia se puede despachar de verdad?
//
// Los términos y condiciones publican "Despachamos a todo el territorio nacional", y esa frase
// descansa sobre una muestra de DOS ciudades — Medellín y Bogotá (docs/13 §6.5). Nadie ha medido
// el resto. El checkout, mientras tanto, tiene ENVIO_SIN_COBERTURA y cae a la recogida en el
// punto: la pregunta no es si el mecanismo funciona, es a cuánta gente le toca.
//
// Cotiza cada municipio dos veces —sin recaudo y con recaudo— porque son dos coberturas
// distintas: sobrevivir a una cotización con recaudo es la señal de contraentrega (docs/13 §6).
// Cotizar NO consume saldo: esta sonda no emite ninguna guía. Solo gasta tiempo y las dos
// peticiones por segundo de la cuenta.
//
// Uso:
//   node tools/sonda-cobertura.mjs                      los 1122 municipios
//   node tools/sonda-cobertura.mjs --capitales          solo las 33 capitales, para una prueba corta
//   node tools/sonda-cobertura.mjs --desde 400          retoma en el municipio 400
//   node tools/sonda-cobertura.mjs --limite 50          corta a los 50 primeros
//
// Es reanudable a propósito: cuatro horas contra un proveedor lento se cortan, y volver a empezar
// desde cero es como se acaba no midiendo nunca. El progreso se guarda en `cobertura-medida.json`
// tras cada municipio, y una corrida nueva continúa donde quedó. Lo que SÍ se vuelve a intentar es
// lo que falló: un fallo no es una medición.
//
// Las credenciales las lee de .env.local / .env (SKYDROPX_URL_BASE, SKYDROPX_CLIENT_ID,
// SKYDROPX_CLIENT_SECRET). No las imprime nunca.

import { readFileSync, existsSync, writeFileSync } from 'node:fs';

const RAIZ = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const PAUSA_MS = 600; // el proveedor admite 2 peticiones por segundo
const INTENTOS_SONDEO = 8;
const SALIDA = `${RAIZ}/cobertura-medida.json`;

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

const argumentos = process.argv.slice(2);
const soloCapitales = argumentos.includes('--capitales');
const valorDe = (bandera, omision) => {
  const i = argumentos.indexOf(bandera);
  return i >= 0 && argumentos[i + 1] ? Number(argumentos[i + 1]) : omision;
};
const DESDE = valorDe('--desde', 0);
const LIMITE = valorDe('--limite', Infinity);

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

async function autenticar() {
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

/**
 * El token caduca, y esta sonda corre cuatro horas.
 *
 * <p>La primera corrida completa lo aprendió de la peor manera: autenticaba una vez al arrancar y a
 * partir del municipio 597 **todo** respondió `401`. Trescientos setenta y siete municipios
 * quedaron contados como "no se pudo medir" y el resumen imprimió un 62,8 % de cobertura que no
 * significaba nada — el número no medía el país, medía cuándo caducó el token. Es la misma lección
 * que `docs/09` ya tenía escrita en otro contexto: **una herramienta de diagnóstico también es una
 * variable del experimento.**
 *
 * <p>Dos defensas y no una, porque la de tiempo sola vuelve a depender de adivinar la vigencia:
 * se renueva por reloj cada media hora, y además cualquier `401` fuerza una reautenticación y un
 * reintento. Si el reintento también da `401`, eso sí es un fallo de verdad.
 */
const VIGENCIA_TOKEN_MS = 30 * 60 * 1000;
let bearerActual = null;
let bearerDesde = 0;

async function bearer(forzarRenovacion = false) {
  if (forzarRenovacion || !bearerActual || Date.now() - bearerDesde > VIGENCIA_TOKEN_MS) {
    bearerActual = await autenticar();
    bearerDesde = Date.now();
  }
  return bearerActual;
}

/**
 * Los 1122 municipios salen de la misma lista DIVIPOLA que ya usa el checkout, no de una copia
 * aparte: si el DANE cambia la división territorial, esta sonda mide lo mismo que el formulario le
 * ofrece a quien compra. Se lee con expresiones regulares en vez de importar el `.ts` para que la
 * sonda siga siendo Node a secas, sin pasar por el compilador de Angular.
 */
function municipios() {
  const ruta = `${RAIZ}/apps/web/src/app/features/checkout/domain/geografia-co.datos.ts`;
  const fuente = readFileSync(ruta, 'utf8');

  const departamentos = new Map();
  const bloqueDepartamentos = fuente.slice(
    fuente.indexOf('export const DEPARTAMENTOS'),
    fuente.indexOf('export const MUNICIPIOS'),
  );
  for (const m of bloqueDepartamentos.matchAll(/\{ codigo: '(\d+)', nombre: '([^']+)' \}/g)) {
    departamentos.set(m[1], m[2]);
  }

  const bloqueMunicipios = fuente.slice(fuente.indexOf('export const MUNICIPIOS'));
  const lista = [];
  const patron =
    /\{ codigoDepartamento: '(\d+)', codigo: '(\d+)', nombre: '((?:[^'\\]|\\.)+)' \}/g;
  for (const m of bloqueMunicipios.matchAll(patron)) {
    lista.push({
      codigoDepartamento: m[1],
      departamento: departamentos.get(m[1]) ?? m[1],
      codigo: m[2],
      nombre: m[3].replace(/\\'/g, "'"),
    });
  }
  if (lista.length === 0) {
    throw new Error('No se pudo leer la lista DIVIPOLA: ¿cambió el formato del archivo?');
  }
  return lista;
}

// Origen real de despacho, el de application.yml.
const ORIGEN = {
  country_code: 'CO',
  postal_code: '05001',
  area_level1: 'Antioquia',
  area_level2: 'Medellín',
  area_level3: 'La Milagrosa',
  street1: 'Cra. 26C # 38B-31, apto. 401',
  name: 'TecnoSport',
  phone: '3138816711',
};

// Un solo artículo de peso y valor medianos: lo único que varía entre una medición y la siguiente
// tiene que ser el destino. Estas medidas son las de un celular con su caja, que es el grueso del
// catálogo.
const BULTO = { length: 25, width: 18, height: 8, weight: 0.8 };
const VALOR_DECLARADO = 250000;

// El proveedor deduplica cotizaciones por contenido: sin algo que cambie, una corrida nueva
// devolvería la respuesta congelada de una vieja.
const NONCE = process.env.NONCE || Date.now().toString().slice(-6);

function cuerpo(municipio, conRecaudo) {
  const quotation = {
    address_from: ORIGEN,
    address_to: {
      country_code: 'CO',
      postal_code: municipio.codigo,
      area_level1: municipio.departamento,
      area_level2: municipio.nombre,
      area_level3: 'Centro',
      street1: `Calle 10 # 10-10 ${NONCE}`,
    },
    parcels: [{ ...BULTO, declared_amount: VALOR_DECLARADO }],
    declared_amount: VALOR_DECLARADO,
  };
  if (conRecaudo) {
    quotation.cash_on_delivery = true;
    quotation.on_delivery_amount = VALOR_DECLARADO;
  }
  return { quotation };
}

async function cotizar(municipio, conRecaudo, yaReintento = false) {
  const cabeceras = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${await bearer()}`,
  };
  const creada = await llamar('/api/v1/quotations', {
    method: 'POST',
    headers: cabeceras,
    body: JSON.stringify(cuerpo(municipio, conRecaudo)),
  });
  if (creada.estado === 401 && !yaReintento) {
    // El token caducó en mitad de la corrida. Se renueva y se reintenta una vez; si vuelve a
    // fallar, entonces sí es un fallo que hay que contar como tal.
    await bearer(true);
    return cotizar(municipio, conRecaudo, true);
  }
  if (creada.estado >= 400) {
    // Un 422 no es lo mismo que una caída: el proveedor contestó y rechazó el cuerpo. La
    // distinción importa porque una es "aquí no hay cobertura" y la otra "no pudimos preguntar",
    // y mezclarlas es justo el defecto que adr/0039 corrigió en el checkout.
    return {
      resultado: creada.estado === 422 ? 'rechazada' : 'fallo',
      detalle: `${creada.estado} ${JSON.stringify(creada.cuerpo).slice(0, 160)}`,
      transportadoras: [],
    };
  }
  const id = creada.cuerpo.id;
  let ultima = creada.cuerpo;
  for (let i = 0; i < INTENTOS_SONDEO && !ultima.is_completed; i++) {
    ultima = (await llamar(`/api/v1/quotations/${id}`, { headers: cabeceras })).cuerpo;
  }
  const tarifas = ultima.rates || [];
  const vivas = tarifas.filter(
    (r) => r.status === 'price_found_internal' || r.status === 'price_found_external',
  );
  // docs/13 §6.5: is_completed puede volver en true con una tarifa todavía en `pending`, y el
  // mapeador del backend la descarta. Se cuenta aparte, porque si esto ocurre a escala significa
  // que el checkout pierde tarifas en silencio — y a veces la más barata.
  const pendientes = tarifas.filter((r) => r.status === 'pending').length;
  return {
    resultado: vivas.length > 0 ? 'cubierto' : 'sin_cobertura',
    completa: Boolean(ultima.is_completed),
    pendientes,
    transportadoras: vivas.map((r) => ({
      proveedor: r.provider_name,
      servicio: r.provider_service_code ?? r.provider_service_name ?? '?',
      total: r.total,
    })),
  };
}

let lista = municipios();
if (soloCapitales) {
  // La capital de cada departamento es el municipio cuyo código termina en 001.
  lista = lista.filter((m) => m.codigo.endsWith('001'));
}
const aMedir = lista.slice(DESDE, DESDE + LIMITE);

const previo = existsSync(SALIDA) ? JSON.parse(readFileSync(SALIDA, 'utf8')) : { medidos: {} };
const medidos = previo.medidos ?? {};

console.log(
  `Host ${URL_BASE} · ${aMedir.length} municipios (de ${lista.length}) · nonce ${NONCE}\n` +
    `Cotizar no gasta saldo. Estimado: ~${Math.round((aMedir.length * 4 * PAUSA_MS) / 60000)} min.\n`,
);

let hechos = 0;

for (const municipio of aMedir) {
  const clave = municipio.codigo;
  // Un fallo NO se da por medido: se vuelve a intentar en la corrida siguiente. Sin esto, los 377
  // municipios que la primera corrida perdió por el token caducado se habrían quedado contados
  // como "no se pudo medir" para siempre, y el resumen habría seguido imprimiendo un porcentaje
  // sobre un país a medias.
  if (medidos[clave] && medidos[clave].sinRecaudo.resultado !== 'fallo') {
    hechos++;
    continue;
  }
  const sinRecaudo = await cotizar(municipio, false);
  const conRecaudo = await cotizar(municipio, true);
  medidos[clave] = {
    departamento: municipio.departamento,
    municipio: municipio.nombre,
    sinRecaudo,
    conRecaudo,
  };
  hechos++;

  const marca =
    sinRecaudo.resultado === 'cubierto' ? (conRecaudo.resultado === 'cubierto' ? '✓✓' : '✓·') : '··';
  console.log(
    `${String(hechos).padStart(4)}/${aMedir.length} ${marca} ` +
      `${municipio.departamento} / ${municipio.nombre} (${clave})` +
      (sinRecaudo.resultado === 'cubierto'
        ? ` — ${sinRecaudo.transportadoras.map((t) => t.proveedor).join(', ')}`
        : ` — ${sinRecaudo.resultado}${sinRecaudo.detalle ? ': ' + sinRecaudo.detalle : ''}`),
  );

  // Se guarda tras cada municipio, no al final: si esto se corta en el 800, lo medido se queda.
  writeFileSync(
    SALIDA,
    JSON.stringify({ host: URL_BASE, nonce: NONCE, medidos }, null, 2),
    'utf8',
  );
}

const filas = Object.values(medidos);
const cubiertos = filas.filter((f) => f.sinRecaudo.resultado === 'cubierto');
const conContraentrega = filas.filter((f) => f.conRecaudo.resultado === 'cubierto');
const fallos = filas.filter((f) => f.sinRecaudo.resultado === 'fallo');
const conPendientes = filas.filter((f) => (f.sinRecaudo.pendientes ?? 0) > 0);

const porcentaje = (n) => ((n / filas.length) * 100).toFixed(1);

console.log(`\n=== Cobertura medida sobre ${filas.length} municipios ===`);
console.log(`  Envío a domicilio:  ${cubiertos.length} (${porcentaje(cubiertos.length)} %)`);
console.log(
  `  Contraentrega:      ${conContraentrega.length} (${porcentaje(conContraentrega.length)} %)`,
);
console.log(`  Sin cobertura:      ${filas.length - cubiertos.length - fallos.length}`);
console.log(`  No se pudo medir:   ${fallos.length}`);
console.log(
  `  Con tarifas 'pending' al cerrar: ${conPendientes.length}` +
    ' — cada una es una tarifa que el checkout descarta sin verla (docs/13 §6.5)',
);

const porDepartamento = new Map();
for (const fila of filas) {
  const actual = porDepartamento.get(fila.departamento) ?? { total: 0, cubiertos: 0 };
  actual.total++;
  if (fila.sinRecaudo.resultado === 'cubierto') actual.cubiertos++;
  porDepartamento.set(fila.departamento, actual);
}
console.log('\n=== Por departamento ===');
for (const [departamento, { total, cubiertos: c }] of [...porDepartamento].sort()) {
  console.log(`  ${departamento.padEnd(24)} ${String(c).padStart(4)}/${String(total).padEnd(4)}`);
}
console.log(`\nDetalle en ${SALIDA}`);
