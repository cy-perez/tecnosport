import { Route, Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { adminRoutes } from './admin.routes';
import { REPOSITORIO_DIFUSION } from './difusion/domain/repositorio-difusion.puerto';

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
});

/** La primera ruta con ese `path` en todo el árbol, a cualquier profundidad. */
function buscarRuta(rutas: Routes, path: string): Route {
  for (const ruta of rutas) {
    if (ruta.path === path) {
      return ruta;
    }
    const hija = ruta.children && buscarRuta(ruta.children, path);
    if (hija) {
      return hija;
    }
  }
  throw new Error(`No hay ninguna ruta con path '${path}'.`);
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
