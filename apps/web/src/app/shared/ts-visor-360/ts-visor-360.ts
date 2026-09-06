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
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsBoton } from '../ts-boton/ts-boton';
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
  styleUrl: './ts-visor-360.scss',
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
   * Fotogramas que ya están en la caché del navegador. El 0 se da por disponible: es el que sirve
   * el SSR y el único con `priority`, así que se pide siempre.
   */
  private readonly cargados = signal<ReadonlySet<number>>(new Set([0]));
  private readonly solicitados = new Set<number>();

  protected readonly total = computed(() => this.imagenes().length);

  /**
   * Mientras la precarga va en camino, el arrastre no se bloquea: se muestra el fotograma más
   * cercano al deseado que ya esté disponible (`docs/10-captura-360.md`). `ordenDePrecarga` ya
   * devuelve los índices ordenados por cercanía, así que el primero disponible es el más cercano.
   */
  protected readonly indiceVisible = computed(() => {
    const deseado = this.indiceActual();
    const disponibles = this.cargados();
    if (disponibles.has(deseado)) {
      return deseado;
    }
    return ordenDePrecarga(deseado, this.total()).find((indice) => disponibles.has(indice)) ?? 0;
  });

  protected readonly urlVisible = computed(() => this.imagenes()[this.indiceVisible()] ?? '');

  private inicioX = 0;
  private indiceAlIniciar = 0;
  private anchoAlIniciar = 0;

  constructor() {
    // Un set nuevo (otro producto, otra variante) empieza de cero: los índices cargados del
    // anterior no significan nada para este.
    effect(() => {
      this.imagenes();
      this.solicitados.clear();
      this.cargados.set(new Set([0]));
      this.indiceActual.set(0);
    });

    // El fotograma al que llega el arrastre se pide aunque la precarga no haya corrido todavía
    // —o no vaya a correr nunca, con ahorro de datos activo.
    effect(() => {
      const indice = this.indiceActual();
      if (this.esNavegador) {
        this.pedirFotograma(indice);
      }
    });

    afterNextRender(() => {
      // "Después del evento de carga": el set completo no compite con el contenido principal.
      if (document.readyState === 'complete') {
        this.precargar();
      } else {
        const alCargar = () => this.precargar();
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

  private precargar(): void {
    if (!this.precargaPermitida()) {
      return;
    }
    const orden = ordenDePrecarga(this.indiceActual(), this.total());
    // En cadena y no todos a la vez: ocho peticiones en paralelo compiten con lo que el visitante
    // está mirando.
    const siguiente = (posicion: number): void => {
      if (posicion < orden.length) {
        this.pedirFotograma(orden[posicion], () => siguiente(posicion + 1));
      }
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

  private pedirFotograma(indice: number, alTerminar?: () => void): void {
    const url = this.imagenes()[indice];
    if (!url || this.solicitados.has(indice)) {
      alTerminar?.();
      return;
    }
    this.solicitados.add(indice);

    const imagen = new Image();
    imagen.onload = () => {
      this.cargados.update((cargados) => new Set(cargados).add(indice));
      alTerminar?.();
    };
    // Un fotograma que no llega no puede detener la cadena ni dejar el visor colgado: se sigue con
    // el resto y ese índice simplemente nunca se muestra.
    imagen.onerror = () => alTerminar?.();
    imagen.src = url;
  }
}
