#!/usr/bin/env node
// Los textos legales publicados, en una hoja que se pueda imprimir.
//
// El expediente de docs/14 pide llevar a la consulta "los dos textos legales publicados, en su
// versión vigente y con su fecha, no una transcripción". Una transcripción es justo lo que se hace
// sin querer al copiar y pegar de la pantalla, y con un documento de diecinueve secciones basta con
// perder una para que la consulta se haga sobre algo que nadie publicó.
//
// Así que esto no transcribe: lee los mismos JSON de Transloco que pinta el sitio y los recorre en
// el mismo orden que documento-legal.page.html —titulo, parrafos, lista, cierre—, con la versión,
// la vigencia, el aviso del titular y la nota de traducción de cortesía que van en la cabecera.
// Si el texto cambia, esto cambia con él y no hay nada que sincronizar a mano.
//
// Uso: node tools/imprimir-legales.mjs [directorio-de-salida]
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const TEXTOS = join(RAIZ, "apps/web/src/assets/i18n/scopes/legales");
const SALIDA = process.argv[2] ?? join(RAIZ, "docs/tramites/impresos");
const DOCUMENTOS = ["terminos", "privacidad", "cookies"];
const IDIOMAS = ["es", "en"];

function escapar(texto) {
  return String(texto)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

// Sin un solo color ni tamaño de marca: esto no es el sitio, es papel. La regla dura #2 habla del
// frontend, y meter aquí los tokens obligaría a mantener dos copias del sistema visual para algo
// que se imprime en blanco y negro y se archiva.
const ESTILO = `
  @page { margin: 2cm; }
  body { font-family: Georgia, "Times New Roman", serif; line-height: 1.5; max-width: 40em; margin: 2em auto; padding: 0 1em; }
  h1 { font-size: 1.6em; margin-bottom: 0.2em; }
  h2 { font-size: 1.05em; margin-top: 2em; page-break-after: avoid; }
  .entradilla { font-style: italic; }
  .meta { font-size: 0.85em; border-top: 1px solid; border-bottom: 1px solid; padding: 0.8em 0; margin: 1.5em 0 2em; }
  .meta p { margin: 0.35em 0; }
  section { page-break-inside: avoid; }
  footer { margin-top: 3em; padding-top: 1em; border-top: 1px solid; font-size: 0.8em; }
`;

function hoja(legales, documento, idioma) {
  const d = legales[documento];
  const c = legales.comun;
  const partes = [];
  partes.push(`<h1>${escapar(d.titulo)}</h1>`);
  partes.push(`<p class="entradilla">${escapar(d.entradilla)}</p>`);
  partes.push(
    `<div class="meta">` +
      `<p><strong>${escapar(c.version_etiqueta)} ${escapar(c.version)}</strong> · ${escapar(c.vigencia)}</p>` +
      `<p>${escapar(c.titular_aviso)}</p>` +
      `<p>${escapar(c.traduccion_cortesia)}</p>` +
      `</div>`,
  );
  for (const seccion of d.secciones) {
    const cuerpo = [`<h2>${escapar(seccion.titulo)}</h2>`];
    for (const parrafo of seccion.parrafos ?? []) {
      cuerpo.push(`<p>${escapar(parrafo)}</p>`);
    }
    if (seccion.lista) {
      cuerpo.push(`<ul>${seccion.lista.map((p) => `<li>${escapar(p)}</li>`).join("")}</ul>`);
    }
    for (const parrafo of seccion.cierre ?? []) {
      cuerpo.push(`<p>${escapar(parrafo)}</p>`);
    }
    partes.push(`<section>${cuerpo.join("\n")}</section>`);
  }
  // Quién lo generó y de dónde salió, para que nadie discuta si el papel corresponde al sitio.
  partes.push(
    `<footer>Generado desde <code>apps/web/src/assets/i18n/scopes/legales/${idioma}.json</code>` +
      ` el ${new Date().toISOString().slice(0, 10)}. Es el mismo texto que pinta el sitio,` +
      ` no una transcripción.</footer>`,
  );
  return `<!doctype html>
<html lang="${idioma}">
<head><meta charset="utf-8"><title>${escapar(d.titulo)} · ${escapar(c.version)}</title><style>${ESTILO}</style></head>
<body>
${partes.join("\n")}
</body>
</html>
`;
}

mkdirSync(SALIDA, { recursive: true });
let version = null;
const escritos = [];
for (const idioma of IDIOMAS) {
  const json = JSON.parse(readFileSync(join(TEXTOS, `${idioma}.json`), "utf8"));
  const legales = json.legales ?? json;
  // La versión es una sola para todo el corpus y la vigila tools/verificar-datos-de-negocio.mjs.
  // Si aquí discreparan, el papel diría una fecha y la pantalla otra.
  version ??= legales.comun.version;
  if (legales.comun.version !== version) {
    throw new Error(`la versión difiere entre idiomas: ${version} y ${legales.comun.version}`);
  }
  for (const documento of DOCUMENTOS) {
    const ruta = join(SALIDA, `${documento}-${idioma}-${version}.html`);
    writeFileSync(ruta, hoja(legales, documento, idioma));
    escritos.push(ruta);
  }
}
console.log(`Versión ${version}. ${escritos.length} hojas en ${SALIDA}:`);
for (const ruta of escritos) console.log(`  ${ruta.replace(RAIZ, "")}`);
