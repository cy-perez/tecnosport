import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { IconNode } from 'lucide';
import {
  iconoContraentrega,
  iconoEnvio,
  iconoGarantia,
  iconoMediosDePago,
  iconoPausar,
  iconoReanudar,
} from '../../../../../shared/ui/icono/iconos';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsIcono } from '../../../../../shared/ui/icono/ts-icono';
import { MEDIA_HERO_TARJETA } from '../../../../../core/imagenes/tamanos-de-imagen';
import { Linea } from '../../../domain/filtro-productos.model';

/**
 * Una diapositiva: la línea de negocio a la que lleva y el nombre de sus dos archivos.
 *
 * <p>El texto no vive aquí. Vive en `portada.hero.slides.<clave>.*` de Transloco, porque es texto
 * visible y la regla dura #4 no admite una cadena en el código. Lo que sí es dato del componente es
 * <b>qué</b> se ofrece y <b>adónde</b> lleva.
 */
interface DiapositivaHero {
  readonly clave: string;
  readonly linea: Linea;
  readonly ancho: string;
  readonly tarjeta: string;
}

/** Cada cuánto pasa sola. Cinco segundos es lo que hace el carrusel de referencia. */
const MS_AUTOPLAY = 5000;

/**
 * Las cuatro piezas, <b>en el orden de la portada</b>, que no es el orden canónico de `LINEAS`.
 *
 * <p>La diferencia es la misma que ya tenía la banda anterior y sigue siendo deliberada: quién
 * encabeza la portada lo decide el negocio, no el modelo. El menú lateral y el filtro siguen
 * ofreciendo las mismas cuatro en su orden.
 *
 * <p><b>Un solo juego de arte para los dos temas</b>, y esto se apartó de lo que el ZIP entrega.
 * `hero-tecnosport/` trae `claro/` y `oscuro/`, y la única diferencia entre las dos carpetas es el
 * color del fondo del lienzo: `#1B1F26` contra `#191E26`, dos unidades de rojo y una de verde. Es
 * invisible, y servir las dos costaba caro de verdad: un `<img>` con `display:none` se descarga
 * igual en Chrome, así que la pareja claro/oscuro duplicaba los bytes del LCP de la portada; y
 * elegir en tiempo de ejecución no se puede, porque el servidor no conoce el tema mientras
 * renderiza —`conTemaAplicado` inyecta `data-tema` por reemplazo de cadena sobre el HTML ya
 * construido— así que la primera pintura saldría en claro y saltaría al hidratar, justo sobre la
 * imagen más grande del sitio. La franja que rodea al arte sí cambia de tema: es `bg-ts-marca`.
 */
const DIAPOSITIVAS: readonly DiapositivaHero[] = [
  { clave: 'ropa', linea: 'ROPA', ancho: 'hero-ropa', tarjeta: 'tarjeta-ropa' },
  { clave: 'calzado', linea: 'CALZADO', ancho: 'hero-calzado', tarjeta: 'tarjeta-calzado' },
  { clave: 'bolsos', linea: 'BOLSOS', ancho: 'hero-bolsos', tarjeta: 'tarjeta-bolsos' },
  {
    clave: 'tecnologia',
    linea: 'TECNOLOGIA',
    ancho: 'hero-tecnologia',
    tarjeta: 'tarjeta-tecnologia',
  },
];

/**
 * Los cuatro sellos de confianza: lo que el sitio sí puede prometer.
 *
 * <p>Vienen tal cual de la banda anterior. Van como dato y no escritos cuatro veces en la
 * plantilla porque cuatro copias de `flex items-start gap-8` con su `size-16` son cuatro sitios
 * donde se puede desalinear uno.
 */
const SELLOS_DE_CONFIANZA: readonly { clave: string; icono: IconNode }[] = [
  { clave: 'portada.hero.confianza.envio', icono: iconoEnvio },
  { clave: 'portada.hero.confianza.contraentrega', icono: iconoContraentrega },
  { clave: 'portada.hero.confianza.pago', icono: iconoMediosDePago },
  { clave: 'portada.hero.confianza.garantia', icono: iconoGarantia },
];

