import { computed, DestroyRef, inject, Injectable, linkedSignal, signal } from '@angular/core';
import { sha256Hex } from '../../../core/hash/sha256';
import { ErrorHttp } from '../../../core/http/respuesta-http';
import { ALMACEN_LOCAL_DE_CAPTURAS } from '../domain/almacen-local-capturas.puerto';
import { CAMARA, FotogramaCrudo } from '../domain/camara.puerto';
import { PROCESADOR_DE_FOTOGRAMAS } from '../domain/procesador-fotogramas.puerto';
import {
  REPOSITORIO_SETS_ROTACION,
  SetRotacionAdmin,
} from '../domain/repositorio-sets-rotacion.puerto';
import { LADO_SALIDA_PX } from '../domain/recorte-360';
import { procesarSet } from './procesar-set';
import {
  ANUNCIO_EN_BLANCO,
  AnuncioDeNivel,
  asentarAnuncio,
  claveDeNivel,
  evaluarNivel,
  Gravedad,
  Nivel,
  suavizar,
} from '../domain/nivel-360';
import { PANTALLA_DESPIERTA } from '../domain/pantalla-despierta.puerto';
import { SENSOR_ORIENTACION } from '../domain/sensor-orientacion.puerto';
import { SesionGuardada } from '../domain/almacen-local-capturas.puerto';
import {
  EstadoPermiso,
  FOTOGRAMAS_RECOMENDADOS,
  FotogramaCapturado,
} from '../domain/sesion-captura.model';

/**
 * La sesión de captura en curso. Se provee en la ruta, no en la raíz: dura lo que dura la
 * pantalla, y al salir suelta la cámara, el sensor y el bloqueo de pantalla.
 *
 * **Cada toma aceptada se guarda en disco del navegador** en cuanto se acepta: cerrar la pestaña
 * por accidente no puede costar quince fotos (`docs/10-captura-360.md`). Subirlas sobre la marcha
 * no habría servido — el factor de escala es común a todo el set y sale del rectángulo más grande
 * de todos los fotogramas, así que hasta que no está la última toma no se puede procesar ninguna.
 *
 * El id de la sesión es local: el set del backend se abre al subir, así que capturar funciona sin
 * red.
 */
@Injectable()
export class CapturaStore {
  private readonly camara = inject(CAMARA);
  private readonly almacenLocal = inject(ALMACEN_LOCAL_DE_CAPTURAS);
  private readonly procesador = inject(PROCESADOR_DE_FOTOGRAMAS);
  private readonly repositorio = inject(REPOSITORIO_SETS_ROTACION);
  private readonly sensor = inject(SENSOR_ORIENTACION);
  private readonly pantalla = inject(PANTALLA_DESPIERTA);

  private stream: MediaStream | null = null;
  private dejarDeEscuchar: (() => void) | null = null;
  private soltarPantalla: (() => void) | null = null;

  readonly productoId = signal('');
  readonly sesionId = signal('');
  readonly permisoCamara = signal<EstadoPermiso>('DESCONOCIDO');
  readonly permisoSensor = signal<EstadoPermiso>('DESCONOCIDO');
  readonly abriendoCamara = signal(false);
  readonly fotogramasPrometidos = signal<number>(FOTOGRAMAS_RECOMENDADOS);
  readonly capturados = signal<readonly FotogramaCapturado[]>([]);
  readonly pendiente = signal<FotogramaCrudo | null>(null);
  /**
   * Hacia dónde cae la gravedad, ya suavizada. **Se guarda el vector y no los ángulos del
   * sensor**: cerca de la vertical —la pose de trabajo del asistente— `gamma` se dispara sin que
   * el teléfono se mueva, y suavizar eso es promediar ruido. Ver `nivel-360.ts`.
   */
  readonly gravedadActual = signal<Gravedad | null>(null);

  /** Una captura a medias de este mismo producto, encontrada al entrar. */
  readonly sesionRecuperable = signal<SesionGuardada | null>(null);

  /** Verdadero si el disco del navegador rechazó una toma: la captura sigue, sin red de seguridad. */
  readonly guardadoLocalFallo = signal(false);

