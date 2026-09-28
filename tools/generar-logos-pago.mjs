#!/usr/bin/env node
// Convierte los logos de los medios de pago en un registro monocromo que hereda `currentColor`.
//
// **Por qué un generador y no once `<img>`.** Hasta el 28 de septiembre de 2026 estos logos se
// servían como archivos desde `src/assets/pagos/` y se pintaban con `<img>`, cada uno con sus
// colores de marca sobre una pastilla blanca. La pastilla no era un adorno: tres son negro puro o
// casi —Addi, BBVA y Bancolombia— y sobre `--color-marca`, que es grafito en los dos temas, no se
// veían. Pasaron a blanco para quitar esa pastilla y quedar como los logos de las redes, que sí se
// ven desnudos sobre la franja.
//
// Y ahí está el porqué del generador: **un `<img>` es opaco al CSS de la página**, así que no puede
// heredar `currentColor`. Hornear `#FFF` en el archivo lo habría atado a fondo oscuro para siempre
// —el día que uno haga falta en el checkout, que es superficie clara, habría que duplicar el
// archivo—. En línea heredan `--color-sobre-marca` igual que Facebook, Instagram y WhatsApp, y
// sirven sobre cualquier fondo.
//
// **Hay dos fuentes y no una**, y está en `ORIGENES`: Visa sale de `simple-icons` pinneada, igual
// que las redes en `generar-iconos-marca.mjs`; los demás no están en ninguna librería —o están en
// una versión que aquí no sirve— y viven en `apps/web/logos-pago/`, en el repo y sin servirse.
//
// **Y hay un logo que no es monocromo**, que es la excepción que `aColor` sostiene. Mastercard
// entró el 28 de septiembre de 2026 con el dibujo de los dos círculos, y ese aplanado a un color
// **se destruye**: los círculos se funden en dos manchas solapadas y el logotipo encima desaparece.
// Se miró en el navegador antes de escribir esto. No es un capricho de marca: es que el dibujo
// *significa* con el color, y un logo que no se reconoce no cumple lo único que hace en el pie, que
// es decir con qué se puede pagar.
//
// Lo que este tool aporta sobre copiar los paths a mano son los tres guardianes, y los tres
// nacieron de un fallo real de esta misma tarde: **quitar los rellenos y fallar si queda un color**
// —PSE escondía un `stroke` además del `fill`—, **tirar la placa de fondo declarada** —PSE trae el
// blanco horneado y aplanado se comía el logo— y **redondear tokenizando** —el atajo con una
// expresión regular pegaba números en la notación compacta y dibujaba fragmentos—.
//
// Correr `npm run logos-pago` al reemplazar un archivo de `apps/web/logos-pago/`, o al subir la
// versión de `simple-icons`.
import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const DESTINO = join(RAIZ, "apps/web/src/app/shared/ui/icono/logos-pago.generado.ts");

/**
 * De dónde sale cada archivo.
 *
 * <p>`propio` es `apps/web/logos-pago/`: los medios colombianos, que no están en ninguna librería y
 * por eso viven en el repo, más Mastercard y American Express desde el 28 de septiembre de 2026.
 * `simple-icons` es hoy solo Visa, que se lee pinneada de `node_modules` igual que hace
 * `generar-iconos-marca.mjs` con las redes — la librería es `devDependency`, se usa al generar y no
 * entra en el paquete que se sirve.
 *
 * <p><b>Las otras dos franquicias salieron de `simple-icons` el mismo día que entraron.</b> Allí
 * son glifos de 24×24 de una sola silueta, y eso vale para una red social pero no para estas dos:
 * Mastercard se queda sin los dos círculos, que es donde está toda su identidad, y American Express
 * se queda sin la silueta que envuelve cada palabra. Los archivos que las reemplazan traen el
 * dibujo completo.
 *
 * <p>Copiarlas al repo habría sido más corto y peor: una copia vendorizada de un logo que su
 * mantenedor actualiza cuando la marca se rediseña es una copia que envejece sin que nadie se
 * entere.
 */
const ORIGENES = {
  propio: join(RAIZ, "apps/web/logos-pago"),
  "simple-icons": join(RAIZ, "node_modules/simple-icons/icons"),
};

