#!/usr/bin/env node
// Extrae de `simple-icons` los logos de marca que el sitio usa y escribe su registro generado.
//
// **Por qué un generador y no una dependencia de producción.** `simple-icons` es la fuente
// razonable para un logo de marca —los mantiene, los actualiza cuando una marca se rediseña, y son
// CC0—, pero su único punto de entrada JS es un `index.mjs` de **5,2 MB** con las miles de marcas
// en un solo archivo, y `simple-icons/icons/*` expone `.svg`, no módulos: no hay import por icono.
// Meter eso en el grafo de producción para tres logos contradice lo que este proyecto ya decidió
// dos veces — `iconos.ts` ("solo se agrega un icono cuando hay una pantalla que lo pide") y
// `ADR-0020`, donde 38,6 kB en el paquete inicial bastaron para mover un componente entero.
//
// Así que la librería entra como `devDependency` y de ella sale un archivo con tres `path`. El
// bundle carga tres cadenas; la fuente queda pinneada, auditable y actualizable con un comando. Es
// el mismo trato que `packages/marca` tiene con `copiar-marca.mjs`.
//
// Correr `npm run iconos-marca` después de subir la versión de `simple-icons`, o al agregar una
// marca a la lista de abajo.
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const ORIGEN = join(RAIZ, "node_modules/simple-icons/icons");
const DESTINO = join(RAIZ, "apps/web/src/app/shared/ui/icono/marcas.generado.ts");

/** Las marcas que el sitio enlaza de verdad. Una marca sin enlace no entra. */
const MARCAS = [
  { constante: "marcaFacebook", archivo: "facebook.svg" },
  { constante: "marcaInstagram", archivo: "instagram.svg" },
  { constante: "marcaWhatsapp", archivo: "whatsapp.svg" },
];

const version = JSON.parse(
  readFileSync(join(RAIZ, "node_modules/simple-icons/package.json"), "utf8"),
).version;

function extraer(archivo) {
  const svg = readFileSync(join(ORIGEN, archivo), "utf8");
  const titulo = svg.match(/<title>([^<]+)<\/title>/)?.[1];
  const trazo = svg.match(/<path\s+d="([^"]+)"/)?.[1];
  const vista = svg.match(/viewBox="([^"]+)"/)?.[1];
  if (!titulo || !trazo) {
    throw new Error(`${archivo}: no se pudo leer el título o el path.`);
  }
  // Se comprueba y no se asume: si algún día un icono viniera en otra caja, escalarlo en silencio
  // deformaría el logo, y un logo deformado es un problema de marca, no de CSS.
  if (vista !== "0 0 24 24") {
    throw new Error(`${archivo}: viewBox inesperado (${vista}). El componente asume 24.`);
  }
  return { titulo, trazo };
}

const marcas = MARCAS.map(({ constante, archivo }) => ({ constante, ...extraer(archivo) }));

const contenido = `// GENERADO por tools/generar-iconos-marca.mjs — no editar a mano.
//
// Logos de marca extraídos de simple-icons ${version} (CC0-1.0). Cada uno es un \`path\` **relleno**
// en una caja de 24, que es como se distribuyen las marcas: no son iconos de trazo como los de
// \`iconos.ts\`, y re-trazarlos deformaría el logo. Los dibuja \`ts-icono-marca\`.
//
// Las marcas registradas siguen siendo propiedad de sus titulares; aquí se usan para enlazar los
// perfiles propios del negocio, no como respaldo de nadie.
//
// Para actualizarlos —una marca se rediseña de vez en cuando— \`npm run iconos-marca\`.

/** Un logo de marca: su \`path\` relleno y el nombre con el que la marca se escribe. */
export interface IconoMarca {
  readonly titulo: string;
  readonly trazo: string;
}

${marcas
  .map(
    ({ constante, titulo, trazo }) =>
      `export const ${constante}: IconoMarca = {\n  titulo: '${titulo}',\n  trazo:\n    '${trazo}',\n};`,
  )
  .join("\n\n")}
`;

writeFileSync(DESTINO, contenido);
console.log(
  `iconos de marca: ${marcas.map((m) => m.titulo).join(", ")} desde simple-icons ${version}.`,
);
