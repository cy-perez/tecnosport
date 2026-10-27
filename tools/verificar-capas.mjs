#!/usr/bin/env node
// Verifica la dirección de las dependencias en el frontend.
//
// Existe porque `eslint-plugin-boundaries` **no verificaba nada**: la
// configuración legada la acepta el plugin v7 y no la aplica, y su
// clasificación de elementos tampoco funciona sin `mode: 'full'`. Se comprobó
// metiendo violaciones a propósito y el lint pasaba. Un guardián que nunca
// dispara es peor que no tener guardián, porque da confianza falsa.
//
// Esto no reemplaza al plugin en general: solo aplica las cuatro reglas que
// este proyecto necesita, sobre los imports relativos entre capas, y una quinta
// sobre los paquetes: `domain` no importa framework. Es corto a propósito — se
// lee entero en un minuto y se prueba metiendo una violación.
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, resolve, dirname, sep } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ_POR_OMISION = "apps/web/src/app";

// Dos excepciones, y las dos están en el propio enunciado de las reglas.
//
// `*.routes.ts`: el mensaje de la regla dice literalmente que "el proveedor de
// la ruta decide la implementación de infrastructure". Ese proveedor vive en el
// archivo de rutas, así que prohibírselo era prohibir justo lo que la regla
// manda hacer.
//
// `*.spec.ts`: una prueba monta el escenario, y eso incluye sembrar el
// almacenamiento local o inyectar un adaptador. No es código que se despliegue.
// Se informan aparte para que no desaparezcan de la vista.
const ES_RUTAS = /\.routes\.ts$/;
const ES_PRUEBA = /\.spec\.ts$/;

/** A qué capa pertenece un archivo, por su ruta. */
function capaDe(rutaRelativa) {
  const p = rutaRelativa.split(sep).join("/");
  // `admin/` agrupa las funcionalidades del panel un nivel más abajo —features/admin/pedidos/domain—
  // y la expresión de antes solo admitía un nivel: las 14 del panel, 128 archivos, quedaban fuera
  // del grafo sin que nada lo dijera. Mismo agujero que tuvo core/ hasta el 24 de septiembre.
  const m = p.match(
    /^features\/(?:admin\/)?[^/]+\/(domain|application|infrastructure|presentation)\//,
  );
  if (m) return m[1];
  if (p.startsWith("shared/")) return "shared";
  // core/ es transversal: lo usa la aplicación entera, así que no puede depender de una
  // funcionalidad concreta. Estaba fuera del grafo —"como antes", decía esta línea— y con eso el
  // guardián tenía un agujero justo donde viven la sesión, el HTTP, el i18n, el SEO y el cargador
  // de imágenes global, que importaba el modelo de `catalogo`.
  //
  // `layout/` se queda fuera a propósito, y no por descuido: componer funcionalidades es
  // literalmente su trabajo. La insignia del encabezado tiene que ver el mismo carrito que la ficha
  // y la página del carrito, y `apps/web/CLAUDE.md` documenta ese `CarritoStore` singleton como la
  // excepción correcta. Meterlo en la regla convertiría un diseño decidido en un aviso permanente,
  // que es la forma más rápida de que un guardián deje de leerse.
  if (p.startsWith("core/")) return "transversal";
  return null; // layout/ y la raíz (app.config, app.routes): fuera del grafo
}

// Qué NO puede importar cada capa. Mismas reglas que `eslint.config.js`
// declaraba y no aplicaba.
const PROHIBIDO = {
  domain: {
    capas: ["application", "infrastructure", "presentation"],
    motivo: "domain no depende de nada. Sin HttpClient, sin Angular.",
  },
  application: {
    capas: ["infrastructure", "presentation"],
    motivo: "application solo depende de domain (los puertos, no sus adaptadores).",
  },
  presentation: {
    capas: ["infrastructure"],
    motivo:
      "presentation inyecta el puerto declarado en domain; el proveedor de la ruta decide la implementación de infrastructure.",
  },
  transversal: {
    capas: ["domain", "application", "infrastructure", "presentation"],
    motivo:
      "core/ lo usa la aplicación entera: no puede depender de una funcionalidad. Si hace falta un tipo, se declara aquí — el cargador de imágenes lo hace con VarianteDeImagen.",
  },
  shared: {
    capas: ["domain", "application", "infrastructure", "presentation"],
    motivo:
      "shared/ no depende de ninguna funcionalidad. Si solo lo usa una, el componente va dentro de ella; si es compartido de verdad, recibe entradas primitivas en vez del tipo del dominio.",
  },
};

function* archivosTs(dir) {
  for (const entrada of readdirSync(dir)) {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) yield* archivosTs(ruta);
    else if (entrada.endsWith(".ts")) yield ruta;
  }
}

// Tres formas de importar un relativo, y las tres cuentan: `import ... from`, `export ... from`, el
// `import '...'` sin nada que importar y el `import('...')` dinámico. Solo se miraba la primera.
const IMPORTS = [
  /(?:^|\n)\s*(?:import|export)\s[^;]*?from\s+['"](\.[^'"]+)['"]/g,
  /(?:^|\n)\s*import\s+['"](\.[^'"]+)['"]/g,
  /\bimport\(\s*['"](\.[^'"]+)['"]\s*\)/g,
];

