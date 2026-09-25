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

/** Primera columna: por dónde se anda el sitio. */
const ENLACES_DEL_SITIO: readonly EnlaceDelPie[] = [
  { segmentos: [], clave: 'pie.portada' },
  { segmentos: ['productos'], clave: 'encabezado.catalogo' },
  { segmentos: ['carrito'], clave: 'pie.carrito' },
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
 * Franja grande de marca (`docs/04-ui-marca.md`): fondo `--color-marca` en los dos temas, nunca
 * ámbar — por eso solo lleva el logo negativo, sin el intercambio positivo/negativo que sí usa
 * `Encabezado`.
 *
 * <p>Datos legales de `docs/00-producto.md` (persona natural, sin sigla societaria —
 * `docs/08-seguridad-legal.md`).
 *
 * <h2>Cuatro columnas y una franja final</h2>
 *
 * <p>El 25 de septiembre de 2026 dejó de ser un `flex-wrap` de bloques sueltos —logo, navegación,
 * legales, redes, dirección y una casilla— y pasó a la forma del pie de referencia de Preline
 * ("Footer with Newsletter Signup and Link Columns", sin el bloque de suscripción): cuatro columnas
 * con título, y abajo una franja con el copyright, los legales y el alternador de tema. Con
 * `flex-wrap` el orden de lectura dependía del ancho de la ventana, y a 1024 px la dirección se
 * colaba entre las redes y los legales.
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
  imports: [TranslocoPipe, RouterLink, TsAlternadorTema, TsIcono, TsIconoMarca],
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
