import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';
import { CLAVE_EXISTENCIAS } from './listar-existencias.consulta';
import { CLAVE_MEDIDAS } from './listar-medidas.consulta';
import { CLAVE_VARIANTES_SIN_MEDIR } from './listar-variantes-sin-medir.consulta';

/**
 * Borrar un producto del catálogo.
 *
 * <p>Invalida **las mismas cinco llaves que publicar y retirar**, y por una razón de más: aquí no
 * cambia un estado, desaparecen filas. Las dos pantallas de inventario y el conteo de sin-medir
 * listan por variante, así que las del producto borrado se quedarían en pantalla hasta que venciera
 * su `staleTime` — y pulsar "contar" o "medir" sobre una variante que ya no existe responde 404.
 *
 * <p>El catálogo público entra por lo mismo que en `publicar-producto.mutacion.ts`: el admin con la
 * vitrina abierta en otra pestaña de la SPA. Aquí no debería hacer falta —solo se borra lo que está
 * en borrador, y un borrador no sale en la rejilla— pero invalidar una consulta que no cambió no
 * cuesta nada, y depender de que la regla del servidor no se relaje nunca sí.
 */
export function usarEliminarProducto() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (id: string): Promise<void> => repositorio.eliminar(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin', 'productos'] });
      await queryClient.invalidateQueries({ queryKey: CLAVE_EXISTENCIAS });
      await queryClient.invalidateQueries({ queryKey: CLAVE_MEDIDAS });
      await queryClient.invalidateQueries({ queryKey: CLAVE_VARIANTES_SIN_MEDIR });
      await queryClient.invalidateQueries({ queryKey: ['catalogo'] });
    },
  }));
}
