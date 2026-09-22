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

  protected readonly variantesHero = VARIANTES_HERO;
  protected readonly tamanosHero = TAMANOS_HERO;

  protected readonly iconoEnvio = iconoEnvio;
  protected readonly iconoContraentrega = iconoContraentrega;
  protected readonly iconoGarantia = iconoGarantia;
}
