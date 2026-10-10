// Las pruebas del guardián de categorías. Existen por lo mismo que las del de capas —un guardián
// que no dispara da confianza falsa— y además porque **este guardián nació con dos errores**, los
// dos cometidos a mano el 10 de octubre de 2026 mientras se barrían los fixtures:
//
//   1. Resolver el slug con sufijo por el prefijo real **más corto**: `bolsos-dama-morrales-t9` se
//      leía como la rama `bolsos-dama` y el guardián pedía que se llamara "Dama". Ruido que tapa
//      los hallazgos de verdad.
//   2. Leer un producto del frontend como si fuera una categoría: `{ nombre, slug }` es la misma
//      forma en los dos, y sin exigir `linea:` el guardián fallaba por nueve sitios sanos.
//
// Cada caso de abajo fija uno de los dos, más lo que el guardián tiene que ver de verdad.
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { revisar, arbolDesdeMigraciones, sentencias } from "../verificar-categorias.mjs";

const MIGRACIONES = "apps/api/infrastructure/src/main/resources/db/migration";

/** Un árbol de mentira: dos ramas y dos hojas, con las mismas formas de SQL que usa el repo. */
const SQL_BASE = `
-- Un comentario con 'comillas' y un punto y coma; dentro.
insert into categoria (id, nombre, slug, linea, creado_en) values
    (gen_random_uuid(), 'Celulares', 'celulares', 'TECNOLOGIA', now()),
    (gen_random_uuid(), 'Consolas',  'consolas',  'TECNOLOGIA', now())
on conflict (slug) do nothing;

update categoria set nombre = 'Consolas de videojuegos' where slug = 'consolas';

insert into categoria (id, nombre, slug, linea, padre_id, creado_en) values
    (gen_random_uuid(), 'Dama', 'bolsos-dama', 'BOLSOS', null, now())
on conflict (slug) do nothing;

insert into categoria (id, nombre, slug, linea, padre_id, creado_en)
select gen_random_uuid(), h.nombre, h.slug, p.linea, p.id, now()
from (values
    ('bolsos-dama', 'Morrales',     'bolsos-dama-morrales'),
    ('bolsos-dama', 'Manos libres', 'bolsos-dama-manos-libres')
) as h (padre_slug, nombre, slug)
join categoria p on p.slug = h.padre_slug
on conflict (slug) do nothing;
`;

/** Lo que `ArbolDeCategoriasTest` afirma del árbol de mentira, con el que el guardián se cruza. */
const JAVA_AFIRMADO = `
  private static final Map<String, List<String>> PRIMER_NIVEL =
      Map.of("TECNOLOGIA", List.of("celulares", "consolas"), "BOLSOS", List.of("bolsos-dama"));

  private static final Map<String, List<String>> SEGUNDO_NIVEL =
      Map.of("bolsos-dama", List.of("bolsos-dama-morrales", "bolsos-dama-manos-libres"));
`;

const RUTA_AFIRMADO =
  "apps/api/infrastructure/src/test/java/co/tecnosport/api/infrastructure/catalogo/ArbolDeCategoriasTest.java";

function conArbol(archivos, prueba) {
  const raiz = mkdtempSync(join(tmpdir(), "categorias-"));
  const todos = {
    [`${MIGRACIONES}/V1__arbol.sql`]: SQL_BASE,
    [RUTA_AFIRMADO]: JAVA_AFIRMADO,
    ...archivos,
  };
  for (const [ruta, contenido] of Object.entries(todos)) {
    const completa = join(raiz, ruta);
    mkdirSync(dirname(completa), { recursive: true });
    writeFileSync(completa, contenido);
  }
  try {
    prueba(raiz);
  } finally {
    rmSync(raiz, { recursive: true, force: true });
  }
}

const javaConCategoria = (nombre, slug) => ({
  "apps/api/application/src/test/java/CasoTest.java": `
    class CasoTest {
      void caso() {
        Categoria c = Categoria.crear("${nombre}", new Slug("${slug}"), LineaCatalogo.BOLSOS);
      }
    }`,
});

// --- Lo que el árbol derivado tiene que decir ---------------------------------------------

test("el árbol sale de las migraciones, con el renombrado aplicado", () => {
  conArbol({}, (raiz) => {
    const { nombrePorSlug, desconocidas } = arbolDesdeMigraciones(join(raiz, MIGRACIONES));

    assert.deepEqual(desconocidas, []);
    assert.equal(nombrePorSlug.get("bolsos-dama-morrales"), "Morrales");
    assert.equal(nombrePorSlug.get("bolsos-dama"), "Dama");
    // El `update` posterior manda: así entró "Consolas de videojuegos" en V63.
    assert.equal(nombrePorSlug.get("consolas"), "Consolas de videojuegos");
  });
});

test("un `delete` quita la categoría del árbol", () => {
  conArbol(
    {
      [`${MIGRACIONES}/V2__sin_consolas.sql`]: "delete from categoria where slug in ('consolas');",
    },
    (raiz) => {
      const { nombrePorSlug } = arbolDesdeMigraciones(join(raiz, MIGRACIONES));
      assert.equal(nombrePorSlug.has("consolas"), false);
    },
  );
});

