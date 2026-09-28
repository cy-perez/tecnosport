import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsAlternadorTema } from '../../shared/ts-alternador-tema/ts-alternador-tema';
import { TsIcono } from '../../shared/ui/icono/ts-icono';
import { TsIconoMarca } from '../../shared/ui/icono/ts-icono-marca';
import {
  iconoCorreo,
  iconoHorario,
  iconoTelefono,
  iconoUbicacion,
} from '../../shared/ui/icono/iconos';
import {
  marcaFacebook,
  marcaInstagram,
  marcaWhatsapp,
} from '../../shared/ui/icono/marcas.generado';
import type { LogoPago } from '../../shared/ui/icono/logos-pago.generado';
import {
  logoAddi,
  logoAmericanExpress,
  logoBancolombia,
  logoBbva,
  logoDaviplata,
  logoMastercard,
  logoNequi,
  logoPse,
  logoSistecredito,
  logoVisa,
} from '../../shared/ui/icono/logos-pago.generado';
import { TsLogoPago } from '../../shared/ui/icono/ts-logo-pago';

/**
 * Un destino del pie: los segmentos que van después del idioma, y la clave de su etiqueta.
 *
 * <p>Van como dato y no escritos uno por uno en la plantilla por lo de siempre: la lista de clases
 * lleva `anillo-foco-sobre-marca`, que es obligatorio, y siete copias son siete sitios donde se
 * puede caer. Ya se cayó una vez —los enlaces de la navegación del pie llevaban `anillo-foco`, que
 * sobre `--color-marca` es invisible en tema claro porque los dos tokens son el mismo grafito—.
 */
interface EnlaceDelPie {
  readonly segmentos: readonly string[];
  readonly clave: string;
}

/**
 * Primera columna: por dónde se anda el sitio.
 *
 * <p>El buzón de sugerencias entró aquí el 26 de septiembre de 2026, y no en la columna de ayuda,
 * que es donde a primera vista encajaría. La diferencia es real: los tres de "Ayuda" resuelven algo
 * que alguien necesita <b>ahora</b> —una duda, un pedido en camino, un problema con una compra— y
 * el buzón no resuelve nada de quien escribe. Puesto entre ellos, y sobre todo cerca de
 * "Contáctanos", invitaría a mandar por ahí un reclamo —que tiene plazo legal y no corre en el
 * buzón—, que es justo el error que la pantalla del buzón existe para evitar. Aquí se lee como lo
 * que es: una parte más del sitio.
 */
const ENLACES_DEL_SITIO: readonly EnlaceDelPie[] = [
  { segmentos: [], clave: 'pie.portada' },
  { segmentos: ['productos'], clave: 'encabezado.catalogo' },
  { segmentos: ['carrito'], clave: 'pie.carrito' },
  { segmentos: ['ayuda', 'sugerencias'], clave: 'pie.sugerencias' },
  // La única entrada al panel desde la vitrina. Apunta a `/admin` y no al formulario: con sesión de
  // ADMIN cae en el panel, y sin ella `adminGuard` redirige al ingreso anotando el destino. Va
  // siempre visible a propósito — el enlace del encabezado solo aparece cuando ya hay sesión, así
  // que no sirve para llegar a iniciarla. Sin este, al panel solo se llega tecleando la ruta.
  { segmentos: ['admin'], clave: 'pie.panel_admin' },
];

/** Segunda columna: dónde se resuelve una duda. */
const ENLACES_DE_AYUDA: readonly EnlaceDelPie[] = [
  { segmentos: ['ayuda', 'preguntas-frecuentes'], clave: 'pie.preguntas_frecuentes' },
  { segmentos: ['checkout', 'estado'], clave: 'pie.estado_pedido' },
  { segmentos: ['ayuda', 'contacto'], clave: 'pie.contactanos' },
];

/**
 * La franja de abajo: los tres documentos legales.
 *
 * <p>La ley pide que la política de datos esté publicada y enlazada en el pie
 * (`docs/08-seguridad-legal.md`), y quien la busca la busca como bloque. Estaban en una columna
 * propia con su título; desde el 25 de septiembre de 2026 van en la franja final junto al
 * copyright, que es donde los pone el pie de referencia y donde la gente ya los busca.
 */
const ENLACES_LEGALES: readonly EnlaceDelPie[] = [
  { segmentos: ['legales', 'terminos'], clave: 'pie.terminos' },
  { segmentos: ['legales', 'privacidad'], clave: 'pie.privacidad' },
  { segmentos: ['legales', 'cookies'], clave: 'pie.cookies' },
];

