#!/usr/bin/env node
// ¿Las clases de Tailwind que usa el frontend existen de verdad?
//
// Existe porque las escalas por omisión de Tailwind están borradas
// (`apps/web/src/tailwind.css`, ADR-0020) y **una clase que no existe no falla:
// simplemente no hace nada**. No hay linter que avise. Pasó con `min-h-0` y
// `min-h-auto`.
//
// Dos modos:
//
//   node tools/verificar-clases-tailwind.mjs min-h-tactil bg-ts-primario   pregunta por esas
//   node tools/verificar-clases-tailwind.mjs                               barre el frontend
//
// El primero es para cuando se escribe una clase nueva y se quiere saber antes de usarla. El
// segundo es el guardián, y corre dentro de `npm run verificar`: hasta el 21 de septiembre de
// 2026 solo existía el primero, o sea que la regla dura #8 la sostenía que alguien se acordara
// de preguntar, clase por clase.
//
// Dos detalles que costaron caro y por eso están resueltos aquí:
//
//  1. `@source inline(...)`. Tailwind solo genera las clases que **encuentra**
//     en el código, así que preguntar por una clase nueva sin forzarla como
//     candidato siempre respondería "no existe". Con esto se comprueba si la
//     clase es válida, no si además ya se usa.
//  2. El escapado. En el CSS generado, `focus-visible:outline-2` es el selector
//     `.focus-visible\:outline-2`. Escapar de menos —solo los dos puntos, o
//     olvidarse de la barra de `bg-ts-marca-fuerte/60`— da falsos negativos.
//     Se escapa todo lo que no sea alfanumérico.
//
// **Qué barre y qué no, dicho antes de que alguien lo suponga.** Un `class="…"` estático, en una
// plantilla `.html` o en un `template:` de un `.ts`, y un `[class.loquesea]`: eso se barre
// entero. Los literales de cadena de los `.ts` —de donde salen los `[class]="clases()"`— se
// barren con una regla que puede dejar pasar algo: se miran solo los literales donde **al menos
// una** de las palabras ya es una clase válida, porque un literal sin ninguna no se distingue de
// una fecha o de un tipo MIME. O sea que un literal de una sola palabra mal escrita no lo atrapa
// nadie; uno de varias, sí. Lo que arma una clase concatenando trozos (`bg-${color}`) queda
// fuera por definición, y eso no es una limitación de esta herramienta sino de la idea: Tailwind
// tampoco la generaría.
import postcss from "postcss";
import tailwind from "@tailwindcss/postcss";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const ENTRADA_TAILWIND = join(RAIZ, "apps/web/src/tailwind.css");
const FUENTE_WEB = join(RAIZ, "apps/web/src");

/** Clases que no son de Tailwind y no tienen por qué serlo. Cada una con quién la pone. */
const AJENAS = [
  /^cdk-/, // Angular CDK: overlay, trampa de foco, arrastre.
  /^ng-/, // Angular: ng-star-inserted, ng-untouched y compañía.
];

const BARRA = String.fromCharCode(92);
const escapar = (clase) => clase.replace(/[^a-zA-Z0-9_-]/g, (caracter) => BARRA + caracter);

/**
 * Las hojas de estilo que el sitio carga y que Tailwind **no** genera: el kit de marca. `.chaflan`
 * vive ahí —en `tokens.css`, fuera de toda capa y a propósito, como explica `tailwind.css`— y sin
 * mirarlas, el barrido acusaría de inexistentes las seis pantallas que la usan.
 *
 * Se leen del kit y no de la copia de `apps/web/src/assets`, que la escribe `copiar-marca` en cada
 * build: una comprobación que depende de un paso previo del build responde distinto según cuándo
 * se corra.
 */
const HOJAS_PROPIAS = [
  join(RAIZ, "packages/marca/tokens.css"),
  join(RAIZ, "packages/marca/fuentes.css"),
  join(RAIZ, "apps/web/src/styles.scss"),
];

/** Los nombres de clase que aparecen como selector en un CSS, desescapados. */
function clasesDeCss(css) {
  const SELECTOR = new RegExp(`\\.((?:${BARRA}${BARRA}.|[a-zA-Z0-9_-])+)`, "g");
  const ESCAPADO = new RegExp(`${BARRA}${BARRA}(.)`, "g");
  const nombres = new Set();
  for (const encontrado of css.matchAll(SELECTOR)) {
    nombres.add(encontrado[1].replace(ESCAPADO, "$1"));
  }
  return nombres;
}