/**
 * El carrusel de portada: cuatro piezas a todo el ancho de la ventana, una por línea de negocio.
 *
 * <p>Sustituye a `TsHero`, que era una sola fotografía dentro de la rejilla de 1200 px. Lo que
 * cambia no es el adorno: la banda ahora <b>sangra de borde a borde</b> —el referente es
 * gotrendier.com.co— y ofrece las cuatro líneas una detrás de otra en vez de cuatro botones
 * apretados bajo el mismo titular.
 *
 * <h2>Sin Swiper</h2>
 *
 * <p>El carrusel "With indicators" de TailAdmin que sirve de referencia visual está montado sobre
 * Swiper. No entra: cada librería nueva es deuda, y lo que hace falta de ella —cuatro diapositivas,
 * unas viñetas y un temporizador— son las cincuenta líneas de abajo. Lo que sí se copia es el
 * aspecto: viñetas tipo píldora abajo al centro, la activa más ancha, y el paso solo cada cinco
 * segundos.
 *
 * <h2>El texto va en HTML, no en los píxeles</h2>
 *
 * <p>El ZIP entrega cada pieza en dos versiones, `con-texto/` y `limpio/`. Se usa `limpio/` y el
 * titular, el apoyo y el botón los pone esta plantilla, que es lo que su propio `LEEME.md`
 * recomienda y lo que la regla dura #4 exige: texto dentro de una imagen no se traduce, no lo lee
 * un lector de pantalla, no escala y —en el caso del botón— parece pulsable sin serlo. Es la misma
 * decisión que ya se había tomado con la fotografía anterior.
 *
 * <h2>Accesibilidad</h2>
 *
 * <p>Patrón de carrusel de la APG: `aria-roledescription="carousel"` en la región, un `group` por
 * diapositiva con su «N de 4», y las viñetas como botones de verdad con `aria-current`. El
 * movimiento automático se detiene con el puntero encima, con el foco dentro y cuando quien mira
 * pidió menos movimiento —ahí no arranca siquiera—, que es lo que la APG exige de un carrusel que
 * rota solo.
 */