/**
 * Un medio de pago del pie: su etiqueta y, si lo tiene, el logo con que se dibuja.
 *
 * <p><b>El logo es opcional y hoy hay exactamente un caso sin él</b>, la contraentrega. Llegó a ser
 * obligatorio durante unas horas del 28 de septiembre de 2026 —ese día la contraentrega estrenó
 * símbolo propio y la tarjeta genérica se abrió en las tres franquicias de Wompi, así que no
 * quedaba ninguno pelado— y volvió a ser opcional el mismo día, cuando el símbolo de la
 * contraentrega se quitó a pedido.
 *
 * <p><b>Sin logo, el nombre se ve; con logo, no.</b> Eso ya no es un campo que haya que acordarse de
 * marcar: la plantilla lo deduce de si hay logo, y no puede desincronizarse. Hubo una bandera
 * `nombreALaVista` para lo mismo, y con ella eran dos datos que había que mantener de acuerdo
 * —poner el nombre a la vista junto a un logo que ya dice lo mismo es el ruido que se quitó, y
 * quitarlo sin logo deja un elemento de lista vacío—.
 *
 * <p>Los logos salen de `logos-pago.generado.ts`, que produce `npm run logos-pago`. El porqué de
 * que vayan en línea y no como archivos servidos está en `ts-logo-pago`.
 */
interface MedioDePago {
  readonly clave: string;

  /** Sin logo, el medio va como texto y su nombre se ve. Hoy solo la contraentrega. */
  readonly logo?: LogoPago;

  /**
   * Caja distinta de la de la fila —`h-24 w-64`—, cuando el dibujo no aguanta la de todos.
   *
   * <p><b>La caja compartida no iguala lo que se ve, iguala lo que se reserva.</b> Cada logo se
   * contiene dentro de ella, así que lo que acaba midiendo su dibujo depende de su relación de
   * aspecto: los apaisados llegan al ancho y se quedan cortos de alto, los cuadrados al revés. Sin
   * ninguna excepción la columna tenía a American Express, PSE, Nequi y BBVA pisando los 19-23 px
   * de alto —más que Visa, que es la franquicia que la gente busca primero— y a Sistecrédito en
   * 7,7. El tamaño lo estaba decidiendo el encuadre del archivo.
   *
   * <p><b>Lo que se baja es el alto, y el ancho casi nunca se toca</b>: nueve de los diez reservan
   * `w-64`, y esa columna de 64 px más el `gap-x-16` es lo único que sostiene las dos columnas del
   * `flex-wrap`, a 0 y a 80 px. El décimo es Bancolombia, que lo toca porque no le queda otra —ver
   * más abajo— y por eso es el único que rompe esa rejilla. Todo, alto y ancho, sale de un token:
   * nunca un píxel suelto (regla dura #2).
   *
   * <p>Las seis excepciones de hoy, todas del 28 de septiembre de 2026 y todas medidas en el
   * navegador, nunca a ojo:
   *
   * <ul>
   *   <li><b>American Express</b> y <b>PSE</b>, `h-16`. Eran los dos más altos de la columna
   *       —23,4 y 23,6 px de tinta— sin que eso lo hubiera decidido nadie: los dos son casi tan
   *       apaisados como la caja, así que la llenaban de lado a lado. Quedan en 43,8×16 y 40,3×16.
   *   <li><b>Nequi</b>, <b>BBVA</b> y <b>Addi</b>, `h-12`. Los tres se dibujaban entre 19 y 23 px de
   *       alto. Quedan en 38×12, 39,3×12 y 31,5×12.
   *   <li><b>Bancolombia</b>, `w-logo-pago`. El único que pide <b>ancho</b>, y el único que pide un
   *       token con nombre en vez de un escalón de la escala: ver abajo.
   * </ul>
   *
   * <h3>Bancolombia, y por qué su escalón está en el ancho</h3>
   *
   * <p>Llevaba `h-32 w-96` por la razón equivocada: en la caja común su letra medía 4,0 px contra
   * los 6,2 de Sistecrédito, y la culpa no era de la caja sino de su archivo, que declaraba un
   * lienzo de 217×61 con el logotipo metido en `22 19 174 23` —un tercio del alto en aire—.
   * Ceñido el `viewBox` en el generador, esa compensación sobra: **una caja a medida para tapar un
   * lienzo mal recortado es una excepción que no sabe lo que está arreglando.**
   *
   * <p>Lo que queda es un tamaño pedido a propósito, y va en `w-` y no en `h-` porque **en este
   * logo el `h-` no hace nada**. Ceñido, su relación es 174×23, o sea 7,6:1, la más apaisada de los
   * diez: dentro de una caja de 64 de ancho el dibujo topa con el ancho mucho antes que con el
   * alto, así que `h-24`, `h-32` y `h-48` dan los mismos 63,7×8,2 px. Medido en el navegador, los
   * tres.
   *
   * <p><b>Y va en un token con nombre porque en la escala no hay nada en medio.</b> De 64 el
   * siguiente escalón es 96 —y es el último—, que deja el logo en 95,6×12,3 px: demasiado. Los
   * 80 de `--ancho-logo-pago` lo dejan en 79,6×10,2. La otra salida era meter un 80 en
   * `espaciado_px`, y se descartó: rompía la progresión de la escala —de 32 en adelante va ×1,5—
   * y regalaba un `p-80` y un `gap-80` que nadie pidió. El precedente es `--ancho-menu-riel`, 72 px,
   * que tampoco está en la escala por lo mismo.
   *
   * <p><b>Es el único que rompe la columna de 64</b>, y cabe porque cae en la columna derecha: 80 +
   * 80 = 160 sobre los 214 px que mide la columna del pie en escritorio. Si algún día cambia el
   * orden de la lista y le toca la izquierda, empuja a su vecina; es un precio conocido, no un
   * descuido.
   */
  readonly clase?: string;
}