/**
 * Las clases que Tailwind genera de verdad, sacadas de los selectores del CSS y no buscando cada
 * una dentro del texto.
 *
 * **La diferencia no es de estilo: `css.includes(".m")` acierta dentro de `.mb-4`.** Con la
 * comprobación por subcadena, cualquier palabra corta respondía "existe" —`m`, `p`, `a`, `ts`—,
 * que es justo lo que hace un guardián inútil: dice que sí a lo que no miró. Se encontró al
 * construir el barrido, porque las palabras sueltas de los mensajes en español empezaron a
 * contar como clases válidas.
 *
 * Una sola compilación para todas: cada llamada cuesta más de un segundo, y hacerla por clase
 * convertiría el barrido en algo que nadie corre.
 */
async function cualesExisten(clases) {
  const candidatas = clases.filter((clase) => !clase.includes('"') && !clase.includes(BARRA));
  const entrada = `${readFileSync(ENTRADA_TAILWIND, "utf8")}
@source inline("${candidatas.join(" ")}");
`;
  const { css } = await postcss([tailwind()]).process(entrada, { from: ENTRADA_TAILWIND });

  const existentes = clasesDeCss(css);
  for (const hoja of HOJAS_PROPIAS) {
    if (existsSync(hoja)) {
      for (const clase of clasesDeCss(readFileSync(hoja, "utf8"))) existentes.add(clase);
    }
  }
  return new Set(candidatas.filter((clase) => existentes.has(clase)));
}

/** Los `.html` y `.ts` del frontend, sin las pruebas. */
function archivosDelFrontend(directorio = FUENTE_WEB) {
  return readdirSync(directorio, { withFileTypes: true }).flatMap((entrada) => {
    const ruta = join(directorio, entrada.name);
    if (entrada.isDirectory()) {
      return entrada.name === "node_modules" || entrada.name === "assets"
        ? []
        : archivosDelFrontend(ruta);
    }
    return /\.(html|ts)$/.test(entrada.name) && !entrada.name.endsWith(".spec.ts") ? [ruta] : [];
  });
}

const palabras = (texto) => texto.split(/\s+/).filter(Boolean);

/**
 * El archivo sin sus comentarios.
 *
 * Este proyecto los escribe largos y citando código: el javadoc de `ts-galeria.ts` dice *"no una
 * base más un `[class.x]`"*, y sin quitarlo el barrido acusaba a `x` de clase inexistente. Un
 * guardián que señala la documentación en vez del código enseña a ignorarlo.
 *
 * De los de línea se quitan solo los que ocupan la línea entera, que es lo que deja intacto un
 * `https://` en mitad de un literal — el error clásico de quitar comentarios con una expresión
 * regular.
 */
