import { EstadoProducto } from '../domain/producto-admin.model';

/**
 * En su propio archivo y no en la página de la lista: la edición también la usa, y importarla desde
 * allí arrastraba el componente entero de la lista al bundle de la edición. Mismo criterio que
 * `borradores/presentation/estado-borrador.ts`.
 */
export const CLAVE_ETIQUETA_ESTADO: Record<EstadoProducto, string> = {
  BORRADOR: 'admin.productos.estados.borrador',
  PUBLICADO: 'admin.productos.estados.publicado',
};

/**
 * La insignia de estado. Contorno y no relleno: el relleno de color pide un `sobre-` propio por
 * cada estado y el sistema solo tiene los de primario, acento, marca y deshabilitado —inventar
 * dos sería inventar color, que es lo que la regla dura #2 prohíbe—. Con el contorno, el par que
 * hay que verificar es `--color-exito` sobre `--color-superficie`, que ya existe en esta misma
 * pantalla (el aviso de "quedó publicado").
 */
const CLASES_INSIGNIA =
  'inline-flex items-center rounded-completo border px-12 py-4 text-xs font-medio';

const CLASES_INSIGNIA_ESTADO: Record<EstadoProducto, string> = {
  BORRADOR: 'border-ts-borde text-ts-texto-suave',
  PUBLICADO: 'border-ts-exito text-ts-exito',
};

export function clasesDeEstadoProducto(estado: EstadoProducto): string {
  return CLASES_INSIGNIA + ' ' + CLASES_INSIGNIA_ESTADO[estado];
}

/** Lo que se puede hacer con un producto entero, y lo que cada acción arrastra al confirmarse. */
export type AccionDeProducto = 'publicar' | 'retirar' | 'eliminar';

/**
 * Los textos de cada confirmación: la pregunta, lo que implica, el botón que la acepta y el acuse.
 *
 * <p>Como tabla y no como tres `?:` en la plantilla: con dos acciones ya era un condicional
 * anidado por cada línea de la caja, y con tres son nueve sitios donde emparejar mal la pregunta
 * de una con el botón de otra. Las de publicar y retirar conservan sus claves originales —el texto
 * no cambió— y por eso viven bajo `publicar.` aunque una de ellas retire.
 */
export const TEXTOS_DE_CONFIRMACION: Record<
  AccionDeProducto,
  { pregunta: string; implica: string; accion: string; hecho: string }
> = {
  publicar: {
    pregunta: 'admin.productos.publicar.confirmar',
    implica: 'admin.productos.publicar.loQueImplica',
    accion: 'admin.productos.publicar.confirmarAccion',
    hecho: 'admin.productos.publicar.hecho',
  },
  retirar: {
    pregunta: 'admin.productos.publicar.confirmarRetirar',
    implica: 'admin.productos.publicar.loQueImplicaRetirar',
    accion: 'admin.productos.publicar.confirmarRetirarAccion',
    hecho: 'admin.productos.publicar.retirado',
  },
  eliminar: {
    pregunta: 'admin.productos.eliminar.confirmar',
    implica: 'admin.productos.eliminar.loQueImplica',
    accion: 'admin.productos.eliminar.accion',
    hecho: 'admin.productos.eliminar.hecho',
  },
};

/** La clave genérica del error de cada acción, cuando el código del backend no tiene traducción. */
export const CLAVE_ERROR: Record<AccionDeProducto, string> = {
  publicar: 'admin.productos.publicar.error',
  retirar: 'admin.productos.publicar.errorRetirar',
  eliminar: 'admin.productos.eliminar.error',
};
