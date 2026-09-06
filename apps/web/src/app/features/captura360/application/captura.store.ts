import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { CAMARA, FotogramaCrudo } from '../domain/camara.puerto';
import { evaluarNivel, Inclinacion, Nivel, suavizar } from '../domain/nivel-360';
import { PANTALLA_DESPIERTA } from '../domain/pantalla-despierta.puerto';
import { SENSOR_ORIENTACION } from '../domain/sensor-orientacion.puerto';
import {
  EstadoPermiso,
  FOTOGRAMAS_RECOMENDADOS,
  FotogramaCapturado,
} from '../domain/sesion-captura.model';

/**
 * La sesión de captura en curso. Se provee en la ruta, no en la raíz: dura lo que dura la
 * pantalla, y al salir suelta la cámara, el sensor y el bloqueo de pantalla.
 *
 * **Lo capturado vive en memoria.** Persistir ocho fotos de dos megas no cabe en `localStorage`,
 * y meter IndexedDB por esto es desproporcionado: la protección real contra perder el trabajo es
 * subir cada fotograma apenas se acepta —eso llega con el paso de subida— porque además protege
 * de que se muera el teléfono, no solo de que se cierre la pestaña.
 */
@Injectable()
export class CapturaStore {
  private readonly camara = inject(CAMARA);
  private readonly sensor = inject(SENSOR_ORIENTACION);
  private readonly pantalla = inject(PANTALLA_DESPIERTA);

  private stream: MediaStream | null = null;
  private dejarDeEscuchar: (() => void) | null = null;
  private soltarPantalla: (() => void) | null = null;

  readonly permisoCamara = signal<EstadoPermiso>('DESCONOCIDO');
  readonly permisoSensor = signal<EstadoPermiso>('DESCONOCIDO');
  readonly abriendoCamara = signal(false);
  readonly fotogramasPrometidos = signal<number>(FOTOGRAMAS_RECOMENDADOS);
  readonly capturados = signal<readonly FotogramaCapturado[]>([]);
  readonly pendiente = signal<FotogramaCrudo | null>(null);
  readonly inclinacion = signal<Inclinacion | null>(null);

  /**
   * La referencia del nivel la fija la primera toma: el fotograma 0 decide la inclinación y los
   * demás tienen que igualarla. Pedirle al operador que calibre a ojo antes de empezar sería
   * pedirle que adivine.
   */
  readonly objetivo = signal<Inclinacion | null>(null);

  readonly siguienteOrden = computed(() => this.capturados().length);
  readonly termino = computed(() => this.siguienteOrden() >= this.fotogramasPrometidos());
  readonly fijandoReferencia = computed(() => this.objetivo() === null);

  /** El fotograma anterior, que la superposición pinta como fantasma para alinear contra él. */
  readonly fantasma = computed(() => {
    const capturados = this.capturados();
    return capturados.length === 0 ? null : capturados[capturados.length - 1].imagen.url;
  });

  readonly nivel = computed<Nivel>(() => {
    const inclinacion = this.inclinacion();
    const objetivo = this.objetivo();
    // Sin referencia todavía, cualquier inclinación está bien: esta toma es la que la fija.
    return evaluarNivel(inclinacion, objetivo ?? inclinacion ?? { beta: 0, gamma: 0 });
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.terminar());
  }

  async pedirCamara(): Promise<void> {
    if (!this.camara.disponible()) {
      this.permisoCamara.set('NO_DISPONIBLE');
      return;
    }

    this.abriendoCamara.set(true);
    try {
      this.stream = await this.camara.abrir();
      this.permisoCamara.set('CONCEDIDO');
      this.soltarPantalla = await this.pantalla.mantener();
    } catch {
      this.permisoCamara.set('NEGADO');
    } finally {
      this.abriendoCamara.set(false);
    }
  }

  async pedirSensor(): Promise<void> {
    if (!this.sensor.disponible()) {
      this.permisoSensor.set('NO_DISPONIBLE');
      return;
    }

    const concedido = await this.sensor.pedirPermiso();
    if (!concedido) {
      this.permisoSensor.set('NEGADO');
      return;
    }

    this.permisoSensor.set('CONCEDIDO');
    this.dejarDeEscuchar = this.sensor.escuchar((lectura) =>
      this.inclinacion.set(suavizar(this.inclinacion(), lectura)),
    );
  }

  streamActual(): MediaStream | null {
    return this.stream;
  }

  async capturar(video: HTMLVideoElement): Promise<void> {
    if (this.pendiente() !== null || this.termino()) {
      return;
    }
    this.pendiente.set(await this.camara.capturar(video));
  }

  /** Acepta la toma pendiente. La primera fija la referencia del nivel para todas las demás. */
  aceptar(): void {
    const imagen = this.pendiente();
    if (imagen === null) {
      return;
    }

    const inclinacion = this.inclinacion();
    if (this.objetivo() === null && inclinacion !== null) {
      this.objetivo.set(inclinacion);
    }

    this.capturados.update((capturados) => [
      ...capturados,
      { orden: capturados.length, imagen, inclinacion },
    ]);
    this.pendiente.set(null);
  }

  /** Repetir una toma no reinicia la secuencia (`docs/10-captura-360.md`, paso 4). */
  repetir(): void {
    const imagen = this.pendiente();
    if (imagen === null) {
      return;
    }
    this.camara.liberar(imagen);
    this.pendiente.set(null);
  }

  terminar(): void {
    if (this.stream !== null) {
      this.camara.cerrar(this.stream);
      this.stream = null;
    }
    this.dejarDeEscuchar?.();
    this.dejarDeEscuchar = null;
    this.soltarPantalla?.();
    this.soltarPantalla = null;

    const pendiente = this.pendiente();
    if (pendiente !== null) {
      this.camara.liberar(pendiente);
      this.pendiente.set(null);
    }
    for (const capturado of this.capturados()) {
      this.camara.liberar(capturado.imagen);
    }
    this.capturados.set([]);
  }
}