test("una sentencia sobre `categoria` que no sabe leer es un problema, no un silencio", () => {
  conArbol(
    {
      [`${MIGRACIONES}/V2__raro.sql`]:
        "insert into categoria select * from otra_tabla_que_nadie_previo;",
    },
    (raiz) => {
      const problemas = revisar(raiz).problemas;
      assert.equal(problemas.length, 1);
      assert.match(problemas[0], /No sé leer esta sentencia/);
    },
  );
});

test("un `update` que no toca el nombre no estorba", () => {
  conArbol(
    {
      [`${MIGRACIONES}/V2__tallas.sql`]:
        "update categoria set escala_tallas = E'S\\nM' where slug in ('bolsos-dama');",
    },
    (raiz) => assert.deepEqual(revisar(raiz).problemas, []),
  );
});

test("los comentarios y las comillas no parten las sentencias", () => {
  const leidas = sentencias("-- uno; dos\ninsert into x values ('a;b');\nupdate y set z = 1;");
  assert.equal(leidas.length, 2);
  assert.equal(leidas[0], "insert into x values ('a;b')");
});

// --- Lo que tiene que ver en los fixtures -------------------------------------------------

test("un slug que el catálogo no tiene es un problema", () => {
  conArbol(javaConCategoria("Bolsos", "bolsos"), (raiz) => {
    const problemas = revisar(raiz).problemas;
    assert.equal(problemas.length, 1);
    assert.match(problemas[0], /no existe en el catálogo/);
  });
});

test("un nombre que no es el de su slug es un problema", () => {
  conArbol(javaConCategoria("Bolsos", "bolsos-dama-morrales"), (raiz) => {
    const problemas = revisar(raiz).problemas;
    assert.equal(problemas.length, 1);
    assert.match(problemas[0], /el árbol lo llama "Morrales"/);
  });
});

/** El error nº 1: el prefijo más corto leía esto como la rama Dama y pedía que se llamara "Dama". */
test("el sufijo de Testcontainers se resuelve por el prefijo más largo", () => {
  conArbol(javaConCategoria("Morrales", "bolsos-dama-morrales-t9"), (raiz) =>
    assert.deepEqual(revisar(raiz).problemas, []),
  );
});

test("y con el sufijo puesto, el nombre sigue siendo obligatorio", () => {
  conArbol(javaConCategoria("Bolsos", "bolsos-dama-morrales-t9"), (raiz) => {
    const problemas = revisar(raiz).problemas;
    assert.equal(problemas.length, 1);
    assert.match(problemas[0], /el árbol lo llama "Morrales"/);
  });
});

test("una etiqueta de prueba detrás del nombre real se tolera", () => {
  conArbol(javaConCategoria("Morrales TC", "bolsos-dama-morrales-t9"), (raiz) =>
    assert.deepEqual(revisar(raiz).problemas, []),
  );
});

test("un sintético declarado no molesta", () => {
  conArbol(javaConCategoria("Ropa", "ropa-mapa"), (raiz) =>
    assert.deepEqual(revisar(raiz).problemas, []),
  );
});

/** El error nº 2: un producto se escribe igual que una categoría y no es una. */
test("un producto del frontend no se lee como categoría", () => {
  conArbol(
    {
      "apps/web/src/app/caso.spec.ts": `
        const producto = { nombre: 'Camiseta running Dry-Fit', slug: 'camiseta', precio: 1 };
      `,
    },
    (raiz) => assert.deepEqual(revisar(raiz).problemas, []),
  );
});

test("una categoría del frontend sí, y con su nombre", () => {
  conArbol(
    {
      "apps/web/src/app/caso.spec.ts": `
        const categoria = { nombre: 'Bolsos', slug: 'bolsos-dama-morrales', linea: 'BOLSOS' };
      `,
    },
    (raiz) => {
      const problemas = revisar(raiz).problemas;
      assert.equal(problemas.length, 1);
      assert.match(problemas[0], /el árbol lo llama "Morrales"/);
    },
  );
});

test("solo mira archivos de prueba: el código de producción no es su asunto", () => {
  conArbol(
    {
      "apps/web/src/app/pagina.ts": "const c = { nombre: 'X', slug: 'inventado', linea: 'ROPA' };",
    },
    (raiz) => assert.deepEqual(revisar(raiz).problemas, []),
  );
});

// --- El cruce con la prueba que mira la base de verdad -------------------------------------

test("una categoría que la migración crea y la prueba del árbol no afirma es un problema", () => {
  conArbol(
    {
      [`${MIGRACIONES}/V2__nueva.sql`]:
        "insert into categoria (id, nombre, slug, linea, creado_en) values" +
        " (gen_random_uuid(), 'Tablets', 'tablets', 'TECNOLOGIA', now());",
    },
    (raiz) => {
      const problemas = revisar(raiz).problemas;
      assert.equal(problemas.length, 1);
      assert.match(problemas[0], /ArbolDeCategoriasTest no afirma: tablets/);
    },
  );
});

test("y al revés: lo que la prueba afirma sin que ninguna migración lo cree", () => {
  conArbol(
    {
      [RUTA_AFIRMADO]: JAVA_AFIRMADO.replace('"celulares"', '"celulares", "relojes"'),
    },
    (raiz) => {
      const problemas = revisar(raiz).problemas;
      assert.equal(problemas.length, 1);
      assert.match(problemas[0], /ninguna migración crea: relojes/);
    },
  );
});