  /** Del final del recorrido: procesar, subir, completar y publicar. */
  readonly fase = signal<'CAPTURANDO' | 'PROCESANDO' | 'SUBIENDO' | 'REVISANDO' | 'PUBLICADO'>(
    'CAPTURANDO',
  );
  readonly avance = signal({ hechos: 0, total: 0 });
  readonly errorDelCierre = signal<string | null>(null);
  readonly setSubido = signal<SetRotacionAdmin | null>(null);

  /** Lo que la revisión del paso 6 le pasa al visor: el set ya procesado, tal como quedó. */
  readonly rotacionParaRevisar = computed(() =>
    (this.setSubido()?.imagenes ?? [])
      .slice()
      .sort((uno, otro) => uno.orden - otro.orden)
      .map((imagen) => imagen.url),
  );

  /**
   * La referencia del nivel la fija la primera toma: el fotograma 0 decide la inclinación y los
   * demás tienen que igualarla. Pedirle al operador que calibre a ojo antes de empezar sería
   * pedirle que adivine.
   */
  readonly objetivo = signal<Gravedad | null>(null);

  readonly siguienteOrden = computed(() => this.capturados().length);
  readonly termino = computed(() => this.siguienteOrden() >= this.fotogramasPrometidos());
  readonly fijandoReferencia = computed(() => this.objetivo() === null);

  /** El fotograma anterior, que la superposición pinta como fantasma para alinear contra él. */
  readonly fantasma = computed(() => {
    const capturados = this.capturados();
    return capturados.length === 0 ? null : capturados[capturados.length - 1].imagen.url;
  });

  /**
   * El nivel, que es lo único de aquí que necesita mirar su propio pasado: la histéresis ensancha
   * la tolerancia cuando ya se está en rango, y sin eso el obturador parpadea sobre el umbral.
   *
   * De ahí el `linkedSignal` en vez de un `computed`: recalcula igual cuando cambia la lectura o
   * la referencia, pero le pasa a la función pura el estado anterior.
   */
  readonly nivel = linkedSignal<{ actual: Gravedad | null; objetivo: Gravedad | null }, Nivel>({
    source: () => ({ actual: this.gravedadActual(), objetivo: this.objetivo() }),
    computation: ({ actual, objetivo }, anterior) =>
      // Sin referencia todavía, cualquier inclinación está bien: esta toma es la que la fija.
      evaluarNivel(actual, objetivo, anterior?.value.estado ?? null),
  });

