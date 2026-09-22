import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { ProductoAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';
import { CLAVE_EXISTENCIAS } from './listar-existencias.consulta';
import { CLAVE_MEDIDAS } from './listar-medidas.consulta';
import { CLAVE_VARIANTES_SIN_MEDIR } from './listar-variantes-sin-medir.consulta';

/**
 * Publicar cambia el estado del producto, y ese estado lo enseñan **cuatro** consultas: la lista
 * del panel, las dos pantallas de inventario —que marcan qué está a la venta— y el conteo de
 * sin-medir, cuyo aviso distingue borradores de publicados.
 *
 * <p>Invalidar solo la lista dejaría los avisos del tablero diciendo el número de antes hasta que
 * venciera su `staleTime`: un producto recién publicado sin existencia y sin medir tiene que
 * aparecer en esas cuentas de inmediato, porque es justo cuando le puede pasar algo a un
 * comprador.
 */
/** Las cuatro llaves que enseñan el estado de un producto. Publicar y retirar mueven las mismas. */
async function invalidarLoQueEnsenaElEstado(queryClient: QueryClient): Promise<void> {
  // La lista lleva el filtro dentro de su llave —una por página y orden—, así que se invalida el
  // prefijo: publicar desde la página 2 tiene que refrescar también la 1.
  await queryClient.invalidateQueries({ queryKey: ['admin', 'productos'] });
  await queryClient.invalidateQueries({ queryKey: CLAVE_EXISTENCIAS });
  await queryClient.invalidateQueries({ queryKey: CLAVE_MEDIDAS });
  await queryClient.invalidateQueries({ queryKey: CLAVE_VARIANTES_SIN_MEDIR });
  // Y la quinta, que faltaba: el catálogo público. Son cuatro consultas del panel y una de la
  // tienda, y esta última es la que ve quien compra. En la misma sesión —el admin con la vitrina
  // abierta en otra pestaña de la SPA— un producto retirado seguía en la rejilla hasta que su
  // entrada venciera, y al hacer clic la ficha respondía 404. `ADR-0051` promete que desaparece;
  // en esa sesión no desaparecía. Es el mismo descuido que `ajustar-existencia` ya tenía cubierto.
  await queryClient.invalidateQueries({ queryKey: ['catalogo'] });
}

export function usarPublicarProducto() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (id: string): Promise<ProductoAdmin> => repositorio.publicar(id),
    onSuccess: () => invalidarLoQueEnsenaElEstado(queryClient),
  }));
}

/**
 * Retirar de la vitrina. Mueve exactamente lo mismo que publicar, en el otro sentido: el producto
 * sale de la tienda y vuelve a contar como borrador en los avisos del tablero.
 */
export function usarDespublicarProducto() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (id: string): Promise<ProductoAdmin> => repositorio.despublicar(id),
    onSuccess: () => invalidarLoQueEnsenaElEstado(queryClient),
  }));
}
