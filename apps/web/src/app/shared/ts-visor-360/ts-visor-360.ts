import { isPlatformBrowser, NgOptimizedImage } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  PLATFORM_ID,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsBoton } from '../ui/boton/ts-boton';
import { indiceCircular, indiceDesdeDesplazamiento, indiceOpuesto, ordenDePrecarga } from './rotacion-360';

/** `navigator.connection` no está en la librería DOM de TypeScript; solo lo que se lee de él. */
interface NavegadorConConexion extends Navigator {
  readonly connection?: {
    readonly saveData?: boolean;
    readonly effectiveType?: string;
  };
}

const CONEXIONES_LENTAS = ['slow-2g', '2g'];

/** Lo que dura la pista de "arrastra para girar" si nadie la toca. Breve, no permanente. */
const MS_PISTA = 4000;

/**
 * Para asociar las instrucciones al marco enfocable hace falta un `id`, y tiene que ser único
 * aunque haya dos visores en la misma página. El contador vale también con SSR: el servidor y el
 * cliente pueden llegar a números distintos, pero `[id]` y `[attr.aria-describedby]` salen del
 * mismo campo, así que siempre apuntan al mismo sitio.
 */
let secuenciaDeInstrucciones = 0;

/**
 * Visor de rotación 360 (`docs/10-captura-360.md`). Recibe un arreglo ordenado de URL y nada más:
 * no sabe de HTTP ni de productos. El orden es el del asistente de captura — antihorario visto
 * desde arriba, empezando por el frontal.
 *
 * La aritmética del giro vive en `rotacion-360.ts`, probada aparte.
 */
