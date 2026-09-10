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
// Los pidió el bloque de contacto del pie, que pasó de tres líneas a cinco cuando entraron el
// horario y las redes: sin un ancla visual, "Llamar · WhatsApp", el correo, la dirección y el
// horario se leen como un párrafo.
export { Phone as iconoTelefono } from 'lucide';
export { Mail as iconoCorreo } from 'lucide';
export { MapPin as iconoUbicacion } from 'lucide';
export { Clock as iconoHorario } from 'lucide';
// Los pidió el tipo de entrega, que se muestra en tres pantallas del recorrido —confirmar, estado
// del pedido y el resumen— y donde la diferencia entre que te lo llevan y que lo recoges es lo
// primero que alguien busca al volver a mirar su pedido.
export { Truck as iconoEnvio } from 'lucide';
// Los nombres son los de Lucide 1.x. `AlertTriangle` y `CheckCircle` siguen existiendo como
// alias de `TriangleAlert` y `CircleCheck`, pero el alias es el nombre viejo: se usa el actual.
