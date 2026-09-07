/**
 * El aspecto de un control de formulario, en un solo sitio.
 *
 * `ts-campo` y `ts-select` envuelven controles nativos distintos —`<input>` y
 * `<select>`— pero se ven idénticos y así deben seguir: mismo borde, mismo
 * relleno, mismo objetivo táctil, mismo anillo de foco y mismo estado
 * deshabilitado. Estaban duplicados en dos SCSS con los mismos valores, que es
 * exactamente la forma de que un día dejen de coincidir.
 *
 * `box-border` no es opcional: el proyecto no tiene reset global —Preflight
 * está deliberadamente fuera, ver `src/tailwind.css`— y sin él el relleno
 * sumaría por fuera del 100 %. Y sin `w-full`, un `<input>` toma su ancho del
 * atributo `size` y un `<select>` del texto de su opción más larga, así que dos
 * columnas de la misma rejilla terminaban de tamaños distintos.
 */
export const CLASES_CONTROL =
  'box-border w-full min-h-tactil p-12 border border-ts-borde-control ' +
  'bg-ts-superficie text-ts-texto font-texto text-base ' +
  'anillo-foco ' +
  'disabled:bg-ts-superficie-alt disabled:text-ts-deshabilitado disabled:cursor-not-allowed ' +
  'aria-invalid:border-ts-error';

/** La etiqueta y el mensaje de error, por el mismo motivo. */
export const CLASES_ETIQUETA = 'text-sm text-ts-texto-suave';
export const CLASES_ERROR = 'm-0 text-sm text-ts-error';
