import { TestBed } from '@angular/core/testing';
import { ErrorHttp } from '../../../core/http/respuesta-http';
import {
  ALMACEN_LOCAL_DE_CAPTURAS,
  AlmacenLocalDeCapturas,
  FotogramaGuardado,
  SesionGuardada,
} from '../domain/almacen-local-capturas.puerto';
import { CAMARA, Camara, FotogramaCrudo } from '../domain/camara.puerto';
import { PANTALLA_DESPIERTA, PantallaDespierta } from '../domain/pantalla-despierta.puerto';
import {
  PROCESADOR_DE_FOTOGRAMAS,
  ProcesadorDeFotogramas,
} from '../domain/procesador-fotogramas.puerto';
import { ColorRgb, DeteccionDeRecorte, EncuadreDelSet, Rectangulo } from '../domain/recorte-360';
import {
  AbrirSetRotacion,
  FotogramaSubido,
  REPOSITORIO_SETS_ROTACION,
  RepositorioSetsRotacion,
  SetRotacionAdmin,
  SubidaDeFotograma,
} from '../domain/repositorio-sets-rotacion.puerto';
import { SENSOR_ORIENTACION, SensorOrientacion } from '../domain/sensor-orientacion.puerto';
import { FotogramaCapturado } from '../domain/sesion-captura.model';
import { CapturaStore } from './captura.store';

/**
 * El cierre del set: qué pasa cuando la subida falla a mitad.
 *
 * El javadoc de `procesarYSubir` prometía que el set se abre después de procesar "si el recorte
 * falla, no queda un BORRADOR huérfano". Eso cubre el fallo del recorte, no el de la red — que es
 * el que de verdad pasa en un teléfono. Un `subirFotograma` que falla en la toma 3 de 4 dejaba el
 * set abierto en BORRADOR con dos objetos en el bucket, y volver a intentar **abría otro**:
 * `AbrirSetRotacion` no comprueba nada. Un BORRADOR huérfano por reintento, y
 * `tools/huerfanos-bucket.mjs` declara esos fotogramas "no juzgables", así que no los reclama
 * nadie.
 */