// La quinta regla, que los imports relativos no podían ver: `domain` va sin framework, y un paquete
// se importa por su nombre, no por una ruta. Cinco `domain/query-params-filtro.ts` importaron
// `Params` de `@angular/router` sin que este guardián se enterara, hasta el 27 de octubre de 2026.
//
// **Una excepción, y es el patrón del proyecto, no un descuido**: `InjectionToken` de
// `@angular/core`. El puerto vive en `domain` y su token con él, porque es lo que inyecta quien lo
// usa y lo que provee la ruta (`apps/web/CLAUDE.md`). Cualquier otro nombre de `@angular/core`
// —`inject`, `signal`, `computed`— ya es comportamiento de Angular y no cabe.
const FRAMEWORK = /^(?:@angular\/|@tanstack\/|@jsverse\/|rxjs(?:\/|$))/;
const PERMITIDO_EN_DOMAIN = { "@angular/core": new Set(["InjectionToken"]) };

const PAQUETES = [
  /(?:^|\n)\s*(?:import|export)\s([^;]*?)from\s+['"]([^.'"][^'"]*)['"]/g,
  /(?:^|\n)\s*import\s+()['"]([^.'"][^'"]*)['"]/g,
  /\bimport\(\s*()['"]([^.'"][^'"]*)['"]\s*\)/g,
];

/**
 * Lo que nombra un `import { a, type B as C }`: `["a", "B"]`. `null` si además trae algo fuera de
 * las llaves —un import por omisión o un `* as`—, que ya no se puede reducir a una lista de nombres.
 */
function nombresImportados(clausula) {
  const llaves = clausula.match(/\{([^}]*)\}/);
  const fuera = clausula
    .replace(/\{[^}]*\}/, "")
    .replace(/^\s*type\b/, "")
    .trim();
  if (!llaves || fuera !== "") return null;
  return llaves[1]
    .split(",")
    .map(
      (nombre) =>
        nombre
          .trim()
          .replace(/^type\s+/, "")
          .split(/\s+as\s+/)[0],
    )
    .filter(Boolean);
}

/** ¿Este import de paquete se sale de lo que `domain` admite? */
function frameworkProhibido(clausula, paquete) {
  if (!FRAMEWORK.test(paquete)) return false;
  const permitidos = PERMITIDO_EN_DOMAIN[paquete];
  if (!permitidos) return true;
  const nombres = nombresImportados(clausula);
  return nombres === null || nombres.length === 0 || nombres.some((n) => !permitidos.has(n));
}

/** Las dependencias invertidas de un árbol, separadas entre código y pruebas. */
export function revisar(raiz) {
  const violaciones = [];
  const enPruebas = [];
  for (const archivo of archivosTs(raiz)) {
    const rel = relative(raiz, archivo);
    const desde = capaDe(rel);
    const regla = desde && PROHIBIDO[desde];
    if (!regla) continue;
    if (ES_RUTAS.test(archivo)) continue;

    const fuente = readFileSync(archivo, "utf8");
    for (const expresion of IMPORTS) {
      for (const m of fuente.matchAll(expresion)) {
        const destino = capaDe(relative(raiz, resolve(dirname(archivo), m[1])));
        if (destino && regla.capas.includes(destino)) {
          (ES_PRUEBA.test(archivo) ? enPruebas : violaciones).push({
            archivo: relative(".", archivo).split(sep).join("/"),
            desde,
            destino,
            especificador: m[1],
            motivo: regla.motivo,
          });
        }
      }
    }
    if (desde !== "domain") continue;
    for (const expresion of PAQUETES) {
      for (const m of fuente.matchAll(expresion)) {
        if (!frameworkProhibido(m[1], m[2])) continue;
        (ES_PRUEBA.test(archivo) ? enPruebas : violaciones).push({
          archivo: relative(".", archivo).split(sep).join("/"),
          desde,
          destino: "framework",
          especificador: m[2],
          motivo:
            "domain no importa framework: ni Angular (salvo InjectionToken para el puerto), ni TanStack, ni Transloco, ni RxJS. Si hace falta un tipo, se declara aquí.",
        });
      }
    }
  }
  return { violaciones, enPruebas };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  const { violaciones, enPruebas } = revisar(RAIZ_POR_OMISION);
  if (enPruebas.length > 0) {
    console.log(
      `aviso: ${enPruebas.length} import(s) de este tipo en archivos de prueba, que no fallan el build:`,
    );
    for (const v of enPruebas) {
      console.log(`  ${v.archivo}  ${v.desde} -> ${v.destino} (${v.especificador})`);
    }
    console.log("");
  }

  if (violaciones.length === 0) {
    console.log("capas: ninguna dependencia invertida en codigo de produccion");
    process.exit(0);
  }

  for (const v of violaciones) {
    console.log(
      `${v.archivo}\n  ${v.desde} -> ${v.destino}  (${v.especificador})\n  ${v.motivo}\n`,
    );
  }
  console.log(`dependencias invertidas: ${violaciones.length}`);
  process.exit(1);
}
