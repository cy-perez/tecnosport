/**
 * El único sitio del proyecto que importa de `lucide`.
 *
 * Dos motivos. Uno: el set de iconos que el sitio realmente usa queda
 * auditable en un archivo, en vez de repartido en importaciones por toda la
 * capa de presentación. Dos: si algún día se cambia de fuente de iconos, se
 * cambia aquí y no en cada plantilla.
 *
 * Se reexporta con nombre, no dentro de un objeto, para que el empaquetador
 * pueda descartar lo que no se use. **Solo se agrega un icono cuando hay una
 * pantalla que lo pide** — `docs/04-ui-marca.md`: cada dependencia es deuda, y
 * un registro que crece "por si acaso" es peso muerto en el bundle inicial.
 */
export { ShoppingCart as iconoCarrito } from 'lucide';
export { X as iconoCerrar } from 'lucide';
export { Menu as iconoMenu } from 'lucide';
// Lo pidió `ts-checkbox`: el visto de una casilla marcada.
export { Check as iconoVisto } from 'lucide';
