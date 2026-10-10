import { Route, Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { adminRoutes } from './admin.routes';
import { REPOSITORIO_DIFUSION } from './difusion/domain/repositorio-difusion.puerto';
import { REPOSITORIO_CATEGORIAS } from '../catalogo/domain/repositorio-categorias.puerto';
import { REPOSITORIO_PRODUCTOS_NO_PUBLICADOS } from './productos/domain/productos-no-publicados.puerto';
import { REPOSITORIO_ARCHIVOS_DE_INGESTA } from './ingestas/domain/repositorio-archivos-de-ingesta.puerto';

/**
 * Lo que los specs de página **no** pueden ver.
 *
 * Cada prueba de componente provee los puertos a mano para poder montarlo aislado, que es lo
 * correcto — pero eso significa que ninguna comprueba si la ruta de verdad los provee. El 30 de
 * septiembre de 2026 `REPOSITORIO_DIFUSION` acabó en la ruta de `crear` en vez de la de
 * `:id/editar`: las dos tienen bloques de `providers` idénticos y un reemplazo pegó en la primera.
 * La suite entera quedó en verde y la ficha reventaba con un NG0201 y la pantalla en blanco.
 *
 * Esto mira la configuración, no un montaje. Es barato y cubre justo el hueco.
 */
describe('adminRoutes', () => {
  it('la ficha de edición provee el repositorio de difusión, que es donde se monta el panel', () => {
    const editar = buscarRuta(adminRoutes, ':id/editar');

    expect(proveeTokens(editar)).toContain(REPOSITORIO_DIFUSION);
  });

  /** Un producto que todavía no existe no se puede difundir: allí el puerto sobra. */
  it('el alta no lo provee, porque no monta el panel', () => {
    const crear = buscarRuta(adminRoutes, 'crear');

    expect(proveeTokens(crear)).not.toContain(REPOSITORIO_DIFUSION);
  });

  /** La talla de una variante se elige de la escala de la categoría, y la escala viene de ahí. */
  it('agregar una variante provee las categorías', () => {
    const agregar = buscarRuta(adminRoutes, ':productoId/variantes/crear');

    expect(proveeTokens(agregar)).toContain(REPOSITORIO_CATEGORIAS);
  });

  /**
   * La lista de productos monta el bloque de limpieza, que inyecta su propio puerto. El 10 de
   * octubre de 2026 el proveedor acabó en `panel` —el primer bloque con el puerto de productos— y
   * la lista reventaba con NG0201, con todas las pruebas en verde.
   */
  it('productos provee el puerto de los no publicados, y el panel no', () => {
    expect(proveeTokens(buscarRuta(adminRoutes, 'productos'))).toContain(
      REPOSITORIO_PRODUCTOS_NO_PUBLICADOS,
    );
    expect(proveeTokens(buscarRuta(adminRoutes, 'panel'))).not.toContain(
      REPOSITORIO_PRODUCTOS_NO_PUBLICADOS,
    );
  });

  /** El historial de zips cuelga de ingestas y no tiene ruta propia con proveedores. */
  it('ingestas provee el puerto de los archivos subidos', () => {
    expect(proveeTokens(buscarRuta(adminRoutes, 'ingestas'))).toContain(
      REPOSITORIO_ARCHIVOS_DE_INGESTA,
    );
  });
});

/**
 * La primera ruta con ese `path` en todo el árbol, a cualquier profundidad.
 *
 * <p>La búsqueda en sí no lanza: hasta el 30 de septiembre de 2026 sí lo hacía, y la primera rama
 * con hijos que no tuviera el `path` —`proveedores`, recién llegada— cortaba el recorrido antes
 * de llegar a `productos`, donde sí está.
 */
function buscarRuta(rutas: Routes, path: string): Route {
  const ruta = buscar(rutas, path);
  if (!ruta) {
    throw new Error(`No hay ninguna ruta con path '${path}'.`);
  }
  return ruta;
}

function buscar(rutas: Routes, path: string): Route | undefined {
  for (const ruta of rutas) {
    if (ruta.path === path) {
      return ruta;
    }
    const hija = ruta.children && buscar(ruta.children, path);
    if (hija) {
      return hija;
    }
  }
  return undefined;
}

/**
 * Los tokens que la ruta provee. Solo mira los suyos, no los que hereda: lo que se comprueba es
 * que el puerto esté declarado donde corresponde, y heredarlo de un padre sería otra cosa.
 */
function proveeTokens(ruta: Route): unknown[] {
  return (ruta.providers ?? []).map((proveedor) =>
    typeof proveedor === 'object' && proveedor !== null && 'provide' in proveedor
      ? (proveedor as { provide: unknown }).provide
      : proveedor,
  );
}
