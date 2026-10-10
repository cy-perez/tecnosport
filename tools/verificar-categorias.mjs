#!/usr/bin/env node
// ¿Nombra cada fixture una categoría que el catálogo de verdad tiene, y la llama como se llama?
//
// Nació el 10 de octubre de 2026, de una barrida que duró tres rondas. Las pruebas montaban
// categorías inventadas —`bolsos`, que `V63` borró hace dos semanas; `cables`, que `V62` borró
// porque el negocio dejó de venderlos; `conjuntos`, que no existía en ninguna base— y nada lo
// miraba, porque un fixture con un slug que no existe **no falla**: el doble de prueba acepta
// cualquier cadena. Cuarenta y tres usos de `bolsos` en veintiséis archivos.
//
// Y la barrida a mano se equivocó dos veces, que es el argumento para que esto sea un guardián y
// no un grep bien hecho:
//
//   1. Al renombrar el slug con sufijo, el nombre se buscó por el slug completo
//      (`bolsos-dama-morrales-t9`), no por su base, así que veinte filas quedaron con el nombre
//      viejo. Pasaban: nadie afirma el nombre de esas filas.
//   2. Tres aserciones sobre el nombre que la pantalla pinta —dos del frontend, una de
//      `CategoriaControladorTest`— quedaron atrás y **rompieron el build**. Esas sí se vieron;
//      las veinte de arriba, no. Lo que no se ve es lo que necesita guardián.
//
// El árbol **no se escribe aquí**. Se deriva de las migraciones, que son la fuente de verdad, y se
// cruza contra los slugs que `ArbolDeCategoriasTest` afirma sobre un Postgres real. Copiarlo a mano
// habría creado la tercera copia del árbol, que es exactamente el error que ese test documenta.
//
// Y si aparece una sentencia sobre `categoria` con una forma que este archivo no sabe leer,
// **falla** en vez de ignorarla. Es la misma razón por la que `ContenidoDeclarado` usa un `switch`
// sin `default`: un lector que se salta en silencio lo que no entiende es un guardián que da
// confianza falsa.
import { readFileSync, readdirSync, existsSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const MIGRACIONES = "apps/api/infrastructure/src/main/resources/db/migration";
const ARBOL_AFIRMADO =
  "apps/api/infrastructure/src/test/java/co/tecnosport/api/infrastructure/catalogo/ArbolDeCategoriasTest.java";

/**
 * Slugs que una prueba se inventa a propósito, con el motivo. Aquí el slug **no finge ser
 * catálogo**: dice de qué es la prueba, y cambiarlo por uno real le quitaría el único rótulo que
 * tiene. Agregar uno es una decisión, no un descuido: por eso hay que escribirlo.
 */
const SINTETICOS = new Map([
  ["ropa-mapa", "RepositorioMapaDelSitioJpaTest: una fila por caso del mapa del sitio"],
  ["ropa-fecha", "RepositorioMapaDelSitioJpaTest: la fecha de modificación"],
  ["ropa-orden", "RepositorioMapaDelSitioJpaTest: el orden"],
  ["ropa-limite", "RepositorioMapaDelSitioJpaTest: el límite"],
  ["raiz-tc-arbol", "RepositorioCategoriasJpaTest: la raíz de un árbol de juguete"],
  ["ropa-dama-tc-arbol", "RepositorioCategoriasJpaTest: la rama de ese árbol"],
  ["ropa-dama-tc-faldas", "RepositorioCategoriasJpaTest: su hoja"],
  ["ropa-jeans-tc", "RepositorioCategoriasJpaTest: consultas por padre"],
  ["ropa-faldas-tc2", "RepositorioCategoriasJpaTest: consultas por padre"],
  ["bolsos-tc2", "RepositorioCategoriasJpaTest: buscar por id"],
  ["cursor-np", "RepositorioProductosJpaTest: el cursor de la paginación"],
  ["x", "CategoriaTest: el nombre vacío, que es lo que se valida"],
  ["con-publicado-cat", "RepositorioCategoriasJpaTest: una categoría con producto publicado"],
  ["solo-borrador-cat", "RepositorioCategoriasJpaTest: solo con borradores"],
  ["sin-nada-cat", "RepositorioCategoriasJpaTest: sin productos"],
  ["aaa-primera-tc1", "RepositorioCategoriasJpaTest: el orden alfabético, primera"],
  ["zzz-ultima-tc1", "RepositorioCategoriasJpaTest: el orden alfabético, última"],
  ["camisas-tc-marcas", "RepositorioMarcasJpaTest: las líneas que surte una marca"],
  ["tenis-tc-marcas", "RepositorioMarcasJpaTest: las líneas que surte una marca"],
  ["morrales-tc-marcas", "RepositorioMarcasJpaTest: las líneas que surte una marca"],
  ["busos-tc-marcas", "RepositorioMarcasJpaTest: las líneas que surte una marca"],
]);

/**
 * El nombre puede llevar una etiqueta de prueba detrás —"Celulares TC", "Dama TC"— y sigue siendo
 * el de su categoría: quien la escribió marcó la fila a propósito para reconocerla en una base
 * compartida. Lo que no se tolera es un nombre **distinto**, que es el descuido real: veinte filas
 * decían "Bolsos" con el slug de Morrales.
 */
const nombreCuadra = (nombre, esperado) => nombre === esperado || nombre.startsWith(`${esperado} `);

/**
 * Fixtures que llevan un nombre que el árbol ya no usa, **porque la prueba ejerce el renombrado**.
 * `V63` cambió "Consolas" por "Consolas de videojuegos", y una prueba del renombrado tiene que
 * arrancar del nombre viejo: "corregirla" dejaría una prueba que renombra algo al nombre que ya
 * tenía.
 */
const ANTES_DE_UN_RENOMBRADO = new Set(["Consolas|consolas"]);

const SALTAR = new Set(["node_modules", "build", "dist", ".git", ".angular", ".gradle"]);

// --- Lectura del SQL -----------------------------------------------------------------------

/** Las sentencias del archivo, sin comentarios y sin el `;`. Respeta las comillas simples. */
export function sentencias(sql) {
  let limpio = "";
  let enTexto = false;
  for (let i = 0; i < sql.length; i++) {
    const c = sql[i];
    if (enTexto) {
      limpio += c;
      if (c === "'") enTexto = false;
      continue;
    }
    if (c === "'") {
      enTexto = true;
      limpio += c;
      continue;
    }
    if (c === "-" && sql[i + 1] === "-") {
      while (i < sql.length && sql[i] !== "\n") i++;
      limpio += "\n";
      continue;
    }
    limpio += c;
  }
  return partirEnNivelCero(limpio, ";").map(normalizar).filter(Boolean);
}

const normalizar = (texto) => texto.replace(/\s+/g, " ").trim();

/** Parte por el separador, ignorando lo que esté dentro de comillas o de paréntesis. */
function partirEnNivelCero(texto, separador) {
  const partes = [];
  let actual = "";
  let profundidad = 0;
  let enTexto = false;
  for (const c of texto) {
    if (enTexto) {
      actual += c;
      if (c === "'") enTexto = false;
      continue;
    }
    if (c === "'") enTexto = true;
    else if (c === "(") profundidad++;
    else if (c === ")") profundidad--;
    if (c === separador && profundidad === 0) {
      partes.push(actual);
      actual = "";
      continue;
    }
    actual += c;
  }
  partes.push(actual);
  return partes.map((p) => p.trim());
}

/** Las tuplas de un `values (...), (...)`, cada una ya partida en sus columnas. */
function tuplas(texto) {
  return partirEnNivelCero(texto, ",")
    .filter((t) => t.startsWith("("))
    .map((t) => partirEnNivelCero(t.slice(1, -1), ",").map((v) => v.trim()));
}

const literal = (valor) => (/^'(.*)'$/s.test(valor) ? valor.slice(1, -1) : null);
const columnas = (lista) => lista.split(",").map((c) => c.trim());

/**
 * El árbol que dejan las migraciones: slug → nombre, aplicadas en orden de versión. Devuelve
 * también las sentencias que no supo leer, que es lo que hace que esto no mienta.
 */
export function arbolDesdeMigraciones(directorio) {
  const nombrePorSlug = new Map();
  const desconocidas = [];

  const archivos = readdirSync(directorio)
    .filter((f) => /^V\d+__.*\.sql$/.test(f))
    .sort((a, b) => Number(a.match(/^V(\d+)/)[1]) - Number(b.match(/^V(\d+)/)[1]));

  for (const archivo of archivos) {
    for (const sentencia of sentencias(readFileSync(join(directorio, archivo), "utf8"))) {
      if (!/^(insert into|update|delete from)\s+categoria\b/i.test(sentencia)) continue;
      if (!aplicar(sentencia, nombrePorSlug)) {
        desconocidas.push(`${archivo}: ${sentencia.slice(0, 120)}`);
      }
    }
  }
  return { nombrePorSlug, desconocidas };
}

/** Aplica una sentencia al árbol. `false` si no reconoce su forma. */
function aplicar(sentencia, nombrePorSlug) {
  const sinConflicto = sentencia.replace(/\s+on conflict\b.*$/i, "");

  const insertaValores = sinConflicto.match(/^insert into categoria \(([^)]+)\) values (.+)$/i);
  if (insertaValores) {
    const cols = columnas(insertaValores[1]);
    for (const fila of tuplas(insertaValores[2])) {
      const dato = (col) => literal(fila[cols.indexOf(col)] ?? "");
      guardar(nombrePorSlug, dato("slug"), dato("nombre"));
    }
    return true;
  }

  const insertaSelect = sinConflicto.match(/^insert into categoria \(([^)]+)\) select (.+)$/i);
  if (insertaSelect) {
    return aplicarSelect(columnas(insertaSelect[1]), insertaSelect[2], nombrePorSlug);
  }

  // Un `update` que no toca el nombre no mueve este árbol: la línea, el padre y la escala de
  // tallas se verifican en otro sitio.
  const renombra = sinConflicto.match(
    /^update categoria set nombre = '([^']*)' where slug = '([^']+)'$/i,
  );
  if (renombra) {
    guardar(nombrePorSlug, renombra[2], renombra[1]);
    return true;
  }
  if (/^update categoria set /i.test(sinConflicto) && !/\bnombre\s*=/i.test(sinConflicto)) {
    return true;
  }

  const borra = sinConflicto.match(/^delete from categoria where slug in \((.+)\)$/i);
  if (borra) {
    // Un slug que no está puede ser de los que creaba el sembrador y nunca una migración.
    for (const valor of partirEnNivelCero(borra[1], ",")) nombrePorSlug.delete(literal(valor));
    return true;
  }
  return false;
}

