// Sonda de rastreo: la forma exacta de `GET /shipments/tracking` y, sobre todo, **con qué nombre
// de transportadora responde**.
//
// La pregunta que la motiva: `GuiaEnvio.transportadora` guarda el nombre para mostrar —lo teclea
// una persona en el panel, o sale de `provider_display_name`— y §6.3 midió el rastreo con el
// *slug* (`servientrega`). Para "99 minutes" el slug es `ninetynineminutes` (§6.1), que no se
// deriva del nombre. Si el endpoint exige el slug, `ConsultorDeSeguimiento` no puede pasar de
// largo lo que guarda la guía.
//
// Todo lo que hace esta sonda es leer. **No cuesta saldo.**
//
// Uso:
//   node tools/sonda-rastreo.mjs                 prueba las guías ya emitidas
//   GUIA=<numero> CARRIER=<nombre> node tools/sonda-rastreo.mjs   una sola

import { readFileSync, existsSync } from "node:fs";

const RAIZ = new URL("..", import.meta.url).pathname.replace(
  /^\/([A-Za-z]:)/,
  "$1",
);
const PAUSA_MS = 600;

function cargarEntorno() {
  const valores = {};
  for (const archivo of [".env", ".env.local"]) {
    const ruta = `${RAIZ}/${archivo}`;
    if (!existsSync(ruta)) continue;
    for (const linea of readFileSync(ruta, "utf8").split(/\r?\n/)) {
      const m = linea.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/);
      if (!m) continue;
      valores[m[1]] = m[2].trim().replace(/^["']|["']$/g, "");
    }
  }
  return { ...valores, ...process.env };
}

const env = cargarEntorno();
const URL_BASE = env.SKYDROPX_URL_BASE || "https://sb-pro.skydropx.com";
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

const { cuerpo: tok } = await llamar("/api/v1/oauth/token", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    grant_type: "client_credentials",
    client_id: env.SKYDROPX_CLIENT_ID,
    client_secret: env.SKYDROPX_CLIENT_SECRET,
  }),
});
if (!tok.access_token) throw new Error("No autenticó.");
const H = {
  Accept: "application/json",
  Authorization: `Bearer ${tok.access_token}`,
};

// Las guías emitidas hasta hoy, con el slug de quien las emitió (docs/13 §6.2 a §6.7).
const GUIAS = process.env.GUIA
  ? [
      {
        guia: process.env.GUIA,
        slug: process.env.CARRIER || "servientrega",
        nombre: process.env.CARRIER || "?",
      },
    ]
  : [
      { guia: "873837506712", slug: "servientrega", nombre: "Servientrega" },
      { guia: "2269401749", slug: "servientrega", nombre: "Servientrega" },
      { guia: "1543555745", slug: "ninetynineminutes", nombre: "99 minutes" },
      { guia: "3838859118", slug: "ninetynineminutes", nombre: "99 minutes" },
    ];

const cuantos = (c) =>
  Array.isArray(c?.data)
    ? `${c.data.length} eventos`
    : JSON.stringify(c).slice(0, 120);

for (const { guia, slug, nombre } of GUIAS) {
  console.log(`\n=== guía ${guia} (${nombre} / ${slug})`);

  const variantes = [
    [
      "con slug        ",
      `/api/v1/shipments/tracking?tracking_number=${guia}&carrier_name=${encodeURIComponent(slug)}`,
    ],
    ["sin carrier_name", `/api/v1/shipments/tracking?tracking_number=${guia}`],
    [
      "con nombre      ",
      `/api/v1/shipments/tracking?tracking_number=${guia}&carrier_name=${encodeURIComponent(nombre)}`,
    ],
    [
      "en la ruta      ",
      `/api/v1/shipments/tracking/${guia}/${encodeURIComponent(slug)}`,
    ],
  ];

  for (const [etiqueta, ruta] of variantes) {
    const r = await llamar(ruta, { headers: H });
    console.log(`  ${etiqueta} → ${r.estado} · ${cuantos(r.cuerpo)}`);
  }
}

// El cuerpo entero de la primera que responda, para fijar la forma como fixture.
const primera = GUIAS[0];
const completo = await llamar(
  `/api/v1/shipments/tracking?tracking_number=${primera.guia}&carrier_name=${primera.slug}`,
  { headers: H },
);
console.log(
  `\n=== cuerpo completo de ${primera.guia}\n${JSON.stringify(completo.cuerpo, null, 2)}`,
);
