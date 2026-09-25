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
// Los pidió la banda de portada, donde las tres cosas que el sitio sí puede prometer —el envío
// cotizado, la contraentrega donde está habilitada y la garantía legal— van con un ancla visual
// cada una. Sin icono, las tres se leen como una lista de texto corrido y ninguna destaca.
export { Banknote as iconoContraentrega } from 'lucide';
export { ShieldCheck as iconoGarantia } from 'lucide';
// Los pidió el carrusel de portada, que rota solo cada cinco segundos y por eso necesita un
// mecanismo de pausa visible: WCAG 2.2.2 lo exige de todo movimiento automático que dure más de
// cinco segundos, y pararlo con el puntero encima no es un mecanismo para quien entra con el dedo
// ni para quien navega con teclado.
export { Pause as iconoPausar } from 'lucide';
export { Play as iconoReanudar } from 'lucide';
// Los pidió `ts-alternador-tema`, que dibuja los dos a la vez y deja que el CSS tape uno: el icono
// anuncia a qué tema lleva el clic, no en cuál estás. En claro se ve la luna, en oscuro el sol.
export { Moon as iconoTemaOscuro } from 'lucide';
export { Sun as iconoTemaClaro } from 'lucide';
// Los pidió el menú lateral: recogido solo caben iconos, así que cada rama de primer nivel
// necesita el suyo — sin ellos el riel es una columna de cuadrados vacíos. El chevron es el que
// dice si una rama está desplegada, y gira con la misma curva que el panel.
export { LayoutGrid as iconoCatalogo } from 'lucide';
export { LayoutDashboard as iconoPanel } from 'lucide';
export { ChevronDown as iconoChevron } from 'lucide';
// Lo pidió el botón que fija el menú lateral abierto. `PanelLeft` y no una chincheta: el icono
// tiene que decir qué queda fijo —el panel de la izquierda—, no con qué se sujeta.
export { PanelLeft as iconoFijar } from 'lucide';
// Los pidió el ancla dentro del control: desde el 24 de septiembre de 2026 los campos de búsqueda
// y los de identidad llevan su icono dentro, a la izquierda, y el nombre del campo vive en el
// placeholder. El icono no es decoración ahí — es lo único que queda en pantalla diciendo qué se
// escribe cuando el placeholder desaparece al primer carácter. Nunca es el nombre accesible: ese
// lo pone la etiqueta, que sigue existiendo aunque esté en `sr-only`.
export { Search as iconoBuscar } from 'lucide';
export { User as iconoUsuario } from 'lucide';
export { Lock as iconoClave } from 'lucide';
// El de "Ordenar por", que es el único de los cuatro filtros que no nombra un atributo del
// producto: sin ancla, "Relevancia" suelto al lado de "Marca" se lee como una marca más.
export { ArrowUpDown as iconoOrden } from 'lucide';
// Lo pidió el cuarto sello de la banda de portada: "Diversas opciones de pago". `CreditCard` y no
// un emoji, aunque la petición lo mencionara: los otros tres sellos son Lucide con el mismo trazo
// y el mismo tamaño, y un emoji lo dibuja la fuente del sistema —cambia de forma, de color y de
// alto en cada plataforma—, así que la fila dejaría de leerse como una familia.
export { CreditCard as iconoMediosDePago } from 'lucide';
// Lo pidió el menú de acciones de una fila (`shared/ui/menu`): los tres puntos que abren lo que no
// cabe en la fila. Horizontal y no vertical porque es lo que ocupa menos alto en una tabla densa.
export { MoreHorizontal as iconoAcciones } from 'lucide';