/**
 * Las marcas de pago y el símbolo de la contraentrega, en el orden en que los enseña el pie.
 *
 * **La contraentrega no es una marca y aun así vive aquí**, porque lo que agrupa esta lista no es
 * "logos de terceros" sino "dibujos rellenos con su propia caja". Su símbolo es un icono de 512×512
 * sin un solo relleno declarado, así que ya hereda `currentColor`; lo que no puede es pasar por
 * `ts-icono`, que dibuja con contorno y `fill="none"` y lo dejaría invisible —el mismo motivo por
 * el que `ts-icono-marca` existe aparte—.
 *
 * <p><b>Las tres franquicias de tarjeta van primero y son exactamente las que Wompi acepta</b>:
 * Visa, Mastercard y American Express, nacionales e internacionales, según su documentación y su
 * centro de soporte (consultado el 28 de septiembre de 2026). <b>Diners Club no está en ninguna de
 * las dos</b>, y por eso no está aquí. Hasta hoy el pie enseñaba un icono genérico de tarjeta con el
 * texto "Tarjeta de crédito y débito", que no decía cuáles.
 *
 * <p>Si se cambia de pasarela, o si Wompi suma una franquicia, esta lista se queda vieja sin que
 * nada falle: publicar una franquicia que el cobro rechaza es información engañosa, igual que
 * anunciar un medio que no existe.
 */
const LOGOS = [
  {
    constante: "logoVisa",
    archivo: "visa.svg",
    titulo: "Visa",
    origen: "simple-icons",
    vista: "0 4 24 16",
  },
  {
    constante: "logoMastercard",
    archivo: "mastercard.svg",
    titulo: "Mastercard",
    origen: "propio",
    aColor: true,
  },
  {
    constante: "logoAmericanExpress",
    archivo: "americanexpress.svg",
    titulo: "American Express",
    origen: "propio",
  },
  { constante: "logoPse", archivo: "pse.svg", titulo: "PSE", origen: "propio" },
  { constante: "logoNequi", archivo: "nequi.svg", titulo: "Nequi", origen: "propio" },
  {
    constante: "logoBancolombia",
    archivo: "bancolombia.svg",
    titulo: "Bancolombia",
    origen: "propio",
  },
  { constante: "logoDaviplata", archivo: "daviplata.svg", titulo: "Daviplata", origen: "propio" },
  { constante: "logoBbva", archivo: "bbva.svg", titulo: "BBVA", origen: "propio" },
  {
    constante: "logoSistecredito",
    archivo: "sistecredito.svg",
    titulo: "Sistecrédito",
    origen: "propio",
  },
  {
    constante: "logoContraentrega",
    archivo: "contraentrega.svg",
    titulo: "Contraentrega",
    origen: "propio",
  },
  { constante: "logoAddi", archivo: "addi.svg", titulo: "Addi", origen: "propio" },
];

/**
 * Cuándo se declara `vista` en `LOGOS` y por qué no es lo normal.
 *
 * <p>Lo normal es que el `viewBox` salga del archivo: es el dueño del logo quien decide cuánto aire
 * lleva alrededor. La anulación existe para un caso concreto y hoy solo la usa Visa.
 *
 * <p><b>`simple-icons` dibuja todos sus iconos en un cuadrado de 24×24</b>, porque su caso de uso
 * es un glifo cuadrado. El logotipo de Visa es apaisado, así que dentro de ese cuadrado ocupa los
 * 24 de ancho y <b>7,8 de alto</b> — el resto es lienzo. Metido en la caja compartida del pie, que
 * contiene el dibujo, eso lo dejaba a 24×7,8 px cuando sus vecinos se dibujan a 19-24 de alto: el
 * logo más pequeño de la fila con diferencia, y no por decisión de nadie.
 *
 * <p><b>`0 4 24 16` se queda con la mitad del aire, no con nada.</b> Ceñirlo del todo —`0 8 24 8`—
 * lo manda al otro extremo: pasa a 64×20,8 px y se convierte en el logotipo más grande de la
 * columna, por delante de Sistecrédito y de BBVA. La mitad lo sube a 36×11,6, que es un aumento
 * y no un vuelco. Los tres se miraron en el navegador, uno al lado del otro y dentro de la fila.
 *
 * <p>No se resuelve moviendo el archivo a `apps/web/logos-pago/` como se hizo con Mastercard y
 * American Express: aquellos cambiaban de <b>dibujo</b> —les faltaban los círculos y la silueta—,
 * y a este no le falta nada. Vendorizarlo por un encuadre sería perder la actualización de la
 * librería a cambio de cuatro números que se pueden escribir aquí.
 */