function sinComentarios(texto) {
  return texto
    .replace(/<!--[\s\S]*?-->/g, "")
    .replace(/^[ \t]*\/\/.*$/gm, "")
    .replace(/\/\*[\s\S]*?\*\//g, "");
}

/**
 * Los candidatos del frontend, cada uno con dónde aparece y de qué forma.
 *
 * Se devuelven los literales aparte porque su filtro necesita saber antes qué clases existen, y
 * eso solo se sabe después de compilar: primero se resuelve lo seguro, y con esa respuesta se
 * decide qué literal parece una lista de clases y cuál no.
 */
function barrer() {
  const seguros = new Map();
  const literales = [];
  const anotar = (clase, archivo) => {
    const donde = seguros.get(clase) ?? new Set();
    donde.add(relative(RAIZ, archivo).replaceAll("\\", "/"));
    seguros.set(clase, donde);
  };

  for (const archivo of archivosDelFrontend()) {
    const texto = sinComentarios(readFileSync(archivo, "utf8"));
    for (const comilla of ['"', "'"]) {
      const atributo = new RegExp(`\\bclass\\s*=\\s*${comilla}([^${comilla}]*)${comilla}`, "g");
      for (const encontrado of texto.matchAll(atributo)) {
        // Una interpolación no es una clase: `class="{{ x }}"` lo decide el componente.
        for (const palabra of palabras(encontrado[1])) {
          if (!palabra.includes("{{")) anotar(palabra, archivo);
        }
      }
    }
    for (const encontrado of texto.matchAll(/\[class\.([^\]]+)\]/g)) {
      anotar(encontrado[1], archivo);
    }
    if (archivo.endsWith(".ts")) {
      for (const encontrado of texto.matchAll(/(['"`])([^'"`\n]{2,200})\1/g)) {
        // Solo las palabras con forma de clase de Tailwind: minúsculas, dígitos y los signos que
        // usan las variantes y los valores arbitrarios. Las demás se tiran, no descartan el
        // literal — un `${MINIATURA_BASE} border border-ts-borde` es mitad interpolación y mitad
        // lista de clases perfectamente comprobable, y descartarlo entero dejaba fuera justo la
        // forma en que este proyecto arma las clases dinámicas. Lo que sí queda fuera del todo
        // son los 1.100 municipios de `geografia-co.datos.ts` —"Agua de Dios", "San Andrés"—,
        // los tipos MIME y las fechas: sin una sola palabra con esta forma, no queda nada que
        // mirar.
        const partes = palabras(encontrado[2]).filter((parte) =>
          /^[a-z0-9:/[\]().,%_@!-]+$/.test(parte),
        );
        if (partes.length === 0) continue;
        literales.push({ partes, archivo: relative(RAIZ, archivo).replaceAll("\\", "/") });
      }
    }
  }
  return { seguros, literales };
}

const preguntadas = process.argv.slice(2);

if (preguntadas.length > 0) {
  const existen = await cualesExisten(preguntadas);
  const faltan = preguntadas.filter((clase) => !existen.has(clase));
  console.log(`comprobadas ${preguntadas.length}`);
  if (faltan.length > 0) {
    console.log(`NO EXISTEN (no hacen nada): ${faltan.join(" ")}`);
    process.exitCode = 1;
  } else {
    console.log("todas existen");
  }
} else {
  const { seguros, literales } = barrer();
  const deLiterales = [...new Set(literales.flatMap((literal) => literal.partes))];
  const existen = await cualesExisten([...new Set([...seguros.keys(), ...deLiterales])]);

  const faltan = new Map();
  const apuntar = (clase, donde) => {
    const sitios = faltan.get(clase) ?? new Set();
    for (const sitio of donde) sitios.add(sitio);
    faltan.set(clase, sitios);
  };

  for (const [clase, donde] of seguros) {
    if (existen.has(clase)) continue;
    if (AJENAS.some((patron) => patron.test(clase))) continue;
    apuntar(clase, donde);
  }

  // Un literal cuenta como lista de clases solo si **la mayoría** de sus palabras ya son clases,
  // y al menos dos. Sin esa prueba, un guardián que grita por nada se desactiva — y entonces
  // tampoco vigila lo que sí importa.
  //
  // "Al menos una" no alcanzaba, y el contraejemplo lo dio el propio kit: `tokens.css` define
  // `.precio`, `.sku` y `.cantidad`, así que el mensaje de error "no se pudo actualizar la
  // cantidad" tenía una palabra válida de seis y entraba entero al informe, acusando de clase
  // inexistente a "pudo".
  //
  // Lo que se pierde a cambio, dicho: un literal de dos clases con una mal escrita queda en
  // empate y se salta. Se prefiere ese hueco a un informe que nadie lee.
  for (const { partes, archivo } of literales) {
    const validas = partes.filter((parte) => existen.has(parte)).length;
    if (validas < 2 || validas * 2 < partes.length) continue;
    for (const parte of partes) {
      if (existen.has(parte) || AJENAS.some((patron) => patron.test(parte))) continue;
      apuntar(parte, [archivo]);
    }
  }

  const cuantas = seguros.size + deLiterales.length;
  if (faltan.size > 0) {
    console.error("Hay clases que no existen. No fallan: no hacen nada.\n");
    for (const [clase, donde] of [...faltan].sort()) {
      console.error(`  ${clase}`);
      for (const sitio of [...donde].sort()) console.error(`      ${sitio}`);
    }
    console.error(
      "\nUna clase que Tailwind no genera se queda en el HTML sin ningún estilo detrás, y" +
        "\nnada falla: ni el lint, ni las pruebas, ni el build. Las escalas por omisión están" +
        "\nborradas a propósito (ADR-0020), así que `rounded-lg` o `bg-red-500` están en este" +
        "\ncaso. Si el valor hace falta, se agrega al kit y se regenera; si es un error de" +
        "\nescritura, se corrige. La única escapatoria es `h-[var(--token)]`.",
    );
    process.exitCode = 1;
  } else {
    console.log(
      `Las ${cuantas} clases que usa el frontend existen (${seguros.size} de atributos y` +
        ` enlaces, ${deLiterales.length} de literales de TypeScript).`,
    );
  }
}
