import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import type { IconNode } from 'lucide';
import {
  iconoContraentrega,
  iconoEnvio,
  iconoGarantia,
  iconoMediosDePago,
} from '../../../../../shared/ui/icono/iconos';
import { TsIcono } from '../../../../../shared/ui/icono/ts-icono';
import { TAMANOS_HERO } from '../../../../../core/imagenes/tamanos-de-imagen';
import { Linea } from '../../../domain/filtro-productos.model';
import { VarianteDeImagen } from '../../../domain/producto.model';

/**
 * Los anchos que existen en `public/imagenes/portada/`, como dato y no como patrón.
 *
 * `cargadorDeImagenes` resuelve cada ancho del `srcset` buscándolo aquí, que es el mismo mecanismo
 * de las imágenes de producto —solo que las de producto las manda la API y estas son archivos del
 * repositorio—. Sin esto el hero no puede tener `srcset`: el cargador devuelve el `src` tal cual
 * cuando no le llegan variantes, y las siete entradas del `srcset` serían el mismo archivo.
 */
const VARIANTES_HERO: readonly VarianteDeImagen[] = [
  { ancho: 480, url: '/imagenes/portada/hero-480.webp' },
  { ancho: 800, url: '/imagenes/portada/hero-800.webp' },
  { ancho: 1200, url: '/imagenes/portada/hero-1200.webp' },
];

/**
 * Las clases de los cuatro botones de línea, que hoy son **las mismas para los cuatro**.
 *
 * <p>Hasta el 24 de septiembre de 2026 cada uno llevaba su propio paso de la escala de ámbar
 * —`bg-ts-acento`, `-2`, `-3` y `-4`— y esa era la única excepción admitida a la regla de "una
 * sola cosa por pantalla" de `docs/04-ui-marca.md`. Se retiró: los cuatro van en el ámbar de marca
 * y del mismo tamaño, así que la regla vuelve a no tener excepciones (`ADR-0063`, sustituido por
 * `ADR-0064`). Con cuatro tonos, el más claro parecía un estado deshabilitado del primero, y
 * "Ver tecnología" —la línea que el negocio quiere delante— era el que menos pesaba de los cuatro.
 *
 * <p>Sigue siendo una constante y no texto repetido en la plantilla por lo de siempre:
 * `anillo-foco-sobre-acento` es obligatorio en los cuatro y cuatro copias de una lista de clases
 * son cuatro sitios donde se puede caer una. Es el mismo criterio de `ts-boton`.
 *
 * <p>`px-24` y no `px-32`: el ancho ya no lo pone el relleno sino la columna de la rejilla, que es
 * lo que iguala los cuatro. Con `px-32`, "Ver calzado deportivo" —el más largo de los cuatro—
 * fijaba una columna innecesariamente ancha y los otros tres se quedaban con el texto nadando en
 * el centro.
 *
 * <p>`hover:brightness-110` y no un token de hover: aclarar un 10 % es la respuesta al ratón que ya
 * usa la variante `peligro` de `ts-boton`, y pedirle al kit un `hover` más sería un token para un
 * estado que ningún otro sitio consume.
 */
const CLASES_BOTON_LINEA =
  'anillo-foco-sobre-acento chaflan inline-flex min-h-tactil items-center justify-center ' +
  'bg-ts-acento px-24 text-center font-medio text-ts-sobre-acento no-underline ' +
  'hover:brightness-110';

/**
 * Las cuatro líneas de negocio, **en el orden de la banda**.
 *
 * <p>No es el orden canónico de `LINEAS` —ahí tecnología va primera, por rotación— y la diferencia
 * es deliberada: quien decide qué línea encabeza la portada es el negocio, no el modelo. El menú
 * lateral y el filtro siguen ofreciendo las mismas cuatro en su orden.
 */
const BOTONES_DE_LINEA: readonly { linea: Linea; clave: string }[] = [
  { linea: 'ROPA', clave: 'portada.hero.cta.ropa' },
  { linea: 'CALZADO', clave: 'portada.hero.cta.calzado' },
  { linea: 'BOLSOS', clave: 'portada.hero.cta.bolsos' },
  { linea: 'TECNOLOGIA', clave: 'portada.hero.cta.tecnologia' },
];

