import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  iconoContraentrega,
  iconoEnvio,
  iconoGarantia,
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
 * Lo que comparten los cuatro botones de línea. Lo único que cambia entre ellos es el fondo.
 *
 * Va en una constante y no repetido en la plantilla porque `anillo-foco-sobre-acento` es
 * obligatorio en los cuatro y cuatro copias de una lista de clases son cuatro sitios donde se
 * puede caer una. Es el mismo criterio de `ts-boton`, que compone sus clases en el componente.
 *
 * `hover:brightness-110` y no un token de hover por tono: aclarar un 10 % es la respuesta al ratón
 * que ya usa la variante `peligro` de `ts-boton`, y pedirle al kit cuatro `hover` más sería cuatro
 * tokens para un estado que ningún otro sitio consume. El botón de portada **no tenía ninguno**
 * hasta ahora.
 */
const CLASES_BOTON_LINEA =
  'anillo-foco-sobre-acento chaflan inline-flex min-h-tactil items-center justify-center ' +
  'px-32 font-medio text-ts-sobre-acento no-underline hover:brightness-110';

/**
 * Las cuatro líneas de negocio con su paso de la escala de ámbar, **en el orden de la banda**.
 *
 * No es el orden canónico de `LINEAS` —ahí tecnología va primera, por rotación— y la diferencia es
 * deliberada: en esta banda la primera posición y el tono más saturado van juntos, y quien decide
 * qué línea encabeza la portada es el negocio, no el modelo. El menú lateral y el filtro siguen
 * ofreciendo las mismas cuatro en su orden.
 *
 * El tono es un dato de esta lista y no un cálculo: `bg-ts-acento-2` tiene que aparecer escrito
 * tal cual en el código o Tailwind no lo genera —las clases se descubren leyendo el fuente, y una
 * armada con plantillas de cadena no existe—. `npm run clases` es lo que vigila que las cuatro
 * existan de verdad.
 */
const BOTONES_DE_LINEA: readonly { linea: Linea; clave: string; clases: string }[] = [
  { linea: 'ROPA', clave: 'portada.hero.cta.ropa', clases: CLASES_BOTON_LINEA + ' bg-ts-acento' },
  {
    linea: 'CALZADO',
    clave: 'portada.hero.cta.calzado',
    clases: CLASES_BOTON_LINEA + ' bg-ts-acento-2',
  },
  {
    linea: 'BOLSOS',
    clave: 'portada.hero.cta.bolsos',
    clases: CLASES_BOTON_LINEA + ' bg-ts-acento-3',
  },
  {
    linea: 'TECNOLOGIA',
    clave: 'portada.hero.cta.tecnologia',
    clases: CLASES_BOTON_LINEA + ' bg-ts-acento-4',
  },
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
  protected readonly variantesHero = VARIANTES_HERO;
  protected readonly tamanosHero = TAMANOS_HERO;

  protected readonly iconoEnvio = iconoEnvio;
  protected readonly iconoContraentrega = iconoContraentrega;
  protected readonly iconoGarantia = iconoGarantia;
}
