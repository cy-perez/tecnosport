#!/usr/bin/env node
// ¿Dice el sitio el mismo teléfono, el mismo NIT y el mismo correo en todas partes?
//
// El 10 de septiembre de 2026 se corrigió el celular del negocio: estaba mal en el pie y en tres
// párrafos de los textos legales, publicado durante toda una fase. Se arregló en los cuatro sitios y
// no quedó nada que impidiera que volviera a pasar: el teléfono vive hoy en diez copias y en dos
// formatos (+573138816711 en el pie, "313 881 6711" en la prosa legal), el NIT en ocho y el correo
// en catorce. Ninguna prueba los cruzaba — `pie.spec.ts` compara la plantilla contra el mismo JSON
// que la alimenta, y la prueba de los legales compara la *estructura* entre idiomas, no los valores.
//
// La fuente de verdad es el bloque `pie` de es.json: son los datos que la Ley 1480 obliga a publicar
// y los que el pie enseña. Cualquier otra aparición tiene que coincidir.
//
// Y la versión de los legales, que es el caso más grave de los tres: si `legales.comun.version` se
// separa de POLITICA_DATOS_VERSION, cada fila de `autorizacion_datos` apunta a una versión del texto
// que nunca se publicó, y ese registro existe precisamente para poder demostrar qué aceptó quien
// compró.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const TEXTOS = join(RAIZ, "apps/web/src/assets/i18n");

const problemas = [];

function jsons(directorio) {
  return readdirSync(directorio).flatMap((entrada) => {
    const ruta = join(directorio, entrada);
    return statSync(ruta).isDirectory() ? jsons(ruta) : ruta.endsWith(".json") ? [ruta] : [];
  });
}

function leer(ruta) {
  return JSON.parse(readFileSync(ruta, "utf8"));
}

const es = leer(join(TEXTOS, "es.json"));
const en = leer(join(TEXTOS, "en.json"));

// --- 1. Los datos que no son traducción: idénticos en los dos idiomas.
// `direccion` y `horario` quedan fuera a propósito: la inglesa añade ", Colombia" para quien lee de
// fuera, y el horario se traduce. De la dirección se comprueba la calle más abajo, que es el dato.
const DATOS_IDENTICOS = [
  "nit",
  "telefono_e164",
  "whatsapp_numero",
  "correo",
  "facebook_url",
  "instagram_url",
  "nombre_comercial",
];
for (const clave of DATOS_IDENTICOS) {
  if (es.pie[clave] !== en.pie[clave]) {
    problemas.push(
      `pie.${clave}: es.json dice ${JSON.stringify(es.pie[clave])} y en.json ${JSON.stringify(en.pie[clave])}`,
    );
  }
}

// --- 2. Toda aparición de un dato del negocio coincide con la del pie.
const soloDigitos = (valor) => valor.replace(/\D/g, "");
const celular = (valor) => soloDigitos(valor).slice(-10);

const calleCanonica = es.pie.direccion.split(",")[0].trim();

const reglas = [
  {
    nombre: "teléfono",
    patron: /(?:\+?57[\s-]?)?3\d{2}[\s.-]?\d{3}[\s.-]?\d{4}/g,
    normaliza: celular,
    canonico: celular(es.pie.telefono_e164),
  },
  {
    nombre: "NIT",
    patron: /\b\d{8,10}-\d\b/g,
    normaliza: soloDigitos,
    canonico: soloDigitos(es.pie.nit),
  },
  {
    nombre: "correo",
    patron: /[\w.+-]+@tecnosport\.co\b/g,
    normaliza: (valor) => valor.toLowerCase(),
    canonico: es.pie.correo.toLowerCase(),
  },
  {
    nombre: "dirección del punto",
    patron: /Cra\.?\s*26C[^,)\n"]*/g,
    normaliza: (valor) => valor.trim().replace(/\s+/g, " "),
    canonico: calleCanonica.replace(/\s+/g, " "),
  },
];

for (const ruta of jsons(TEXTOS)) {
  const contenido = readFileSync(ruta, "utf8");
  contenido.split("\n").forEach((linea, indice) => {
    for (const { nombre, patron, normaliza, canonico } of reglas) {
      for (const encontrado of linea.match(patron) ?? []) {
        if (normaliza(encontrado) !== canonico) {
          problemas.push(
            `${relative(RAIZ, ruta)}:${indice + 1}  ${nombre} "${encontrado}" no coincide con el del pie ("${canonico}")`,
          );
        }
      }
    }
  });
}

// --- 3. La versión de los legales, en los cuatro sitios donde vive.
const legalesEs = leer(join(TEXTOS, "scopes/legales/es.json"));
const legalesEn = leer(join(TEXTOS, "scopes/legales/en.json"));
const version = legalesEs.comun.version;

if (legalesEn.comun.version !== version) {
  problemas.push(
    `legales.comun.version: es.json dice ${version} y en.json ${legalesEn.comun.version}`,
  );
}

const yml = readFileSync(join(RAIZ, "apps/api/bootstrap/src/main/resources/application.yml"), "utf8");
const enYml = yml.match(/politica-datos-version:\s*\$\{POLITICA_DATOS_VERSION:([^}]+)\}/)?.[1];
if (enYml !== version) {
  problemas.push(
    `application.yml: POLITICA_DATOS_VERSION por omisión es ${enYml}, y el texto publicado es ${version}. La constancia de autorización apuntaría a una versión que nadie leyó.`,
  );
}

const env = readFileSync(join(RAIZ, ".env.example"), "utf8");
const enEnv = env.match(/^POLITICA_DATOS_VERSION=(.+)$/m)?.[1]?.trim();
if (enEnv !== version) {
  problemas.push(`.env.example: POLITICA_DATOS_VERSION es ${enEnv}, y el texto publicado es ${version}`);
}

if (problemas.length > 0) {
  console.error(`\n${problemas.length} dato(s) del negocio que no coinciden:\n`);
  for (const problema of problemas) {
    console.error(`  ${problema}`);
  }
  console.error(
    "\nEstos datos tienen una sola fuente: el bloque `pie` de es.json, y la versión de los\n" +
      "legales en `legales.comun.version`. Cambiar uno exige cambiarlos todos en el mismo commit.\n",
  );
  process.exit(1);
}

console.log("Los datos del negocio coinciden en todas sus copias.");