/**
 * El `insert ... select`, que es como entran las hojas: la expresión de cada columna es un literal,
 * un `h.<col>` de la lista de `values`, o algo que a este árbol no le importa (`p.linea`, `now()`).
 */
function aplicarSelect(cols, resto, nombrePorSlug) {
  const deValores = resto.match(/^(.+?) from \(values (.+?)\) as (\w+) \(([^)]+)\)(.*)$/i);
  const deCategoria = resto.match(/^(.+?) from categoria \w+ where .+$/i);
  if (!deValores && !deCategoria) return false;

  const expresiones = partirEnNivelCero(deValores ? deValores[1] : deCategoria[1], ",");
  const alias = deValores ? deValores[3] : null;
  const colsAlias = deValores ? columnas(deValores[4]) : [];
  const filas = deValores ? tuplas(deValores[2]) : [[]];

  for (const fila of filas) {
    const valorDe = (col) => {
      const expresion = expresiones[cols.indexOf(col)];
      if (expresion === undefined) return null;
      const comoLiteral = literal(expresion);
      if (comoLiteral !== null) return comoLiteral;
      if (alias && expresion.startsWith(`${alias}.`)) {
        return literal(fila[colsAlias.indexOf(expresion.slice(alias.length + 1))] ?? "");
      }
      return null;
    };
    guardar(nombrePorSlug, valorDe("slug"), valorDe("nombre"));
  }
  return true;
}