/**
 * Los `path` que hay que tirar antes de aplanar, por archivo y por el relleno con que vienen.
 *
 * **Hoy solo PSE, y conviene saber por qué.** `pse.svg` es un trazado de bitmap, y el trazador dejó
 * el fondo horneado dentro: su primer `path` es un `#fdfefd` que cubre el lienzo entero de 519×206.
 * Con los colores originales no se notaba —debajo del logo hay blanco, que es donde PSE lo diseñó—
 * pero al aplanar todo a un color esa placa se come el logo y queda un rectángulo sólido. Se vio en
 * el navegador antes de escribir esto; ninguna prueba mira un color.
 *
 * Va declarado y no adivinado a propósito. La heurística tentadora —"tirar todo `path` que cubra el
 * lienzo"— es justo la que un día tiraría el fondo de un logo que sí lo necesita, en silencio y en
 * producción. Aquí cada excepción se escribe con su nombre, y `sinColores` comprueba después.
 */
const PLACAS_DE_FONDO = {
  "pse.svg": ["#fdfefd"],
};

/**
 * Los atributos de presentación que un `path` puede traer, y el campo con que salen.
 *
 * **La lista es cerrada a propósito y el que no esté aquí hace fallar la corrida.** Los siete
 * archivos usan hoy exactamente estos cinco, comprobado uno por uno. Si mañana un logo nuevo trae
 * un `opacity`, un `mask` o un `filter`, el generador para y alguien lo mira; ignorarlo en silencio
 * publicaría un logo distinto del que hay en el archivo, que es justo lo que nadie revisaría.
 */
const ATRIBUTOS_DE_TRAZO = {
  "fill-rule": "reglaDeRelleno",
  "clip-rule": "reglaDeRecorte",
  stroke: "trazo",
  "stroke-width": "grosorDeTrazo",
  "stroke-linejoin": "unionDeTrazo",
};

/** Un comando de path, o un número, o un separador. Lo que no encaje aquí hace fallar la corrida. */
const TOKEN = /([A-Za-z])|(-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)|([,\s]+)/g;

/**
 * A un decimal, y en una sola línea.
 *
 * <p>Los trazados de bitmap vienen con dos decimales —la mitad del peso de este archivo— y además
 * parten el `d` en varias líneas, que el generado no puede escribir entre comillas simples sin
 * quedar en una cadena sin cerrar. Lo segundo lo levantó el compilador en la primera corrida.
 *
 * <h2>Por qué esto tokeniza en vez de buscar y reemplazar</h2>
 *
 * <p>Fue un `replace(/-?\d+\.\d+/g, ...)` durante una tarde, y <b>corrompía los logos</b>. En la
 * notación compacta de SVG el punto hace de separador: `2.04.756` son <i>dos</i> números, 2.04 y
 * .756. Redondeando, `2.04` se queda en `2`, pierde el punto, y el `.756` se le pega: donde había
 * dos números queda `2.756`. Cuatro veces en Visa, una en Mastercard y dieciocho en American
 * Express — se vieron en el navegador como fragmentos sueltos, un triangulito y una coma.
 *
 * <p>Los siete archivos colombianos se salvaron de milagro: son exportaciones con un espacio entre
 * cada número, así que ahí el atajo funcionaba. Es la clase de error que no falla en ningún sitio,
 * simplemente dibuja otra cosa.
 *
 * <p>Tokenizado, cada número se redondea entero y se reemite con un espacio delante, así que no hay
 * forma de que dos se peguen. Y si el tokenizador encuentra un carácter que no entiende, para: un
 * path a medio leer dibujaría un logo distinto del que hay en el archivo, en silencio.
 *
 * <h2>Los arcos se dejan en paz</h2>
 *
 * <p>Visa usa `a`, y en un arco los parámetros cuarto y quinto son banderas de un dígito que la
 * notación compacta escribe pegadas —`a1 1 0 011 1` son `0`, `1` y `1`—. Un tokenizador de números
 * genérico lee ahí un `011` y el arco sale volteado. Distinguirlas pide contar parámetros por
 * comando, y no vale la pena por el kilobyte que ahorraría en tres archivos que ya son pequeños: un
 * path con arcos se normaliza en espacios y se queda con sus decimales.
 */