@Component({
  selector: 'ts-carrusel-hero',
  imports: [TranslocoPipe, TsBoton, TsIcono],
  templateUrl: './ts-carrusel-hero.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsCarruselHero {
  /** El prefijo de idioma de la ruta actual: las rutas del sitio lo llevan siempre. */
  readonly idioma = input.required<string>();

  protected readonly diapositivas = DIAPOSITIVAS;
  protected readonly sellosDeConfianza = SELLOS_DE_CONFIANZA;
  protected readonly mediaTarjeta = MEDIA_HERO_TARJETA;
  protected readonly iconoPausar = iconoPausar;
  protected readonly iconoReanudar = iconoReanudar;

  protected readonly actual = signal(0);

  /**
   * Si el temporizador puede correr. Arranca en `false` y solo lo enciende `afterNextRender`: en el
   * servidor no hay a quién animarle nada, y un `setInterval` durante el SSR deja viva una tarea
   * que Angular espera antes de serializar.
   */
  private readonly puedeRotar = signal(false);

  /** Mientras el puntero está encima o el foco dentro, el carrusel no se mueve. */
  private readonly detenido = signal(false);

  /**
   * La pausa que pidió la persona con el botón, que es <b>una señal aparte</b> de la del puntero.
   *
   * <p>No es duplicación: con un solo booleano para las dos fuentes, sacar el ratón de la región
   * llamaba a `reanudar()` y arrancaba de nuevo lo que alguien acababa de pausar a propósito. Esta
   * gana siempre; la del puntero solo puede detener, nunca reanudar contra ella.
   *
   * <p>Existe porque WCAG 2.2.2 (nivel A) exige un mecanismo para pausar, detener u ocultar todo
   * movimiento automático que dure más de cinco segundos. Detenerse con el puntero encima y con el
   * foco dentro <b>no es ese mecanismo</b>: en un teléfono no hay puntero —y tocar una viñeta
   * reinicia la cuenta en vez de detenerla— y con teclado no es descubrible. Lo levantó la
   * auditoría de accesibilidad; el componente y `docs/04-ui-marca.md` describían lo anterior como
   * si bastara.
   */
  protected readonly pausadoPorLaPersona = signal(false);

  private readonly destroyRef = inject(DestroyRef);

  private temporizador: ReturnType<typeof setInterval> | null = null;

  /**
   * `aria-live` vale `polite` solo cuando el carrusel está quieto.
   *
   * <p>Es lo que pide la APG y no es un detalle: si anunciara los cambios mientras rota solo,
   * interrumpiría la lectura de la página cada cinco segundos. Cuando alguien detiene la rotación
   * —pasando el puntero, o entrando con el tabulador— el cambio sí lo produjo esa persona y sí
   * quiere oírlo.
   */
  protected readonly cortesia = computed(() => (this.rotando() ? 'off' : 'polite'));

  protected readonly rotando = computed(
    () => this.puedeRotar() && !this.detenido() && !this.pausadoPorLaPersona(),
  );

  constructor() {
    afterNextRender(() => {
      if (this.sistemaPideMenosMovimiento()) {
        return;
      }
      this.puedeRotar.set(true);
      this.reprogramar();
    });
    this.destroyRef.onDestroy(() => this.apagar());
  }

  protected irA(indice: number): void {
    this.actual.set(indice);
    this.reprogramar();
  }

  protected siguiente(): void {
    this.actual.update((indice) => (indice + 1) % this.diapositivas.length);
  }

  protected anterior(): void {
    this.actual.update(
      (indice) => (indice - 1 + this.diapositivas.length) % this.diapositivas.length,
    );
  }

  protected detener(): void {
    this.detenido.set(true);
    this.apagar();
  }

  protected reanudar(): void {
    this.detenido.set(false);
    this.reprogramar();
  }

  /**
   * El botón de pausa. `reprogramar()` no arranca nada si `rotando()` es falso, así que pausar
   * apaga y reanudar vuelve a programar sin que haya que distinguir los dos casos aquí.
   */
  protected alternarPausa(): void {
    this.pausadoPorLaPersona.update((pausado) => !pausado);
    this.reprogramar();
  }

  /**
   * Las flechas mueven el carrusel cuando el foco está en la tira de viñetas.
   *
   * <p>Va en la tira y no en la región entera a propósito: dentro de una diapositiva hay un enlace,
   * y secuestrar las flechas ahí le quitaría a quien navega con teclado el desplazamiento normal de
   * la página.
   */
  protected alPulsarTecla(evento: KeyboardEvent): void {
    if (evento.key === 'ArrowRight') {
      this.siguiente();
    } else if (evento.key === 'ArrowLeft') {
      this.anterior();
    } else {
      return;
    }
    evento.preventDefault();
    this.reprogramar();
  }

  /**
   * La pieza que se muestra primero es la única con `fetchpriority="high"`; las otras tres van
   * `lazy`. Priorizar cuatro imágenes es no priorizar ninguna (apps/web/CLAUDE.md, NG02955).
   */
  protected esPrimera(indice: number): boolean {
    return indice === 0;
  }

  /** La clave de i18n de una diapositiva, para no repetir el prefijo cuatro veces por plantilla. */
  protected claveDe(diapositiva: DiapositivaHero, campo: string): string {
    return `portada.hero.slides.${diapositiva.clave}.${campo}`;
  }

  /**
   * Las dos preferencias de menos movimiento que el sitio respeta: la del sistema operativo y la
   * que el propio sitio escribió en `<html>`. Es el mismo par que `src/tailwind.css` apaga para el
   * brillo de carga; aquí no basta con acortar la animación, hay que no programar el temporizador.
   */
  private sistemaPideMenosMovimiento(): boolean {
    const delSistema = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    const delSitio = document.documentElement.getAttribute('data-movimiento') === 'reducido';
    return delSistema || delSitio;
  }

  /**
   * Reinicia la cuenta atrás. Se llama también al cambiar de diapositiva a mano, y ese es el punto:
   * sin reprogramar, pulsar la cuarta viñeta justo antes de que salte dejaba medio segundo de
   * lectura antes de que el carrusel se fuera solo a la siguiente.
   */
  private reprogramar(): void {
    this.apagar();
    if (!this.rotando()) {
      return;
    }
    this.temporizador = setInterval(() => this.siguiente(), MS_AUTOPLAY);
  }

  private apagar(): void {
    if (this.temporizador !== null) {
      clearInterval(this.temporizador);
      this.temporizador = null;
    }
  }
}
