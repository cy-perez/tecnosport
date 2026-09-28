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
  logoContraentrega,
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
 * Un medio de pago del pie: su etiqueta y el logo con que se dibuja.
 *
 * <p><b>El logo ya no es opcional</b>, y eso es lo que queda de una simplificación. Hubo un segundo
 * campo, `icono`, para los dos que no tenían marca propia —la tarjeta genérica y la contraentrega—
 * con la regla de que uno de los dos estaba siempre en `null`. El 28 de septiembre de 2026 los dos
 * consiguieron dibujo propio: la contraentrega su símbolo, y la tarjeta se abrió en las tres
 * franquicias que Wompi acepta de verdad. Sin ningún caso que lo use, el campo era una rama muerta
 * y una invitación a volver a mezclar dos lenguajes gráficos en una lista.
 *
 * <p>Los logos salen de `logos-pago.generado.ts`, que produce `npm run logos-pago`. El porqué de
 * que vayan en línea y no como archivos servidos está en `ts-logo-pago`.
 */
interface MedioDePago {
  readonly clave: string;
  readonly logo: LogoPago;

  /**
   * Enseña el nombre al lado del logo en vez de dejarlo solo para el lector de pantalla. Lo lleva
   * únicamente la contraentrega, y va como dato y no como un caso en la plantilla porque es la
   * excepción de una regla, no una segunda regla.
   */
  readonly nombreALaVista?: boolean;

  /**
   * Caja distinta de la de la fila, cuando el dibujo no aguanta la de todos.
   *
   * <p><b>La caja compartida no iguala lo que se ve, iguala lo que se reserva.</b> Cada logo se
   * contiene dentro de ella, así que lo que acaba midiendo su dibujo depende de cuánto lienzo
   * vacío traiga su propio archivo y de qué tan grande esté puesto el logotipo dentro. Medido en el
   * navegador el 28 de septiembre de 2026, la altura de equis de la fila iba de 4,0 px
   * (Bancolombia) a 6,2 (Sistecrédito) sin que nadie lo hubiera decidido. De ahí las tres
   * excepciones, y las tres salen de una medición y no del ojo:
   *
   * <ul>
   *   <li><b>La contraentrega</b>, `h-32`. Es el único cuadrado —512×512, dos manos y una caja— y
   *       a la altura de la fila le queda una fracción del área de un logotipo ancho. Fue `h-48`
   *       hasta que pesó el doble que sus vecinos.
   *   <li><b>Bancolombia</b>, `h-32 w-96`. Su archivo pone el logotipo muy pequeño dentro de su
   *       propio lienzo —173 de 217 de ancho, 22 de 61 de alto— así que en la caja común su letra
   *       medía 4,0 px contra los 6,2 de Sistecrédito. <b>Hacen falta las dos medidas</b>: solo con
   *       `w-96` la caja pasa a ser más apaisada que el dibujo y el límite se muda al alto, que
   *       seguía en 24 — la letra se quedaba en 5,35. Con las dos, 6,02.
   *   <li><b>Addi</b>, `h-16`. Al revés que los anteriores: su archivo va ceñido, así que en la
   *       caja común se dibujaba a 22,6 px de alto contra los 19,1 de BBVA, y era el logo más
   *       grande de la columna sin ser el más importante. Un paso de la escala por debajo lo deja
   *       en 15,1.
   * </ul>
   *
   * <p>Va como dato y no como un caso en la plantilla porque son excepciones de una regla, no una
   * segunda regla.
   *
   * <p><b>Fue `h-48` hasta el 28 de septiembre de 2026 y bajó a `h-32`.</b> A 48 px pesaba el doble
   * que cualquier logotipo de la fila y se leía como un icono de otra familia más que como un medio
   * de pago entre once. A 32 queda a la par visual de Daviplata —que contenido en la caja se dibuja
   * a 24 de alto por unos 29 de ancho— sin caer en los 24 donde el símbolo se vuelve mancha.
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
  { clave: 'pie.pagos.american_express', logo: logoAmericanExpress },
  { clave: 'pie.pagos.pse', logo: logoPse },
  { clave: 'pie.pagos.nequi', logo: logoNequi },
  { clave: 'pie.pagos.bancolombia', logo: logoBancolombia, clase: 'h-32 w-96' },
  { clave: 'pie.pagos.daviplata', logo: logoDaviplata },
  { clave: 'pie.pagos.bbva', logo: logoBbva },
  { clave: 'pie.pagos.sistecredito', logo: logoSistecredito },
  { clave: 'pie.pagos.addi', logo: logoAddi, clase: 'h-16' },
  {
    clave: 'pie.pagos.contraentrega',
    logo: logoContraentrega,
    nombreALaVista: true,
    clase: 'h-32',
  },
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