function guardar(nombrePorSlug, slug, nombre) {
  if (slug && nombre) nombrePorSlug.set(slug, nombre);
}

// --- Lectura de los fixtures ---------------------------------------------------------------

/**
 * Las formas en que una prueba escribe una categoría. Cada una captura nombre y slug en ese orden;
 * la última, al revés. Si aparece una forma nueva que no esté aquí, sus fixtures no se revisan — y
 * por eso conviene que cada nueva se agregue, no que se invente otra manera de escribirla.
 */
const FORMAS = [
  // Categoria.crear("Blusas", new Slug("ropa-dama-blusas"), ...) y crearBajo(dama, "Blusas", ...)
  /Categoria\.crear(?:Bajo)?\((?:[^,()]*,\s*)?"([^"]*)"\s*,\s*new Slug\("([^"]+)"\)/g,
  // new Categoria(id, "Morrales", new Slug("bolsos-dama-morrales"), ...)
  /new Categoria\([^,]+,\s*"([^"]*)"\s*,\s*new Slug\("([^"]+)"\)/g,
  // new CategoriaJpaEntity(id, "Morrales", "bolsos-dama-morrales", "BOLSOS", ...)
  /new CategoriaJpaEntity\([^,]+,\s*"([^"]*)"\s*,\s*"([^"]+)"\s*,\s*"[A-Z_]+"/g,
  // el ayudante categoria("Morrales", "bolsos-dama-morrales", "BOLSOS")
  /\bcategoria\w*\(\s*"([^"]*)"\s*,\s*"([^"]+)"\s*,\s*"[A-Z_]+"/g,
  // Frontend: { nombre: 'Morrales', slug: 'bolsos-dama-morrales', linea: 'BOLSOS' }.
  //
  // El `linea:` del final **no es decoración**: en el frontend un producto se escribe igual que una
  // categoría —`{ nombre, slug }`— y sin ese ancla este guardián leía "Camiseta running Dry-Fit"
  // como una categoría inexistente y fallaba por nueve sitios donde no pasaba nada.
  /nombre:\s*'([^']*)',\s*(?:\n\s*)?slug:\s*'([^']+)',\s*(?:\n\s*)?linea:/g,
];
const FORMA_INVERTIDA = /slug:\s*'([^']+)',\s*(?:\n\s*)?nombre:\s*'([^']*)',\s*(?:\n\s*)?linea:/g;

const ES_PRUEBA = (archivo) => /Test\.java$|\.spec\.ts$|Apoyo\w*\.java$/.test(archivo);

function archivosDePrueba(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).flatMap((entrada) => {
    if (SALTAR.has(entrada)) return [];
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return archivosDePrueba(ruta);
    return ES_PRUEBA(entrada) ? [ruta] : [];
  });
}

// --- El guardián ---------------------------------------------------------------------------

export function revisar(raiz) {
  const problemas = [];
  const directorio = join(raiz, MIGRACIONES);
  if (!existsSync(directorio)) {
    return {
      problemas: [`No encontré las migraciones en ${MIGRACIONES}`],
      arbol: new Map(),
    };
  }

  const { nombrePorSlug, desconocidas } = arbolDesdeMigraciones(directorio);
  for (const sentencia of desconocidas) {
    problemas.push(
      `No sé leer esta sentencia sobre \`categoria\`, así que el árbol que derivo está incompleto` +
        ` y preferí fallar — ${sentencia}`,
    );
  }

  problemas.push(...cruzarConElArbolAfirmado(raiz, nombrePorSlug));

  // Del más largo al más corto: `bolsos-dama-morrales-t9` es Morrales con sufijo, no la rama Dama.
  const porLongitud = [...nombrePorSlug.keys()].sort((a, b) => b.length - a.length);
  const base = (slug) =>
    nombrePorSlug.has(slug) ? slug : porLongitud.find((real) => slug.startsWith(`${real}-`));

  for (const ruta of archivosDePrueba(raiz)) {
    const texto = readFileSync(ruta, "utf8");
    const donde = relative(raiz, ruta).split("\\").join("/");
    for (const [nombre, slug] of fixturesDe(texto)) {
      if (SINTETICOS.has(slug)) continue;
      const real = base(slug);
      if (!real) {
        problemas.push(
          `${donde}: "${nombre}" cuelga del slug "${slug}", que no existe en el catálogo` +
            ` (ni es un sintético declarado en SINTETICOS)`,
        );
        continue;
      }
      const esperado = nombrePorSlug.get(real);
      if (!nombreCuadra(nombre, esperado) && !ANTES_DE_UN_RENOMBRADO.has(`${nombre}|${slug}`)) {
        problemas.push(
          `${donde}: "${nombre}" con slug "${slug}" — el árbol lo llama "${esperado}"`,
        );
      }
    }
  }
  return { problemas, arbol: nombrePorSlug };
}

function* fixturesDe(texto) {
  for (const forma of FORMAS) {
    for (const [, nombre, slug] of texto.matchAll(forma)) yield [nombre, slug];
  }
  for (const [, slug, nombre] of texto.matchAll(FORMA_INVERTIDA)) yield [nombre, slug];
}

/**
 * Los slugs que derivé tienen que ser los mismos que `ArbolDeCategoriasTest` afirma, y esa prueba
 * los compara contra un Postgres de verdad ya migrado. Son dos lecturas independientes de la misma
 * realidad: si discrepan, una de las dos está mal y da igual cuál.
 */
function cruzarConElArbolAfirmado(raiz, nombrePorSlug) {
  const ruta = join(raiz, ARBOL_AFIRMADO);
  if (!existsSync(ruta)) return [];
  const java = readFileSync(ruta, "utf8");

  const afirmados = new Set();
  for (const mapa of ["PRIMER_NIVEL", "SEGUNDO_NIVEL"]) {
    const bloque = java.match(new RegExp(`${mapa}\\s*=([\\s\\S]*?);`));
    if (!bloque) return [`${ARBOL_AFIRMADO}: no encontré ${mapa}, que es con lo que me cruzo`];
    for (const [, slug] of bloque[1].matchAll(/"([a-z][a-z0-9-]*)"/g)) afirmados.add(slug);
  }

  const soloEnMigraciones = [...nombrePorSlug.keys()].filter((s) => !afirmados.has(s));
  const soloEnLaPrueba = [...afirmados].filter((s) => !nombrePorSlug.has(s));
  const problemas = [];
  if (soloEnMigraciones.length > 0) {
    problemas.push(
      `Las migraciones dejan categorías que ArbolDeCategoriasTest no afirma: ${soloEnMigraciones.join(", ")}`,
    );
  }
  if (soloEnLaPrueba.length > 0) {
    problemas.push(
      `ArbolDeCategoriasTest afirma categorías que ninguna migración crea: ${soloEnLaPrueba.join(", ")}`,
    );
  }
  return problemas;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  const raiz = fileURLToPath(new URL("..", import.meta.url));
  const { problemas, arbol } = revisar(raiz);
  if (problemas.length > 0) {
    console.error("Categorías incoherentes:\n");
    for (const problema of problemas) console.error(`  ${problema}`);
    console.error(`\n${problemas.length} problema(s).`);
    process.exit(1);
  }
  console.log(
    `Las ${arbol.size} categorías del árbol cuadran, y cada fixture llama a la suya por su nombre.`,
  );
}