/**
 * Los cuatro sellos de la tira de confianza: lo que el sitio sí puede prometer.
 *
 * <p>Eran tres hasta el 24 de septiembre de 2026 y entró "Diversas opciones de pago", que es lo
 * único de los cuatro que habla del *cómo se paga* y no del *cómo llega*. Van como dato y no
 * escritos cuatro veces en la plantilla por lo mismo que los botones: cuatro copias de
 * `flex items-start gap-8` con su `size-16` son cuatro sitios donde se puede desalinear uno.
 */
const SELLOS_DE_CONFIANZA: readonly { clave: string; icono: IconNode }[] = [
  { clave: 'portada.hero.confianza.envio', icono: iconoEnvio },
  { clave: 'portada.hero.confianza.contraentrega', icono: iconoContraentrega },
  { clave: 'portada.hero.confianza.pago', icono: iconoMediosDePago },
  { clave: 'portada.hero.confianza.garantia', icono: iconoGarantia },
];

/**
 * La banda de portada: lo primero que ve quien llega.
 *
 * <p>Sale del kit que entregó diseño el 18 de septiembre de 2026, con tres diferencias que conviene
 * dejar escritas porque no son gusto:
 *
 * <ul>
 *   <li><b>Tailwind y no el `.scss` del kit</b> (`ADR-0020`). El SCSS que venía llevaba treinta y
 *       tantos literales —`13px`, `52px`, `clamp(40px, 5.4vw, 72px)`, `rgb(255 255 255 / 62%)`— que
 *       la regla dura #2 no admite. Lo que de verdad hacía falta son tres longitudes, y esas
 *       entraron al kit como tokens: `--hero-corte`, `--hero-alto-min` y `--chaflan-hero`.</li>
 *   <li><b>Sin la trama de líneas ni el resplandor.</b> Las dos capas decorativas están hechas
 *       enteras de valores literales —franjas de 13 y 14 píxeles, blancos al 2.8 %— y reproducirlas
 *       cumpliendo la regla habría pedido cuatro tokens más para una textura que el propio kit
 *       describe como "si se nota, está mal". Si se echan de menos, entran con sus tokens.</li>
 *   <li><b>Sin la etiqueta de "Desde $189.900".</b> Era un precio escrito en la plantilla: envejece
 *       solo y no hay ningún endpoint que dé el mínimo del catálogo, así que la alternativa honesta
 *       era calcularlo sobre las novedades ya cargadas, que no es "desde" de nada.</li>
 * </ul>
 *
 * <p>La banda va en `--color-marca` y no en `--color-primario`, y la diferencia solo se ve en tema
 * oscuro: ahí `primario` <b>es el ámbar</b>, así que el fondo entero se teñía de ámbar y el botón
 * de acento desaparecía dentro de él. Es la regla de "una sola cosa por pantalla" de
 * `docs/04-ui-marca.md`, y el pie ya usaba el par correcto —`bg-ts-marca` con `text-ts-sobre-marca`,
 * que es para lo que existe `anillo-foco-sobre-marca`—. Encontrado mirándolo en el navegador: las
 * pruebas no lo habrían dicho.
 *
 * <p>La imagen también se apartó de lo entregado, y esa fue decisión del negocio: el archivo era un
 * banner terminado con el titular, el subtítulo y un botón "COMPRAR AHORA" incrustados en los
 * píxeles. Texto dentro de una imagen no se traduce, no lo lee un lector de pantalla y no escala; el
 * botón, además, parecía pulsable sin serlo. Se recortó la fotografía y el texto lo pone el HTML.
 */
@Component({
  selector: 'ts-hero',
  standalone: true,
  imports: [NgOptimizedImage, RouterLink, TranslocoPipe, TsIcono],
  templateUrl: './ts-hero.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsHero {
  /** El prefijo de idioma de la ruta actual: las rutas del sitio lo llevan siempre. */
  readonly idioma = input.required<string>();

  protected readonly botonesDeLinea = BOTONES_DE_LINEA;
  protected readonly clasesBotonLinea = CLASES_BOTON_LINEA;
  protected readonly sellosDeConfianza = SELLOS_DE_CONFIANZA;
  protected readonly variantesHero = VARIANTES_HERO;
  protected readonly tamanosHero = TAMANOS_HERO;
}