@Component({
  selector: 'ts-visor-360',
  imports: [NgOptimizedImage, TranslocoPipe, TsBoton],
  templateUrl: './ts-visor-360.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsVisor360 {
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly destruccion = inject(DestroyRef);

  readonly imagenes = input.required<readonly string[]>();

  private readonly marco = viewChild.required<ElementRef<HTMLElement>>('marco');

  protected readonly indiceActual = signal(0);
  protected readonly arrastrando = signal(false);
  protected readonly pistaVisible = signal(true);

  /**
   * Lo que ya está en la caché del navegador, **por URL y no por índice**. La diferencia importa
   * cuando el set cambia con una imagen todavía en vuelo: con índices, el `onload` tardío del
   * fotograma 3 del set anterior marcaba disponible el 3 del set nuevo, que nadie había pedido, y
   * el visor saltaba a una imagen sin cargar. Con URL, cada respuesta solo habla de sí misma.
   *
   * Por lo mismo no hace falta vaciarlas al cambiar de set: una URL que cargó sigue en la caché
   * del navegador, se esté mirando el set que se esté mirando.
   */
  private readonly cargadas = signal<ReadonlySet<string>>(new Set());
  private readonly solicitadas = new Set<string>();

  /** Corta la cadena de precarga del set anterior en cuanto empieza la del nuevo. */
  private generacionDePrecarga = 0;
  private paginaCargada = false;

  protected readonly total = computed(() => this.imagenes().length);

  /**
   * Con menos de dos fotogramas no hay rotación que mostrar y el componente no pinta nada: ni
   * botones que no llevan a ninguna parte ni un contador que diría "Fotograma 1 de 0" — el mismo
   * defecto que ya se corrigió en `ts-paginador`. La imagen suelta, si la hay, es trabajo de la
   * galería.
   *
   * El backend solo expone sets `PUBLICADO`, que exigen cuatro fotogramas, pero este componente es
   * compartido: el asistente de captura va a pasarle sets a medio armar.
   */
  protected readonly hayRotacion = computed(() => this.total() > 1);

  protected readonly idInstrucciones = `ts-visor-360-instrucciones-${++secuenciaDeInstrucciones}`;

  /**
   * El set, por su contenido y no por la identidad del arreglo. Quien nos pasa las imágenes suele
   * calcularlas con un `computed`, que devuelve un arreglo nuevo cada vez que cambia cualquier cosa
   * del producto — y una revalidación en segundo plano de TanStack cambia el precio o la existencia
   * cada minuto. Con la identidad, eso reiniciaba el visor al frontal mientras alguien lo estaba
   * girando; con el contenido, solo se reinicia cuando el set de verdad es otro.
   */
  private readonly claveDelSet = computed(() => this.imagenes().join('\n'));

  /**
   * Mientras la precarga va en camino, el arrastre no se bloquea: se muestra el fotograma más
   * cercano al deseado que ya esté disponible (`docs/10-captura-360.md`). `ordenDePrecarga` ya
   * devuelve los índices ordenados por cercanía, así que el primero disponible es el más cercano.
   */
  protected readonly indiceVisible = computed(() => {
    const deseado = this.indiceActual();
    const imagenes = this.imagenes();
    const disponibles = this.cargadas();
    // El fotograma 0 cuenta siempre como disponible: es el que pinta el SSR, el único con
    // `priority`, y el que el navegador ya está trayendo cuando el visor aparece.
    const disponible = (indice: number) => indice === 0 || disponibles.has(imagenes[indice] ?? '');

    if (disponible(deseado)) {
      return deseado;
    }
    return ordenDePrecarga(deseado, this.total()).find(disponible) ?? 0;
  });

  protected readonly urlVisible = computed(() => this.imagenes()[this.indiceVisible()] ?? '');

  private inicioX = 0;
  private indiceAlIniciar = 0;
  private anchoAlIniciar = 0;

  constructor() {
    // Un set nuevo (otro producto, otra variante) empieza por su frontal, y se precarga entero si
    // la página ya terminó de cargar. Se depende de `claveDelSet`, no de `imagenes`: ver arriba por
    // qué la identidad del arreglo no sirve como señal de "esto cambió".
    effect(() => {
      this.claveDelSet();
      this.indiceActual.set(0);
      if (this.esNavegador && this.paginaCargada) {
        untracked(() => this.precargar());
      }
    });

    // El fotograma al que llega el arrastre se pide aunque la precarga no haya corrido todavía
    // —o no vaya a correr nunca, con ahorro de datos activo.
    effect(() => {
      const url = this.imagenes()[this.indiceActual()];
      if (this.esNavegador && url) {
        this.pedirImagen(url);
      }
    });

    afterNextRender(() => {
      // "Después del evento de carga": el set completo no compite con el contenido principal.
      if (document.readyState === 'complete') {
        this.alTerminarDeCargarLaPagina();
      } else {
        const alCargar = () => this.alTerminarDeCargarLaPagina();
        window.addEventListener('load', alCargar, { once: true });
        // Una ficha que se abandona antes de que la página termine de cargar no tiene por qué
        // ponerse a pedir fotogramas de un visor que ya no existe.
        this.destruccion.onDestroy(() => window.removeEventListener('load', alCargar));
      }

      // La pista se va sola aunque nadie interactúe: un texto permanente encima de la imagen es
      // justo lo que `docs/10-captura-360.md` no quiere.
      const temporizador = setTimeout(() => this.ocultarPista(), MS_PISTA);
      this.destruccion.onDestroy(() => clearTimeout(temporizador));
    });
  }

  protected iniciarArrastre(evento: PointerEvent): void {
    if (this.total() <= 1) {
      return;
    }
    const marco = this.marco().nativeElement;
    this.anchoAlIniciar = marco.getBoundingClientRect().width;
    this.inicioX = evento.clientX;
    this.indiceAlIniciar = this.indiceActual();
    this.arrastrando.set(true);
    this.ocultarPista();
    // jsdom no implementa la captura de puntero, y sin ella el arrastre sigue funcionando dentro
    // del marco: por eso se llama de forma opcional en vez de darla por hecha.
    marco.setPointerCapture?.(evento.pointerId);
  }

  protected mover(evento: PointerEvent): void {
    if (!this.arrastrando()) {
      return;
    }
    // Solo el eje horizontal gira. El vertical lo maneja el navegador como desplazamiento de
    // página, gracias a `touch-action: pan-y`.
    this.indiceActual.set(
      indiceDesdeDesplazamiento(evento.clientX - this.inicioX, this.anchoAlIniciar, this.total(), this.indiceAlIniciar),
    );
  }

  protected terminarArrastre(evento: PointerEvent): void {
    if (!this.arrastrando()) {
      return;
    }
    this.arrastrando.set(false);
    const marco = this.marco().nativeElement;
    if (marco.hasPointerCapture?.(evento.pointerId)) {
      marco.releasePointerCapture(evento.pointerId);
    }
  }

  protected alTeclado(evento: KeyboardEvent): void {
    switch (evento.key) {
      case 'ArrowRight':
        this.girar(1);
        break;
      case 'ArrowLeft':
        this.girar(-1);
        break;
      case 'Home':
        this.irA(0);
        break;
      case 'End':
        this.irA(indiceOpuesto(this.total()));
        break;
      default:
        return;
    }
    // Solo se consume la tecla que el visor sí usa: Inicio y Fin desplazarían la página entera.
    evento.preventDefault();
  }

  protected girar(pasos: number): void {
    this.irA(indiceCircular(this.indiceActual() + pasos, this.total()));
  }

  private irA(indice: number): void {
    this.indiceActual.set(indice);
    this.ocultarPista();
  }

  private ocultarPista(): void {
    this.pistaVisible.set(false);
  }

  private alTerminarDeCargarLaPagina(): void {
    this.paginaCargada = true;
    this.precargar();
  }

  private precargar(): void {
    if (!this.precargaPermitida()) {
      return;
    }
    // El set de esta cadena, tomado una vez: si cambia a mitad de camino, esta cadena ya no es la
    // que manda y se abandona en el siguiente paso.
    const generacion = ++this.generacionDePrecarga;
    const imagenes = this.imagenes();
    const orden = ordenDePrecarga(this.indiceActual(), imagenes.length);

    // En cadena y no todas a la vez: ocho peticiones en paralelo compiten con lo que el visitante
    // está mirando.
    const siguiente = (posicion: number): void => {
      if (this.generacionDePrecarga !== generacion || posicion >= orden.length) {
        return;
      }
      this.pedirImagen(imagenes[orden[posicion]] ?? '', () => siguiente(posicion + 1));
    };
    siguiente(0);
  }

  /** Ahorro de datos o conexión lenta: no se precarga nada, se espera a que el visitante arrastre. */
  private precargaPermitida(): boolean {
    const conexion = (navigator as NavegadorConConexion).connection;
    if (!conexion) {
      return true;
    }
    return !conexion.saveData && !CONEXIONES_LENTAS.includes(conexion.effectiveType ?? '');
  }

  private pedirImagen(url: string, alTerminar?: () => void): void {
    if (!url || this.solicitadas.has(url)) {
      alTerminar?.();
      return;
    }
    this.solicitadas.add(url);

    const imagen = new Image();
    imagen.onload = () => {
      this.cargadas.update((cargadas) => new Set(cargadas).add(url));
      alTerminar?.();
    };
    // Una imagen que no llega no puede detener la cadena ni dejar el visor colgado: se sigue con el
    // resto y ese fotograma simplemente nunca se muestra — `indiceVisible` sustituye por el más
    // cercano disponible y el visitante ve el visor girar, con un fotograma menos, sin enterarse.
    //
    // Justamente por eso queda registrado. Un set `PUBLICADO` con un objeto roto en el bucket es un
    // defecto de datos que nadie va a notar mirando la ficha, y este es el único sitio del recorrido
    // donde se sabe que ocurrió: sin el aviso, el fotograma desaparece sin dejar rastro. No se
    // muestra nada en pantalla a propósito — qué decirle a quien está mirando un producto cuando
    // falta una foto es una decisión de producto, no una deuda de este componente.
    imagen.onerror = () => {
      const indice = this.imagenes().indexOf(url);
      console.warn(`[ts-visor-360] El fotograma ${indice + 1} de ${this.total()} no cargó y se omite: ${url}`);
      alTerminar?.();
    };
    imagen.src = url;
  }
}