function redondear(archivo, d) {
  const normalizado = d.replace(/\s+/g, " ").trim();
  if (/[aA]/.test(normalizado)) {
    return normalizado;
  }

  const partes = [];
  let leidoHasta = 0;
  TOKEN.lastIndex = 0;
  for (let m; (m = TOKEN.exec(normalizado)) !== null; ) {
    if (m.index !== leidoHasta) {
      throw new Error(
        `${archivo}: no se entiende "${normalizado.slice(leidoHasta, m.index + 8)}" dentro de un path. ` +
          `Antes que redondear a medias, mejor parar.`,
      );
    }
    leidoHasta = m.index + m[0].length;
    const [, comando, numero] = m;
    if (comando) {
      partes.push(comando);
    } else if (numero) {
      partes.push(String(Math.round(Number(numero) * 10) / 10));
    }
  }
  if (leidoHasta !== normalizado.length) {
    throw new Error(
      `${archivo}: sobró "${normalizado.slice(leidoHasta)}" al final de un path. El archivo viene raro.`,
    );
  }

  return partes.join(" ");
}

/**
 * El guardián del color. Un hex que sobreviva al aplanado es un logo que se va a ver de su color
 * sobre la franja de marca —o, si era una placa, una mancha que se come al de al lado— y es
 * exactamente la clase de fallo que ninguna prueba ve: en jsdom un color es una cadena más.
 *
 * <p><b>No corre sobre los logos con `aColor`</b>, que es lo único que los distingue de los demás:
 * ahí el color no es un descuido, es el dibujo. La excepción se declara por logo y no se deduce del
 * archivo, igual que `PLACAS_DE_FONDO`: un logo que empieza a colarse a color sin que nadie lo
 * decida es justo lo que este guardián existe para impedir.
 */
function sinColor(archivo, atributo, valor) {
  if (/#[0-9a-fA-F]{3,8}|rgb\(|hsl\(|url\(#/.test(valor)) {
    throw new Error(
      `${archivo}: quedó un color en ${atributo}="${valor}" después de aplanar. ` +
        `Míralo a mano: si es una placa de fondo nueva, va en PLACAS_DE_FONDO.`,
    );
  }
}

