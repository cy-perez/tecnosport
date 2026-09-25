import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
  viewChild,
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
import { MEDIA_HERO_VERTICAL } from '../../../../../core/imagenes/tamanos-de-imagen';
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
  readonly vertical: string;
}

/**
 * Cada cuánto pasa sola.
 *
 * <p>Fueron cinco segundos, que es lo que hace el carrusel de referencia; son cuatro desde el 25 de
 * septiembre de 2026, a petición del negocio. <b>El botón de pausa no se va con el segundo que se
 * quitó</b>: WCAG 2.2.2 habla de movimiento automático que <i>dure</i> más de cinco segundos, y el
 * de un carrusel que rota solo no termina nunca — lo que dura cuatro segundos es cada paso.
 */
const MS_AUTOPLAY = 4000;

/**
 * Cuánto hay que arrastrar para que el carrusel pase de pieza: la sexta parte del ancho visible.
 *
 * <p>Una fracción y no un número de píxeles, porque el gesto se hace con el pulgar sobre una pieza
 * que mide lo que mida la ventana: 60 px en un teléfono de 390 son un empujón deliberado, y en un
 * monitor de 2560 son un temblor de la mano.
 */
const FRACCION_ARRASTRE = 1 / 6;

/**
 * A partir de cuántos píxeles el arrastre decide su eje, y qué pasa mientras no lo ha decidido.
 *
 * <p>Sin esto, el carrusel se queda con cualquier gesto que empiece encima de él: alguien que baja
 * la página con el pulgar apoyado en la foto ve la diapositiva temblar de lado. `touch-action:
 * pan-y` le deja el eje vertical al navegador, pero el horizontal sigue siendo nuestro y hay que
 * devolverlo cuando quien mira estaba desplazando y no pasando de pieza.
 */
const PX_PARA_DECIDIR_EJE = 8;

/** Lo que frena el arrastre cuando no hay pieza que descubrir: se mueve, pero cuesta el triple. */
const FRENO_EN_EL_BORDE = 3;

/**
 * Las cuatro piezas, <b>en el orden de la portada</b>, que no es el orden canónico de `LINEAS`.
 *
 * <p>La diferencia es la misma que ya tenía la banda anterior y sigue siendo deliberada: quién
 * encabeza la portada lo decide el negocio, no el modelo. El menú lateral y el filtro siguen
 * ofreciendo las mismas cuatro en su orden.
 *
 * <p><b>Fotografía a sangre, y no el arte de estudio que hubo hasta el 25 de septiembre de 2026.</b>
 * Aquel lienzo grafito llevaba el producto recortado a la derecha y dejaba libre la mitad izquierda
 * para que el texto cayera sobre color plano; el de ahora es la fotografía entera, y el texto va
 * encima de ella. Lo que hacía el lienzo —garantizar el contraste— lo hace ahora un velo degradado,
 * y eso se mide: ver `docs/04-ui-marca.md`.
 *
 * <p><b>Un solo juego de arte para los dos temas</b>, que ya valía para el arte anterior y vale más
 * para una fotografía: no hay dos versiones que elegir. Servirlas costaría caro —un `<img>` con
 * `display:none` se descarga igual en Chrome, así que la pareja claro/oscuro duplicaría los bytes
 * del LCP de la portada— y elegir en tiempo de ejecución no se puede, porque el servidor no conoce
 * el tema mientras renderiza: `conTemaAplicado` inyecta `data-tema` por reemplazo de cadena sobre el
 * HTML ya construido, así que la primera pintura saldría en claro y saltaría al hidratar, justo
 * sobre la imagen más grande del sitio. El velo sí cambia de tema, porque sale de `--color-marca`.
 */