/**
 * Con qué se puede pagar, en el orden en que la gente los busca.
 *
 * <p><b>Es la lista del checkout, no una lista de deseos.</b> Sale de `MetodoPago` del backend
 * —tarjeta, PSE, Nequi, Bancolombia, Sistecrédito, transferencia manual y contraentrega— más Addi.
 *
 * <p><b>`TARJETA` se abre en las tres franquicias que Wompi acepta</b>: Visa, Mastercard y American
 * Express, nacionales e internacionales, según la documentación de Wompi y su centro de soporte,
 * consultados el 28 de septiembre de 2026. Diners Club no aparece en ninguna de las dos y por eso
 * no está. Antes había un icono genérico que decía "Tarjeta de crédito y débito" sin decir cuáles.
 * <b>Esta lista se queda vieja en silencio si se cambia de pasarela</b>, y publicar una franquicia
 * que el cobro rechaza es información engañosa igual que anunciar un medio que no existe.
 *
 * <p><b>Addi sigue aquí y ya no dice "próximamente" a la vista.</b> `MetodoPago` lo tuvo y lo quitó
 * a propósito (`V61__sin_addi.sql`), así que el checkout no lo ofrece; su nombre accesible sí
 * conserva el aviso, porque sale de la misma clave. Decisión tomada a sabiendas el 28 de septiembre
 * de 2026, con el riesgo de la Ley 1480 sobre la mesa.
 *
 * <p>Daviplata y BBVA aparecen como transferencia y no como botones de pago: son las cuentas a las
 * que se transfiere. <b>Hoy el checkout enseña una sola cuenta</b>, la que configuren
 * `TRANSFERENCIA_BANCO` y compañía, así que la etiqueta dice "transferencia" y no promete elegir
 * entre las tres; el día que el backend soporte varias cuentas, esto ya las nombra.
 */
const MEDIOS_DE_PAGO: readonly MedioDePago[] = [
  { clave: 'pie.pagos.visa', logo: logoVisa },
  { clave: 'pie.pagos.mastercard', logo: logoMastercard },
  { clave: 'pie.pagos.american_express', logo: logoAmericanExpress, clase: 'h-16' },
  { clave: 'pie.pagos.pse', logo: logoPse, clase: 'h-16' },
  { clave: 'pie.pagos.nequi', logo: logoNequi, clase: 'h-12' },
  { clave: 'pie.pagos.bancolombia', logo: logoBancolombia, clase: 'w-logo-pago' },
  { clave: 'pie.pagos.daviplata', logo: logoDaviplata },
  { clave: 'pie.pagos.bbva', logo: logoBbva, clase: 'h-12' },
  { clave: 'pie.pagos.sistecredito', logo: logoSistecredito },
  { clave: 'pie.pagos.addi', logo: logoAddi, clase: 'h-12' },
  // Sin logo: va solo con su nombre, y por eso es la única de la columna que se lee en vez de
  // reconocerse. Tuvo símbolo propio unas horas del 28 de septiembre de 2026 y se quitó a pedido.
  { clave: 'pie.pagos.contraentrega' },
];