function extraer({ archivo, origen, aColor = false, vista: vistaDeclarada = null }) {
  const svg = readFileSync(join(ORIGENES[origen], archivo), "utf8");
  const vistaDelArchivo = svg.match(/viewBox="([^"]+)"/)?.[1];
  if (!vistaDelArchivo) {
    throw new Error(`${archivo}: sin viewBox. Sin él no se puede dibujar a escala.`);
  }
  const vista = vistaDeclarada ?? vistaDelArchivo;

  // Los dos trazados de bitmap —Addi y BBVA— meten sus paths en un `<g transform>` que los coloca y
  // los voltea. La transformación sube al logo entero: un solo grupo por archivo, comprobado.
  const grupos = [...svg.matchAll(/<g\b([^>]*)>/g)];
  if (grupos.length > 1) {
    throw new Error(
      `${archivo}: ${grupos.length} grupos, y el componente dibuja uno. Míralo antes de seguir.`,
    );
  }
  const transformacion = grupos[0]?.[1].match(/transform="([^"]+)"/)?.[1] ?? null;

  let cuerpo = svg.replace(/<!--[\s\S]*?-->/g, "");

  for (const relleno of PLACAS_DE_FONDO[archivo] ?? []) {
    const antes = cuerpo;
    cuerpo = cuerpo.replace(new RegExp(`<path[^>]*fill="${relleno}"[^>]*/>`, "gi"), "");
    if (cuerpo === antes) {
      throw new Error(
        `${archivo}: se esperaba una placa de fondo ${relleno} y no está. ` +
          `¿Cambió el archivo? Míralo antes de quitar la excepción.`,
      );
    }
  }

  const trazos = [...cuerpo.matchAll(/<path\b([^>]*?)\/?>/g)].map((etiqueta) => {
    const atributos = [...etiqueta[1].matchAll(/([a-zA-Z-]+)="([^"]*)"/g)];
    const d = atributos.find(([, nombre]) => nombre === "d")?.[2];
    if (!d) {
      throw new Error(`${archivo}: un path sin "d". El archivo viene mal.`);
    }

    const trazo = {
      d: redondear(archivo, d),
      // **El color propio solo existe para un logo a color, y ahí es obligatorio en cada path.**
      // Dejar uno sin relleno lo haría heredar el `currentColor` del `<svg>` del componente, o sea
      // blanco sobre la franja del pie: en Mastercard eso borraría el contorno del logotipo, que
      // viene sin `fill` declarado porque el negro por omisión de SVG ya era el que tocaba.
      relleno: aColor ? "#000000" : null,
      reglaDeRelleno: null,
      reglaDeRecorte: null,
      trazo: null,
      grosorDeTrazo: null,
      unionDeTrazo: null,
    };

    for (const [, nombre, valor] of atributos) {
      if (nombre === "d") {
        continue;
      }
      // En un logo monocromo el relleno se va entero: el `<svg>` del componente pone
      // `fill="currentColor"` y el `path` lo hereda, que es lo que le deja seguir al color del
      // texto en los dos temas. En uno a color se conserva tal cual viene del archivo.
      if (nombre === "fill") {
        if (aColor) {
          trazo.relleno = valor;
        }
        continue;
      }
      const campo = ATRIBUTOS_DE_TRAZO[nombre];
      if (!campo) {
        throw new Error(
          `${archivo}: atributo desconocido ${nombre}="${valor}" en un path. ` +
            `O entra en ATRIBUTOS_DE_TRAZO con su campo, o el archivo trae algo que no se dibuja igual.`,
        );
      }
      // **El trazo se reescribe y no se quita**, que es distinto, y lo levantó la primera corrida:
      // PSE lleva `stroke="#0e5f9e" stroke-width="0.25"` en cada path, un engrosamiento del
      // trazador de bitmap sin el cual el logo adelgaza. Quitándolo, el trazo caería al valor por
      // omisión —ninguno— y el dibujo cambiaría de peso; en `currentColor` sigue al texto igual que
      // el relleno.
      const limpio = nombre === "stroke" && !aColor ? "currentColor" : valor;
      if (!aColor) {
        sinColor(archivo, nombre, limpio);
      }
      trazo[campo] = limpio;
    }

    return trazo;
  });

  if (trazos.length === 0) {
    throw new Error(`${archivo}: quedó sin un solo path. Algo se llevó el dibujo entero.`);
  }

  return { vista, transformacion, trazos };
}

// Solo se barre la carpeta propia: `node_modules/simple-icons/icons` tiene miles y ahí lo normal es
// que sobren. Un archivo sin usar en la nuestra, en cambio, es un archivo que nadie revisa.
const sobrantes = readdirSync(ORIGENES.propio)
  .filter((f) => f.endsWith(".svg"))
  .filter((f) => !LOGOS.some((l) => l.origen === "propio" && l.archivo === f));
if (sobrantes.length > 0) {
  throw new Error(
    `Hay logos en ${ORIGENES.propio} que nadie usa: ${sobrantes.join(", ")}. ` +
      `O entran en LOGOS, o se borran: un archivo que nadie dibuja es un archivo que nadie revisa.`,
  );
}

const logos = LOGOS.map((logo) => ({ ...logo, ...extraer(logo) }));

/** `null` o la cadena entre comillas simples, que es como se escribe el campo en el generado. */
const valor = (v) => (v === null ? "null" : `'${v.replace(/'/g, "\\'")}'`);