describe('CapturaStore: el cierre del set', () => {
  const FONDO: ColorRgb = { r: 240, g: 240, b: 240 };

  class ProcesadorFalso implements ProcesadorDeFotogramas {
    async medir(): Promise<DeteccionDeRecorte> {
      const rectangulo: Rectangulo = { x: 500, y: 400, ancho: 800, alto: 700 };
      return { ok: true, rectangulo, fondo: FONDO };
    }

    async renderizar(
      _toma: Blob,
      _rectangulo: Rectangulo,
      _encuadre: EncuadreDelSet,
      _fondo: ColorRgb,
    ): Promise<Blob> {
      return new Blob(['procesado']);
    }
  }

  /** Cuenta cuántos sets se abrieron y falla las subidas mientras `fallarSubidas` esté puesto. */
  class RepositorioEspia implements RepositorioSetsRotacion {
    abiertos = 0;
    fallarSubidas: Error | null = null;
    completados: string[] = [];

    async abrir(comando: AbrirSetRotacion): Promise<SetRotacionAdmin> {
      this.abiertos++;
      return {
        id: `set-${this.abiertos}`,
        productoId: comando.productoId,
        fotogramasPrometidos: comando.fotogramas,
        estado: 'BORRADOR',
        imagenes: [],
      };
    }

    async urlsDeSubida(setId: string): Promise<SubidaDeFotograma[]> {
      return [0, 1, 2, 3].map((orden) => ({
        orden,
        url: `https://bucket.test/${setId}/${orden}`,
        objectKey: `productos/p1/rotacion/${setId}/${orden}.webp`,
      }));
    }

    async subirFotograma(): Promise<void> {
      if (this.fallarSubidas) {
        throw this.fallarSubidas;
      }
    }

    async completar(
      setId: string,
      fotogramas: readonly FotogramaSubido[],
    ): Promise<SetRotacionAdmin> {
      this.completados.push(setId);
      return {
        id: setId,
        productoId: 'p1',
        fotogramasPrometidos: fotogramas.length,
        estado: 'COMPLETO',
        imagenes: [],
      };
    }

    async publicar(setId: string): Promise<SetRotacionAdmin> {
      return {
        id: setId,
        productoId: 'p1',
        fotogramasPrometidos: 4,
        estado: 'PUBLICADO',
        imagenes: [],
      };
    }

    async eliminar(): Promise<void> {}
  }

  const camaraFalsa: Camara = {
    disponible: () => false,
    abrir: async () => ({}) as MediaStream,
    cerrar: () => {},
    capturar: async () => ({}) as FotogramaCrudo,
    liberar: () => {},
  };

  const almacenFalso: AlmacenLocalDeCapturas = {
    disponible: () => false,
    guardarSesion: async (_sesion: SesionGuardada) => {},
    sesionDe: async () => null,
    guardarFotograma: async (_fotograma: FotogramaGuardado) => {},
    fotogramasDe: async () => [],
    olvidar: async () => {},
  };

  const sensorFalso: SensorOrientacion = {
    disponible: () => false,
    pedirPermiso: async () => false,
    escuchar: () => () => {},
  };

  const pantallaFalsa: PantallaDespierta = {
    mantener: async () => () => {},
  };

  function capturado(orden: number): FotogramaCapturado {
    return {
      orden,
      imagen: { url: `blob:${orden}`, blob: new Blob([`toma-${orden}`]), ancho: 1920, alto: 1920 },
      gravedad: null,
    };
  }

  function montar(): { store: CapturaStore; repositorio: RepositorioEspia } {
    const repositorio = new RepositorioEspia();
    TestBed.configureTestingModule({
      providers: [
        CapturaStore,
        { provide: CAMARA, useValue: camaraFalsa },
        { provide: ALMACEN_LOCAL_DE_CAPTURAS, useValue: almacenFalso },
        { provide: PROCESADOR_DE_FOTOGRAMAS, useValue: new ProcesadorFalso() },
        { provide: REPOSITORIO_SETS_ROTACION, useValue: repositorio },
        { provide: SENSOR_ORIENTACION, useValue: sensorFalso },
        { provide: PANTALLA_DESPIERTA, useValue: pantallaFalsa },
      ],
    });
    const store = TestBed.inject(CapturaStore);
    store.productoId.set('p1');
    store.fotogramasPrometidos.set(4);
    store.capturados.set([capturado(0), capturado(1), capturado(2), capturado(3)]);
    store.fase.set('CAPTURANDO');
    return { store, repositorio };
  }

  it('reutiliza el set ya abierto cuando la subida falló y se reintenta', async () => {
    const { store, repositorio } = montar();
    repositorio.fallarSubidas = new TypeError('Failed to fetch');

    await store.procesarYSubir();
    expect(store.fase()).toBe('CAPTURANDO');
    expect(repositorio.abiertos).toBe(1);

    repositorio.fallarSubidas = null;
    await store.procesarYSubir();

    // Lo que importa: no se abrió un segundo BORRADOR. Antes sí, uno por reintento.
    expect(repositorio.abiertos).toBe(1);
    expect(repositorio.completados).toEqual(['set-1']);
    expect(store.fase()).toBe('REVISANDO');
  });

  it('abre un set nuevo para la captura siguiente, cuando la anterior sí se completó', async () => {
    const { store, repositorio } = montar();

    await store.procesarYSubir();
    expect(store.fase()).toBe('REVISANDO');

    store.capturados.set([capturado(0), capturado(1), capturado(2), capturado(3)]);
    store.fase.set('CAPTURANDO');
    await store.procesarYSubir();

    expect(repositorio.abiertos).toBe(2);
  });

  /**
   * "No hay red" y "el backend rechazó el fotograma por no ser de 1000 px" no son lo mismo: el
   * primero se resuelve reintentando y el segundo repitiendo el set. El `catch` vacío de antes
   * mostraba el mismo texto para los dos, y quien opera se quedaba reintentando algo que no iba a
   * funcionar nunca.
   */
  it('distingue el rechazo del servidor de una caída de red', async () => {
    const { store, repositorio } = montar();

    repositorio.fallarSubidas = new TypeError('Failed to fetch');
    await store.procesarYSubir();
    expect(store.errorDelCierre()).toBe('captura360.error_subida');

    repositorio.fallarSubidas = new ErrorHttp(
      422,
      'el fotograma no mide 1000 px',
      'RECORTE_INVALIDO',
    );
    await store.procesarYSubir();
    expect(store.errorDelCierre()).toBe('captura360.error_subida_rechazada');
  });
});