/**
 * Franja grande de marca (`docs/04-ui-marca.md`): fondo `--color-marca` en los dos temas, nunca
 * ámbar — por eso solo lleva el logo negativo, sin el intercambio positivo/negativo que sí usa
 * `Encabezado`.
 *
 * <p>Datos legales de `docs/00-producto.md` (persona natural, sin sigla societaria —
 * `docs/08-seguridad-legal.md`).
 *
 * <h2>Cinco columnas y una franja final</h2>
 *
 * <p>El 25 de septiembre de 2026 dejó de ser un `flex-wrap` de bloques sueltos —logo, navegación,
 * legales, redes, dirección y una casilla— y pasó a la forma del pie de referencia de Preline
 * ("Footer with Newsletter Signup and Link Columns", sin el bloque de suscripción): columnas con
 * título, y abajo una franja con el copyright, los legales y el alternador de tema. Con `flex-wrap`
 * el orden de lectura dependía del ancho de la ventana, y a 1024 px la dirección se colaba entre
 * las redes y los legales.
 *
 * <p>Eran cuatro hasta el 26 de septiembre de 2026, cuando entró <b>Medios de pago</b>: con qué se
 * puede pagar es de las tres cosas que más se buscan en el pie de una tienda, y hasta hoy solo
 * estaba dicho dentro del checkout —o sea, después de decidir comprar— y en una pregunta
 * frecuente.
 *
 * <h2>Lo que se fue</h2>
 *
 * <p><b>La casilla de "Reducir movimiento"</b>, que se pidió quitar. Tiene una consecuencia que
 * conviene saber: quien <b>no</b> tenga la preferencia puesta en su sistema operativo se queda sin
 * forma de pedir menos movimiento desde el sitio. La regla de `prefers-reduced-motion` sigue
 * intacta en `tokens.css` y sigue apagando el carrusel, el brillo de carga y el anillo; lo que
 * desaparece es el interruptor propio. Los ganchos `[data-movimiento="reducido"]` de `styles.scss`
 * y de `tailwind.css` se quedan puestos: no cuestan nada y son lo que haría falta el día que el
 * control vuelva, quizá donde de verdad le toca, que es junto a los otros dos controles de
 * accesibilidad que `docs/04-ui-marca.md` todavía debe.
 *
 * <p><b>El enlace "Llamar"</b>. Ahora se enseña el número, que es el dato, y no un verbo que
 * escondía cuál era. El `tel:` vive en la página de contacto, que es a la que se llega para llamar.
 *
 * <p><b>WhatsApp del bloque de contacto</b>: sube a la columna de redes, con Facebook e Instagram.
 * Antes vivía abajo porque es una acción de atención y no un perfil que se sigue, y por eso es el
 * único de los tres sin `rel="me"`: `me` declara identidad, y un enlace de chat no la declara.
 */
@Component({
  selector: 'app-pie',
  imports: [TranslocoPipe, RouterLink, TsAlternadorTema, TsIcono, TsIconoMarca, TsLogoPago],
  templateUrl: './pie.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pie {
  protected readonly iconoTelefono = iconoTelefono;
  protected readonly iconoCorreo = iconoCorreo;
  protected readonly iconoUbicacion = iconoUbicacion;
  protected readonly iconoHorario = iconoHorario;
  protected readonly marcaFacebook = marcaFacebook;
  protected readonly marcaInstagram = marcaInstagram;
  protected readonly marcaWhatsapp = marcaWhatsapp;

  protected readonly mediosDePago = MEDIOS_DE_PAGO;

  protected readonly enlacesDelSitio = ENLACES_DEL_SITIO;
  protected readonly enlacesDeAyuda = ENLACES_DE_AYUDA;
  protected readonly enlacesLegales = ENLACES_LEGALES;

  protected readonly idioma = inject(TranslocoService).activeLang;
  protected readonly anioActual = new Date().getFullYear();

  /** `['/', 'es', 'ayuda', 'contacto']` a partir de los segmentos de un enlace. */
  protected rutaDe(enlace: EnlaceDelPie): unknown[] {
    return ['/', this.idioma(), ...enlace.segmentos];
  }
}