const contenido = `// GENERADO por tools/generar-logos-pago.mjs — no editar a mano.
//
// Los logos de los medios de pago. Diez son monocromos —sin un solo relleno propio, para que
// hereden el \`currentColor\` de quien los dibuja— y Mastercard no, porque su dibujo significa con
// el color: aplanado se queda en dos manchas solapadas. Los pinta \`ts-logo-pago\`.
//
// **No están en caja de 24 como los de \`marcas.generado.ts\`.** Van de 50×41 a 1000×305, cada uno
// con su relación de aspecto, y por eso cada uno trae su \`vista\`: escalarlos a una caja común
// deformaría el logo, y un logo deformado es un problema de marca y no de CSS.
//
// **Y por eso mismo no son un \`path\` suelto**: varios traen más de uno, y los dos trazados de
// bitmap —Addi y BBVA— vienen dentro de un \`<g transform>\` que los coloca y los voltea.
//
// Van como datos y no como una cadena de marcado a propósito: pintar marcado guardado costaría un
// \`innerHTML\` con el sanitizador de Angular puenteado, y eso es una puerta que no se abre para
// ahorrarse un \`@for\`. Aquí cada atributo es un campo, y el generador falla ante uno que no
// conozca.
//
// Las marcas registradas siguen siendo propiedad de sus titulares; aquí se usan para decir con qué
// se le puede pagar a la tienda, no como respaldo de nadie. Cada una publica versión monocroma: es
// un uso previsto, no una licencia que nos tomamos.
//
// Para actualizarlos, reemplaza el archivo en \`apps/web/logos-pago/\` y corre \`npm run logos-pago\`.

/**
 * Un trazo del dibujo. Todo lo que no sea \`d\` va en \`null\` cuando el archivo no lo trae, y el
 * componente lo ata con \`[attr.*]\`, que quita el atributo cuando el valor es nulo.
 *
 * \`relleno\` es \`null\` en los diez logos monocromos, y ahí pinta el \`fill="currentColor"\` del
 * \`<svg>\`. Solo Mastercard lo trae, porque su dibujo significa con el color.
 *
 * \`trazo\` es \`'currentColor'\` o nada: en un logo monocromo el color propio no sobrevive al
 * generador.
 */
export interface TrazoDeLogo {
  readonly d: string;
  readonly relleno: string | null;
  readonly reglaDeRelleno: string | null;
  readonly reglaDeRecorte: string | null;
  readonly trazo: string | null;
  readonly grosorDeTrazo: string | null;
  readonly unionDeTrazo: string | null;
}

/** El logo de un medio de pago: su caja, su dibujo sin color, y el nombre de la marca. */
export interface LogoPago {
  readonly titulo: string;
  readonly vista: string;
  readonly transformacion: string | null;
  readonly trazos: readonly TrazoDeLogo[];
}

${logos
  .map(
    ({ constante, titulo, vista, transformacion, trazos }) =>
      `export const ${constante}: LogoPago = {\n` +
      `  titulo: ${valor(titulo)},\n` +
      `  vista: ${valor(vista)},\n` +
      `  transformacion: ${valor(transformacion)},\n` +
      `  trazos: [\n` +
      trazos
        .map(
          (t) =>
            `    {\n` +
            // El `d` va en una línea por larga que sea: partido, Prettier lo vuelve a juntar y el
            // generado queda sucio nada más escribirlo.
            `      d: ${valor(t.d)},\n` +
            `      relleno: ${valor(t.relleno)},\n` +
            `      reglaDeRelleno: ${valor(t.reglaDeRelleno)},\n` +
            `      reglaDeRecorte: ${valor(t.reglaDeRecorte)},\n` +
            `      trazo: ${valor(t.trazo)},\n` +
            `      grosorDeTrazo: ${valor(t.grosorDeTrazo)},\n` +
            `      unionDeTrazo: ${valor(t.unionDeTrazo)},\n` +
            `    },`,
        )
        .join("\n") +
      `\n  ],\n};`,
  )
  .join("\n\n")}
`;

writeFileSync(DESTINO, contenido);

const kb = (n) => `${(n / 1024).toFixed(1)} kB`;
const peso = (l) => l.trazos.reduce((s, t) => s + t.d.length, 0);
console.log(`${logos.length} logos → ${DESTINO}`);
for (const logo of logos) {
  console.log(
    `  ${logo.archivo.padEnd(18)} ${String(logo.trazos.length).padStart(2)} trazos ${kb(peso(logo)).padStart(8)}`,
  );
}
console.log(
  `  ${"TOTAL".padEnd(18)} ${" ".repeat(9)}${kb(logos.reduce((s, l) => s + peso(l), 0)).padStart(8)}`,
);