  /**
   * Lo que un lector de pantalla oye del nivel, que **no es lo que el indicador pinta**.
   *
   * El texto visible cambia con cada grado y tiene que seguir haciéndolo: quien mira la pantalla
   * necesita el número al instante. Anunciarlo con esa cadencia es otra cosa — sobre la vuelta
   * completa grabada serían 84,5 anuncios por minuto, que vuelve la pantalla inusable con un
   * lector. Así que lo que se anuncia es la clave sin grados, y solo cuando lleva sostenida
   * `RETARDO_DE_ANUNCIO_MS`. Ver la deuda 32 en `docs/09-plan-de-arranque.md`.
   *
   * Lo avanza cada lectura del sensor, no un temporizador: el reloj ya viene en el flujo.
   */
  readonly anuncioDeNivel = signal<AnuncioDeNivel>(ANUNCIO_EN_BLANCO);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.terminar());
  }

  /**
   * Qué producto se está capturando, y si quedó una captura a medias suya. Se llama al entrar a
   * la pantalla; no abre la cámara ni toca la red.
   */
  async configurar(productoId: string): Promise<void> {
    this.productoId.set(productoId);
    if (!this.almacenLocal.disponible()) {
      return;
    }
    this.sesionRecuperable.set(await this.almacenLocal.sesionDe(productoId));
  }

  /** Retoma la captura a medias: los fotogramas vuelven de disco, con su referencia de nivel. */
  async recuperar(): Promise<void> {
    const sesion = this.sesionRecuperable();
    if (sesion === null) {
      return;
    }

    const guardados = await this.almacenLocal.fotogramasDe(sesion.sesionId);
    this.sesionId.set(sesion.sesionId);
    this.fotogramasPrometidos.set(sesion.fotogramasPrometidos);
    this.objetivo.set(sesion.objetivo);
    this.capturados.set(
      guardados.map((guardado) => ({
        orden: guardado.orden,
        imagen: {
          url: URL.createObjectURL(guardado.blob),
          blob: guardado.blob,
          ancho: guardado.ancho,
          alto: guardado.alto,
        },
        gravedad: guardado.gravedad,
      })),
    );
    this.sesionRecuperable.set(null);
  }

  /** Descarta la captura a medias y empieza de cero. */
  async descartarRecuperable(): Promise<void> {
    const sesion = this.sesionRecuperable();
    if (sesion !== null) {
      await this.almacenLocal.olvidar(sesion.sesionId);
    }
    this.sesionRecuperable.set(null);
  }

  /** El set ya se subió (o se abandonó): lo guardado en disco deja de hacer falta. */
  async olvidarLoGuardado(): Promise<void> {
    const sesionId = this.sesionId();
    if (sesionId !== '' && this.almacenLocal.disponible()) {
      await this.almacenLocal.olvidar(sesionId);
    }
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
    this.dejarDeEscuchar = this.sensor.escuchar((lectura) => {
      this.gravedadActual.set(suavizar(this.gravedadActual(), lectura));
      const nivel = this.nivel();
      this.anuncioDeNivel.update((anterior) =>
        asentarAnuncio(
          anterior,
          claveDeNivel(nivel, this.fijandoReferencia()),
          Math.abs(Math.round(nivel.ejeDominante === 'GIRAR' ? nivel.girar : nivel.inclinar)),
          Date.now(),
        ),
      );
    });
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

  /**
   * Acepta la toma pendiente. La primera fija la referencia del nivel para todas las demás, y
   * abre la sesión local en disco.
   *
   * Que el guardado falle no pierde la toma: sigue en memoria y la captura continúa. Lo que se
   * pierde es la red de seguridad, y eso lo dice la pantalla.
   */
  async aceptar(): Promise<void> {
    const imagen = this.pendiente();
    if (imagen === null) {
      return;
    }

    const gravedad = this.gravedadActual();
    if (this.objetivo() === null && gravedad !== null) {
      this.objetivo.set(gravedad);
    }
    if (this.sesionId() === '') {
      this.sesionId.set(nuevoId());
    }

    const orden = this.capturados().length;
    this.capturados.update((capturados) => [...capturados, { orden, imagen, gravedad }]);
    this.pendiente.set(null);

    await this.guardarEnDisco(orden, imagen, gravedad);
  }

  private async guardarEnDisco(
    orden: number,
    imagen: FotogramaCrudo,
    gravedad: Gravedad | null,
  ): Promise<void> {
    if (!this.almacenLocal.disponible()) {
      return;
    }

    try {
      await this.almacenLocal.guardarFotograma({
        sesionId: this.sesionId(),
        orden,
        blob: imagen.blob,
        ancho: imagen.ancho,
        alto: imagen.alto,
        gravedad,
      });
      await this.almacenLocal.guardarSesion({
        sesionId: this.sesionId(),
        productoId: this.productoId(),
        fotogramasPrometidos: this.fotogramasPrometidos(),
        objetivo: this.objetivo(),
        actualizadaEn: Date.now(),
      });
    } catch {
      this.guardadoLocalFallo.set(true);
    }
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

  /**
   * Procesa el set y lo sube: medir, encuadre común, renderizar, abrir el set en el backend, pedir
   * las N URL firmadas, subir cada fotograma y completar. Al final queda en revisión — publicar es
   * un paso aparte, que es donde el paso 6 del documento caza el fotograma torcido.
   *
   * El set se abre **después** de procesar: si el recorte falla, no queda un BORRADOR huérfano en
   * la base de datos.
   *
   * <p>Eso cubría el fallo del recorte, no el de la red, que es el que de verdad pasa en un
   * teléfono. Un `subirFotograma` que falla en la toma 20 de 36 dejaba el set abierto en BORRADOR
   * con 19 objetos en el bucket, y **volver a intentar abría otro**: `AbrirSetRotacion` no
   * comprueba nada, guarda un set nuevo. Un BORRADOR huérfano por reintento, y
   * `tools/huerfanos-bucket.mjs` declara esos fotogramas "no juzgables", así que nadie los
   * reclama. Ahora el set abierto se recuerda y el reintento lo reutiliza.
   */
  /**
   * El set que ya se abrió en el backend, mientras no se haya completado. Null antes de abrirlo y
   * después de completarlo, que es cuando el siguiente intento sí es otro set.
   */
  private setAbierto: SetRotacionAdmin | null = null;

  async procesarYSubir(): Promise<void> {
    if (this.fase() !== 'CAPTURANDO' || !this.termino()) {
      return;
    }

    this.errorDelCierre.set(null);
    this.fase.set('PROCESANDO');
    const resultado = await procesarSet(this.capturados(), this.procesador, (hechos, total) =>
      this.avance.set({ hechos, total }),
    );
    if (!resultado.ok) {
      this.errorDelCierre.set(`captura360.error_proceso.${resultado.motivo}`);
      this.fase.set('CAPTURANDO');
      return;
    }

    this.fase.set('SUBIENDO');
    this.avance.set({ hechos: 0, total: resultado.fotogramas.length });
    try {
      const set =
        this.setAbierto ??
        (await this.repositorio.abrir({
          productoId: this.productoId(),
          fotogramas: this.fotogramasPrometidos(),
          dispositivo: navigator.userAgent,
          versionAsistente: VERSION_ASISTENTE,
        }));
      this.setAbierto = set;

      const subidas = await this.repositorio.urlsDeSubida(set.id, 'image/webp');
      const subidos = [];
      for (const fotograma of resultado.fotogramas) {
        const destino = subidas.find((subida) => subida.orden === fotograma.orden);
        if (destino === undefined) {
          throw new Error('El backend no dio URL para el fotograma ' + fotograma.orden);
        }
        await this.repositorio.subirFotograma(destino.url, fotograma.blob);
        subidos.push({
          orden: fotograma.orden,
          objectKey: destino.objectKey,
          ancho: LADO_SALIDA_PX,
          alto: LADO_SALIDA_PX,
          // Sobre el blob que se acaba de subir, no sobre el de la captura: es este el que quedó
          // en el bucket.
          hash: await sha256Hex(fotograma.blob),
        });
        this.avance.set({ hechos: subidos.length, total: resultado.fotogramas.length });
      }

      this.setSubido.set(await this.repositorio.completar(set.id, subidos));
      this.fase.set('REVISANDO');
      // Completado: el siguiente set es otro.
      this.setAbierto = null;
      // Ya está a salvo en el servidor: lo de disco deja de hacer falta.
      await this.olvidarLoGuardado();
    } catch (error) {
      // "No hay red" y "el backend rechazó el fotograma por no ser de 1000 px" no son lo mismo:
      // el primero se resuelve reintentando y el segundo repitiendo el set. El `catch` vacío de
      // antes mostraba el mismo texto para los dos, y quien opera se quedaba reintentando algo
      // que no iba a funcionar nunca.
      const rechazado = error instanceof ErrorHttp && error.estado >= 400 && error.estado < 500;
      this.errorDelCierre.set(
        rechazado ? 'captura360.error_subida_rechazada' : 'captura360.error_subida',
      );
      this.fase.set('CAPTURANDO');
    }
  }

  /** El último paso: de COMPLETO a PUBLICADO, y la ficha empieza a mostrar el visor. */
  async publicar(): Promise<void> {
    const set = this.setSubido();
    if (set === null || this.fase() !== 'REVISANDO') {
      return;
    }

    this.errorDelCierre.set(null);
    try {
      this.setSubido.set(await this.repositorio.publicar(set.id));
      this.fase.set('PUBLICADO');
    } catch {
      this.errorDelCierre.set('captura360.error_publicar');
    }
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
    // Las URL de objeto se sueltan, pero lo guardado en disco se queda: es lo que permite volver
    // a esta captura si la pestaña se cerró a mitad.
    for (const capturado of this.capturados()) {
      this.camara.liberar(capturado.imagen);
    }
    this.capturados.set([]);
  }
}

/** Queda registrado en el set: cuando uno se ve mal, lo primero es saber con qué se capturó. */
const VERSION_ASISTENTE = 'asistente-web-1';

/** `crypto.randomUUID` existe en todo contexto seguro, que es lo que la cámara ya exige. */
function nuevoId(): string {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `sesion-${Date.now()}-${Math.trunc(Math.random() * 1_000_000)}`;
}