const DIAPOSITIVAS: readonly DiapositivaHero[] = [
  { clave: 'ropa', linea: 'ROPA', ancho: 'hero-ropa', vertical: 'vertical-ropa' },
  { clave: 'calzado', linea: 'CALZADO', ancho: 'hero-calzado', vertical: 'vertical-calzado' },
  { clave: 'bolsos', linea: 'BOLSOS', ancho: 'hero-bolsos', vertical: 'vertical-bolsos' },
  {
    clave: 'tecnologia',
    linea: 'TECNOLOGIA',
    ancho: 'hero-tecnologia',
    vertical: 'vertical-tecnologia',
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
 * aspecto: viñetas tipo píldora abajo al centro, la activa más ancha.
 *
 * <h2>Se pasa también con el dedo</h2>
 *
 * <p>Arrastrar de lado pasa a la siguiente pieza o a la anterior, y la tira <b>sigue el dedo</b>
 * mientras dura el gesto en vez de esperar a que se levante: un carrusel que no se mueve hasta que
 * sueltas no parece arrastrable, parece roto.
 *
 * <p>Tres cosas que no son gratis y que el visor 360 ya había pagado una vez —de ahí sale el
 * patrón—: `touch-action: pan-y` deja el desplazamiento vertical de la página al navegador, el
 * temporizador se apaga mientras el dedo está encima y se reprograma al soltar, y un arrastre que
 * termina sobre el botón de la diapositiva <b>no puede activarlo</b>. Ese último es el que muerde:
 * sin la guarda, empezar el gesto encima del botón pasaba de pieza y navegaba a la vez.
 *
 * <h2>El texto va en HTML, no en los píxeles</h2>
 *
 * <p>El titular, el apoyo y el botón los pone esta plantilla y nunca la imagen, que es lo que exige
 * la regla dura #4: texto dentro de una imagen no se traduce, no lo lee un lector de pantalla, no
 * escala y —en el caso del botón— parece pulsable sin serlo.
 *
 * <p>Sobre fotografía eso cuesta un velo. El arte de estudio anterior reservaba media pieza de
 * color plano y el contraste venía dado; una fotografía de calle tiene sol, cemento claro y, en dos
 * de las cuatro, fondo casi blanco. El velo degradado de `--color-marca` y el halo de las letras
 * están medidos sobre los píxeles del compuesto, no puestos a ojo: `docs/04-ui-marca.md` lleva las
 * cifras y el guion que las saca.
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
  protected readonly mediaVertical = MEDIA_HERO_VERTICAL;
  protected readonly iconoPausar = iconoPausar;
  protected readonly iconoReanudar = iconoReanudar;

  /** La ventana que recorta la tira. Recibe los gestos y es la que mide el ancho de una pieza. */
  private readonly ventana = viewChild.required<ElementRef<HTMLElement>>('ventana');

  protected readonly actual = signal(0);

  /**
   * Los píxeles que la tira lleva corridos por el dedo, cero cuando nadie la toca.
   *
   * <p>Es una señal y no una variable porque la plantilla la pinta en cada movimiento: el
   * `transform` de la tira es la posición de la diapositiva más esto.
   */
  protected readonly corrimiento = signal(0);

  /** Si hay un dedo (o un ratón) arrastrando ahora mismo. Apaga la transición: la tira va pegada. */
  protected readonly arrastrando = signal(false);

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

  /** Dónde empezó el gesto y cuánto medía la pieza entonces, para leer el arrastre en fracciones. */
  private inicioX = 0;
  private inicioY = 0;
  private anchoAlIniciar = 0;

  /**
   * El eje del gesto, que se decide en los primeros píxeles y ya no cambia.
   *
   * <p>`indeciso` es el estado de verdad y no un valor de relleno: hasta que el dedo no se mueve lo
   * bastante, nadie sabe si esto es pasar de pieza o desplazar la página, y adivinarlo en el primer
   * `pointermove` acierta la mitad de las veces.
   */
  private eje: 'indeciso' | 'horizontal' | 'vertical' = 'indeciso';

  /**
   * Si el gesto que acaba de terminar movió algo, para que el clic que viene detrás no cuente.
   *
   * <p>El navegador dispara un `click` al levantar el dedo aunque entre medias haya habido un
   * arrastre de media pantalla, y debajo del dedo suele haber un enlace: el botón de la diapositiva
   * ocupa un buen trozo de la pieza. Sin esta bandera, deslizar empezando encima del botón pasa de
   * pieza y además navega.
   */
  private huboArrastre = false;

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
      this.vigilarElClicDeUnArrastre();
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
   * Empieza el arrastre. Apaga el temporizador —no `detener()`, que es la pausa del puntero y la
   * reanudaría al salir— y toma la medida de la pieza, que es contra la que se compara el gesto.
   */
  protected iniciarArrastre(evento: PointerEvent): void {
    this.inicioX = evento.clientX;
    this.inicioY = evento.clientY;
    this.anchoAlIniciar = this.ventana().nativeElement.getBoundingClientRect().width;
    this.eje = 'indeciso';
    this.huboArrastre = false;
    this.arrastrando.set(true);
    this.apagar();
  }

  protected mover(evento: PointerEvent): void {
    if (!this.arrastrando()) {
      return;
    }
    const dx = evento.clientX - this.inicioX;
    const dy = evento.clientY - this.inicioY;

    if (this.eje === 'indeciso') {
      if (Math.max(Math.abs(dx), Math.abs(dy)) < PX_PARA_DECIDIR_EJE) {
        return;
      }
      this.eje = Math.abs(dx) > Math.abs(dy) ? 'horizontal' : 'vertical';
      if (this.eje === 'vertical') {
        // Quien empezó a desplazar la página no está pasando de pieza: se suelta el gesto entero y
        // el navegador sigue con lo suyo.
        //
        // Y **se vuelve a programar el temporizador**, que se apagó al empezar el gesto: sin esta
        // línea, bajar la página una vez con el pulgar apoyado en la fotografía dejaba el carrusel
        // quieto para siempre. El `pointerup` que viene detrás ya no entra —`arrastrando()` es
        // falso— así que este es el único sitio donde se puede encender de nuevo.
        this.soltar(evento);
        this.reprogramar();
        return;
      }
      // **La captura se toma aquí y no en el `pointerdown`, y esto costó encontrarlo.**
      //
      // Capturar el puntero redirige a este elemento todos los eventos que quedan de ese puntero,
      // y en Chrome eso incluye el `click`. Tomándola al empezar, un clic normal sobre el botón de
      // la diapositiva salía así —leído en el navegador, con los eventos registrados—:
      //
      //     pointerdown  destino=A      el clic empieza en el enlace
      //     pointerup    destino=DIV    lo desvía la captura
      //     click        destino=DIV    el <a> nunca se entera
      //
      // O sea que el botón principal de la portada dejaba de navegar con un clic. Aquí, con el eje
      // ya decidido, solo captura lo que de verdad es un arrastre — y el `click` que ese arrastre
      // deja detrás sí queremos que llegue al contenedor, que es donde lo descarta la guarda.
      //
      // jsdom no implementa la captura, así que **esto no lo atrapa ninguna prueba**: sin ella el
      // arrastre sigue funcionando mientras el dedo no se salga de la ventana, que es el caso de
      // una prueba. Por eso se llama de forma opcional y por eso está anotado en
      // `apps/web/CLAUDE.md`.
      this.ventana().nativeElement.setPointerCapture?.(evento.pointerId);
    }

    this.huboArrastre = true;
    this.corrimiento.set(this.conFrenoEnElBorde(dx));
  }

  /**
   * El gesto deja de ser nuestro: la tira vuelve a su sitio y <b>no se decide nada</b>.
   *
   * <p>Un `pointercancel` no es un dedo que se levanta, es el sistema diciendo que ese puntero ya
   * no cuenta —el navegador empezó un arrastre nativo, entró una llamada, el gesto pasó a ser un
   * zoom—. Tratarlo como un final tenía un defecto medido en el navegador: al arrastrar empezando
   * <b>encima del botón</b>, Chrome arranca el arrastre nativo de un enlace y manda `pointercancel`
   * con coordenadas que no son las del dedo, así que el carrusel saltaba a la pieza contraria a la
   * que pedía el gesto. Con esto, en el peor caso no pasa nada, que es lo correcto cuando no
   * sabemos qué pedía.
   */
  protected cancelarArrastre(evento: PointerEvent): void {
    if (!this.arrastrando()) {
      return;
    }
    this.soltar(evento);
    this.reprogramar();
  }

  /**
   * Levanta el dedo: si el gesto pasó del umbral, cambia de pieza; si no, la tira vuelve a su
   * sitio. En los dos casos la cuenta atrás arranca de cero, igual que al pulsar una viñeta.
   */
  protected terminarArrastre(evento: PointerEvent): void {
    if (!this.arrastrando()) {
      return;
    }
    const recorrido = evento.clientX - this.inicioX;
    this.soltar(evento);

    if (
      this.eje === 'horizontal' &&
      Math.abs(recorrido) > this.anchoAlIniciar * FRACCION_ARRASTRE
    ) {
      if (recorrido < 0) {
        this.siguiente();
      } else {
        this.anterior();
      }
    }
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
   * Las dos preferencias de menos movimiento que el sitio respeta: la del sistema operativo y el
   * atributo `data-movimiento` de `<html>`. Es el mismo par que `src/tailwind.css` apaga para el
   * brillo de carga; aquí no basta con acortar la animación, hay que no programar el temporizador.
   *
   * <p><b>Hoy nada del sitio escribe ese atributo</b>: la casilla del pie que lo ponía se quitó el
   * 25 de septiembre de 2026. La lectura se queda —igual que los ganchos de CSS— porque es lo que
   * haría falta el día que el control vuelva, y porque una prueba de este componente la ejercita
   * poniendo el atributo a mano. Si estás depurando por qué el carrusel no se detiene, mira
   * `prefers-reduced-motion` antes que esta rama.
   */
  private sistemaPideMenosMovimiento(): boolean {
    const delSistema = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    const delSitio = document.documentElement.getAttribute('data-movimiento') === 'reducido';
    return delSistema || delSitio;
  }

  /** Deja la tira quieta y suelta la captura del puntero. No decide nada: solo cierra el gesto. */
  private soltar(evento: PointerEvent): void {
    this.arrastrando.set(false);
    this.corrimiento.set(0);
    const ventana = this.ventana().nativeElement;
    if (ventana.hasPointerCapture?.(evento.pointerId)) {
      ventana.releasePointerCapture(evento.pointerId);
    }
  }

  /**
   * En los extremos de la tira no hay pieza que descubrir, así que el dedo arrastra un hueco.
   *
   * <p>Frenarlo a un tercio es lo que hace un carrusel nativo: se mueve lo justo para que el gesto
   * se sienta atendido, y no tanto como para enseñar una franja vacía. Al soltar sí da la vuelta
   * —`siguiente()` y `anterior()` son circulares—, y por eso esto es un freno y no un tope.
   */
  private conFrenoEnElBorde(dx: number): number {
    const enElBorde =
      (this.actual() === 0 && dx > 0) || (this.actual() === this.diapositivas.length - 1 && dx < 0);
    return enElBorde ? dx / FRENO_EN_EL_BORDE : dx;
  }

  /**
   * El clic que sigue a un arrastre no cuenta.
   *
   * <p>Va en fase de captura y con `addEventListener` a mano porque las dos cosas hacen falta: un
   * `(click)` de la plantilla escucha en fase de burbuja, o sea <b>después</b> de que el enlace de
   * la diapositiva haya hecho lo suyo, y Angular no sabe declarar un oyente de captura en una
   * plantilla. Para cuando nos llegara el evento, el router ya estaría navegando.
   */
  private vigilarElClicDeUnArrastre(): void {
    const ventana = this.ventana().nativeElement;
    const alHacerClic = (evento: Event): void => {
      if (!this.huboArrastre) {
        return;
      }
      this.huboArrastre = false;
      evento.preventDefault();
      evento.stopPropagation();
    };
    ventana.addEventListener('click', alHacerClic, true);
    this.destroyRef.onDestroy(() => ventana.removeEventListener('click', alHacerClic, true));
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
