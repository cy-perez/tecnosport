// Las pruebas del guardián de capas. Existen porque un guardián que nunca dispara da confianza
// falsa, y eso ya pasó dos veces: `eslint-plugin-boundaries` aceptaba la configuración sin
// aplicarla, y la expresión de `capaDe` dejaba fuera todo `features/admin` sin que nadie lo notara.
// Cada caso mete una violación en un árbol de mentira y comprueba que la ve.
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { revisar } from "../verificar-capas.mjs";

function arbol(archivos) {
  const raiz = mkdtempSync(join(tmpdir(), "capas-"));
  for (const [ruta, contenido] of Object.entries(archivos)) {
    const completa = join(raiz, ruta);
    mkdirSync(dirname(completa), { recursive: true });
    writeFileSync(completa, contenido);
  }
  return raiz;
}

function conArbol(archivos, prueba) {
  const raiz = arbol(archivos);
  try {
    prueba(raiz);
  } finally {
    rmSync(raiz, { recursive: true, force: true });
  }
}

const ADAPTADOR = { "features/carrito/infrastructure/almacen.ts": "export const x = 1;\n" };

test("presentation importando infrastructure es una violación", () => {
  conArbol(
    {
      ...ADAPTADOR,
      "features/carrito/presentation/pagina.ts": "import { x } from '../infrastructure/almacen';\n",
    },
    (raiz) => assert.equal(revisar(raiz).violaciones.length, 1),
  );
});

test("el panel también está dentro del grafo: features/admin/<x>/<capa>", () => {
  conArbol(
    {
      "features/admin/pedidos/infrastructure/http.ts": "export const x = 1;\n",
      "features/admin/pedidos/presentation/lista.ts":
        "import { x } from '../infrastructure/http';\n",
    },
    (raiz) => {
      const { violaciones } = revisar(raiz);
      assert.equal(violaciones.length, 1);
      assert.equal(violaciones[0].desde, "presentation");
    },
  );
});

test("un import dinámico cuenta igual", () => {
  conArbol(
    {
      ...ADAPTADOR,
      "features/carrito/presentation/pagina.ts":
        "export const cargar = () => import('../infrastructure/almacen');\n",
    },
    (raiz) => assert.equal(revisar(raiz).violaciones.length, 1),
  );
});

test("un import sin nada que importar cuenta igual", () => {
  conArbol(
    {
      ...ADAPTADOR,
      "features/carrito/presentation/pagina.ts": "import '../infrastructure/almacen';\n",
    },
    (raiz) => assert.equal(revisar(raiz).violaciones.length, 1),
  );
});

test("core/ no puede depender de una funcionalidad", () => {
  conArbol(
    {
      "features/catalogo/domain/modelo.ts": "export type X = string;\n",
      "core/imagenes/cargador.ts": "import { X } from '../../features/catalogo/domain/modelo';\n",
    },
    (raiz) => assert.equal(revisar(raiz).violaciones.length, 1),
  );
});

test("las rutas eligen el adaptador y las pruebas montan el escenario: no fallan", () => {
  conArbol(
    {
      ...ADAPTADOR,
      "features/carrito/presentation/carrito.routes.ts":
        "import { x } from '../infrastructure/almacen';\n",
      "features/carrito/presentation/pagina.spec.ts":
        "import { x } from '../infrastructure/almacen';\n",
    },
    (raiz) => {
      const { violaciones, enPruebas } = revisar(raiz);
      assert.equal(violaciones.length, 0);
      assert.equal(enPruebas.length, 1);
    },
  );
});

test("domain hacia application es una violación; application hacia domain no", () => {
  conArbol(
    {
      "features/carrito/domain/modelo.ts": "import { y } from '../application/store';\n",
      "features/carrito/application/store.ts":
        "import { M } from '../domain/modelo';\nexport const y = 1;\n",
    },
    (raiz) => {
      const { violaciones } = revisar(raiz);
      assert.equal(violaciones.length, 1);
      assert.equal(violaciones[0].desde, "domain");
    },
  );
});

// La quinta regla mira paquetes, no rutas: `import { Params } from '@angular/router'` en domain no
// tiene ningún `../` que resolver, y por eso cinco archivos pasaron sin que el guardián lo viera.
test("domain importando un paquete de framework es una violación", () => {
  conArbol(
    {
      "features/catalogo/domain/filtro.ts": "import { Params } from '@angular/router';\n",
      "features/catalogo/domain/consulta.ts":
        "import { injectQuery } from '@tanstack/angular-query-experimental';\n",
      "features/catalogo/domain/flujo.ts": "import { Observable } from 'rxjs';\n",
      "features/catalogo/domain/texto.ts": "export const t = () => import('@jsverse/transloco');\n",
    },
    (raiz) => {
      const { violaciones } = revisar(raiz);
      assert.deepEqual(violaciones.map((v) => v.especificador).sort(), [
        "@angular/router",
        "@jsverse/transloco",
        "@tanstack/angular-query-experimental",
        "rxjs",
      ]);
      assert.ok(violaciones.every((v) => v.desde === "domain" && v.destino === "framework"));
    },
  );
});

test("el token del puerto sí cabe en domain; cualquier otra cosa de @angular/core, no", () => {
  conArbol(
    {
      "features/carrito/domain/repositorio.puerto.ts":
        "import { InjectionToken } from '@angular/core';\n",
      "features/carrito/domain/estado.ts":
        "import { InjectionToken, signal } from '@angular/core';\n",
      "features/carrito/domain/todo.ts": "import * as core from '@angular/core';\n",
    },
    (raiz) => {
      const archivos = revisar(raiz).violaciones.map((v) => v.archivo.split("/").pop());
      assert.deepEqual(archivos.sort(), ["estado.ts", "todo.ts"]);
    },
  );
});

test("fuera de domain la regla de paquetes no aplica, y en una prueba solo se informa", () => {
  conArbol(
    {
      "features/carrito/application/store.ts":
        "import { injectQuery } from '@tanstack/angular-query-experimental';\n",
      "features/carrito/domain/modelo.spec.ts":
        "import { TestBed } from '@angular/core/testing';\n",
    },
    (raiz) => {
      const { violaciones, enPruebas } = revisar(raiz);
      assert.equal(violaciones.length, 0);
      assert.equal(enPruebas.length, 1);
    },
  );
});
