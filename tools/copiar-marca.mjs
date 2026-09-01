#!/usr/bin/env node
// Copia el kit de marca a apps/web como paso del build, no como acción manual
// (docs/04-ui-marca.md). tokens.css y fuentes.css se generan en packages/marca:
// aquí solo se copian, nunca se editan.
import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const raiz = join(dirname(fileURLToPath(import.meta.url)), "..");
const marca = join(raiz, "packages/marca");
const web = join(raiz, "apps/web");

function copiar(origen, destino) {
  if (!existsSync(origen)) {
    console.log(`copiar-marca: se omite, no existe ${origen}`);
    return;
  }
  mkdirSync(dirname(destino), { recursive: true });
  cpSync(origen, destino, { recursive: true, force: true });
}

// src/assets/marca/ es solo del kit: cpSync sobrescribe archivo por archivo.
// No se borra el directorio antes (rmSync recursivo es poco fiable en algunos
// filesystems de red): un archivo que se quite del kit puede quedar huérfano
// aquí hasta la próxima limpieza manual.
const assetsMarca = join(web, "src/assets/marca");
copiar(join(marca, "tokens.css"), join(assetsMarca, "tokens.css"));
copiar(join(marca, "fuentes.css"), join(assetsMarca, "fuentes.css"));
copiar(join(marca, "fuentes"), join(assetsMarca, "fuentes"));
copiar(join(marca, "logo"), join(assetsMarca, "logo"));

// public/ también tiene archivos propios de Angular (ngsw, etc.): se fusiona,
// no se borra entero.
copiar(join(marca, "dist/web"), join(web, "public"));

// site.webmanifest es el nombre del kit; Angular (ng add @angular/pwa) espera
// manifest.webmanifest. Se renombra al copiar.
const manifiestoKit = join(web, "public/site.webmanifest");
if (existsSync(manifiestoKit)) {
  cpSync(manifiestoKit, join(web, "public/manifest.webmanifest"), { force: true });
  rmSync(manifiestoKit);
}

console.log("copiar-marca: listo.");
