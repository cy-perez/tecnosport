import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen, waitFor } from '@testing-library/angular';
import esAdmin from '../../../../assets/i18n/scopes/admin/es.json';
import esCaptura from '../../../../assets/i18n/scopes/captura360/es.json';
import en from '../../../../assets/i18n/en.json';
import es from '../../../../assets/i18n/es.json';
import { CapturaStore } from '../application/captura.store';
import {
  ALMACEN_LOCAL_DE_CAPTURAS,
  AlmacenLocalDeCapturas,
  FotogramaGuardado,
  SesionGuardada,
} from '../domain/almacen-local-capturas.puerto';
import { CAMARA, Camara, FotogramaCrudo } from '../domain/camara.puerto';
import {
  PROCESADOR_DE_FOTOGRAMAS,
  ProcesadorDeFotogramas,
} from '../domain/procesador-fotogramas.puerto';
import { ColorRgb, DeteccionDeRecorte } from '../domain/recorte-360';
import {
  AbrirSetRotacion,
  FotogramaSubido,
  REPOSITORIO_SETS_ROTACION,
  RepositorioSetsRotacion,
  SetRotacionAdmin,
  SubidaDeFotograma,
} from '../domain/repositorio-sets-rotacion.puerto';
import { LecturaDeOrientacion } from '../domain/nivel-360';
import { PANTALLA_DESPIERTA, PantallaDespierta } from '../domain/pantalla-despierta.puerto';
import { SENSOR_ORIENTACION, SensorOrientacion } from '../domain/sensor-orientacion.puerto';
import { Captura360Page } from './captura-360.page';

/** Doble escrito a mano: la pantalla se prueba entera sin cámara, que es para lo que existe el puerto. */
class CamaraFalsa implements Camara {
  hayCamara = true;
  concedePermiso = true;
  tomas = 0;
  liberadas: string[] = [];

  disponible(): boolean {
    return this.hayCamara;
  }

  async abrir(): Promise<MediaStream> {
    if (!this.concedePermiso) {
      throw new Error('NotAllowedError');
    }
    return {} as MediaStream;
  }

  cerrar(): void {
    // Cerrar un stream falso no tiene nada que hacer.
  }

  async capturar(): Promise<FotogramaCrudo> {
    this.tomas++;
    return {
      url: `blob:toma-${this.tomas}`,
      blob: new Blob([`toma-${this.tomas}`], { type: 'image/webp' }),
      ancho: 1920,
      alto: 1920,
    };
  }

  liberar(fotograma: FotogramaCrudo): void {
    this.liberadas.push(fotograma.url);
  }
}

class SensorFalso implements SensorOrientacion {
  haySensor = true;
  concedePermiso = true;
  private alLeer: ((lectura: LecturaDeOrientacion) => void) | null = null;

  disponible(): boolean {
    return this.haySensor;
  }

  async pedirPermiso(): Promise<boolean> {
    return this.concedePermiso;
  }

  escuchar(alLeer: (lectura: LecturaDeOrientacion) => void): () => void {
    this.alLeer = alLeer;
    return () => {
      this.alLeer = null;
    };
  }

  emitir(beta: number, gamma: number): void {
    this.alLeer?.({ beta, gamma });
  }
}

/** Disco del navegador, en memoria: lo que importa es que se guarde y se pueda recuperar. */
class AlmacenLocalFalso implements AlmacenLocalDeCapturas {
  hayDisco = true;
  sesiones: SesionGuardada[] = [];
  guardados: FotogramaGuardado[] = [];
  olvidados: string[] = [];

  disponible(): boolean {
    return this.hayDisco;
  }

  async guardarSesion(sesion: SesionGuardada): Promise<void> {
    this.sesiones = [...this.sesiones.filter((s) => s.sesionId !== sesion.sesionId), sesion];
  }

  async sesionDe(productoId: string): Promise<SesionGuardada | null> {
    return this.sesiones.find((sesion) => sesion.productoId === productoId) ?? null;
  }

  async guardarFotograma(fotograma: FotogramaGuardado): Promise<void> {
    this.guardados = [
      ...this.guardados.filter(
        (g) => !(g.sesionId === fotograma.sesionId && g.orden === fotograma.orden),
      ),
      fotograma,
    ];
  }

  async fotogramasDe(sesionId: string): Promise<FotogramaGuardado[]> {
    return this.guardados.filter((g) => g.sesionId === sesionId).sort((a, b) => a.orden - b.orden);
  }

  async olvidar(sesionId: string): Promise<void> {
    this.olvidados.push(sesionId);
    this.sesiones = this.sesiones.filter((s) => s.sesionId !== sesionId);
    this.guardados = this.guardados.filter((g) => g.sesionId !== sesionId);
  }
}

const FONDO: ColorRgb = { r: 240, g: 240, b: 240 };

class ProcesadorFalso implements ProcesadorDeFotogramas {
  deteccion: DeteccionDeRecorte = {
    ok: true,
    rectangulo: { x: 400, y: 400, ancho: 800, alto: 900 },
    fondo: FONDO,
  };
  renderizados = 0;

  async medir(): Promise<DeteccionDeRecorte> {
    return this.deteccion;
  }

  async renderizar(): Promise<Blob> {
    this.renderizados++;
    return new Blob([`procesado-${this.renderizados}`], { type: 'image/webp' });
  }
}

class RepositorioSetsFalso implements RepositorioSetsRotacion {
  abiertos: AbrirSetRotacion[] = [];
  subidas: string[] = [];
  completadoCon: readonly FotogramaSubido[] = [];
  publicados: string[] = [];
  fallaLaSubida = false;

  private set(estado: SetRotacionAdmin['estado'], fotogramas: number): SetRotacionAdmin {
    return {
      id: 'set-1',
      productoId: 'p1',
      fotogramasPrometidos: fotogramas,
      estado,
      imagenes: Array.from({ length: fotogramas }, (_, orden) => ({
        orden,
        urlWebp: `https://cdn.test/set-1/${orden}.webp`,
      })),
    };
  }

  async abrir(comando: AbrirSetRotacion): Promise<SetRotacionAdmin> {
    this.abiertos.push(comando);
    return this.set('BORRADOR', comando.fotogramas);
  }

  async urlsDeSubida(setId: string): Promise<SubidaDeFotograma[]> {
    return Array.from({ length: 4 }, (_, orden) => ({
      orden,
      url: `https://firmada.test/${setId}/${orden}`,
      objectKey: `productos/p1/rotacion/${setId}/${orden}.webp`,
    }));
  }

  async subirFotograma(url: string): Promise<void> {
    if (this.fallaLaSubida) {
      throw new Error('sin red');
    }
    this.subidas.push(url);
  }

  async completar(
    _setId: string,
    fotogramas: readonly FotogramaSubido[],
  ): Promise<SetRotacionAdmin> {
    this.completadoCon = fotogramas;
    return this.set('COMPLETO', 4);
  }

  async publicar(setId: string): Promise<SetRotacionAdmin> {
    this.publicados.push(setId);
    return this.set('PUBLICADO', 4);
  }

  async eliminar(): Promise<void> {
    // Borrar un set falso no tiene nada que hacer.
  }
}

class PantallaFalsa implements PantallaDespierta {
  async mantener(): Promise<() => void> {
    return () => undefined;
  }
}

/**
 * Un clic dispara trabajo asíncrono —abrir la cámara, capturar— que en modo zoneless no queda
 * registrado como tarea pendiente, así que `whenStable()` vuelve antes de tiempo. Hace falta
 * ceder un turno real del bucle de eventos y repintar, mismo recurso que ya usa
 * `filtros-productos.spec.ts` para el debounce.
 */
function esperar(ms = 0): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const PRODUCTO = 'p1';

/**
 * `URL.createObjectURL` no existe en jsdom y el store la usa al recuperar de disco. Se apaña con
 * un doble: lo que la prueba comprueba es qué fotogramas vuelven, no cómo se pintan.
 */
function apanarUrlDeObjeto(): void {
  const url = URL as unknown as Record<string, unknown>;
  if (typeof url['createObjectURL'] !== 'function') {
    url['createObjectURL'] = () => 'blob:recuperada';
    url['revokeObjectURL'] = () => undefined;
  }
}

async function renderCaptura(sembrarDisco?: (almacen: AlmacenLocalFalso) => void) {
  apanarUrlDeObjeto();
  const camara = new CamaraFalsa();
  const sensor = new SensorFalso();
  const almacenLocal = new AlmacenLocalFalso();
  sembrarDisco?.(almacenLocal);
  const procesador = new ProcesadorFalso();
  const repositorio = new RepositorioSetsFalso();

  const resultado = await render(Captura360Page, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin, 'captura360/es': esCaptura } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: {
          paramMap: of(convertToParamMap({ productoId: PRODUCTO })),
          snapshot: { paramMap: convertToParamMap({ productoId: PRODUCTO }) },
        },
      },
      { provide: CAMARA, useValue: camara },
      { provide: SENSOR_ORIENTACION, useValue: sensor },
      { provide: PANTALLA_DESPIERTA, useClass: PantallaFalsa },
      { provide: ALMACEN_LOCAL_DE_CAPTURAS, useValue: almacenLocal },
      { provide: PROCESADOR_DE_FOTOGRAMAS, useValue: procesador },
      { provide: REPOSITORIO_SETS_ROTACION, useValue: repositorio },
      CapturaStore,
    ],
  });

  return { ...resultado, camara, sensor, almacenLocal, procesador, repositorio };
}

/**
 * Procesar el set cede el turno del bucle de eventos entre fotograma y fotograma —para que la
 * barra de progreso se repinte—, así que hacen falta varios turnos, no uno.
 */
async function asentarVarias(fixture: Parameters<typeof asentar>[0], veces = 14) {
  for (let i = 0; i < veces; i++) {
    await asentar(fixture);
  }
}

/** Captura el set entero de cuatro tomas, aceptando cada una. */
async function capturarCuatro(fixture: Parameters<typeof asentar>[0]) {
  fireEvent.click(screen.getByRole('button', { name: '4 fotogramas' }));
  fireEvent.click(screen.getByRole('button', { name: 'Permitir la cámara' }));
  await asentar(fixture);

  for (let toma = 0; toma < 4; toma++) {
    fireEvent.click(screen.getByRole('button', { name: 'Tomar la foto' }));
    await asentar(fixture);
    fireEvent.click(await screen.findByRole('button', { name: 'Aceptar y seguir' }));
    await asentar(fixture);
  }
}

async function asentar(fixture: { detectChanges: () => void; whenStable: () => Promise<unknown> }) {
  await esperar();
  fixture.detectChanges();
  await fixture.whenStable();
}

async function conCamaraAbierta() {
  const contexto = await renderCaptura();
  fireEvent.click(screen.getByRole('button', { name: 'Permitir la cámara' }));
  await asentar(contexto.fixture);
  return contexto;
}

describe('Captura360Page', () => {
  it('arranca en la preparacion, con las condiciones de captura a la vista', async () => {
    await renderCaptura();

    expect(screen.getByText('Antes de empezar')).toBeTruthy();
    expect(screen.getByText('Fondo claro y uniforme, sin objetos detrás.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Permitir la cámara' })).toBeTruthy();
  });

  it('sin camara en el dispositivo lo dice, en vez de dejar la pantalla en blanco', async () => {
    const { camara, fixture } = await renderCaptura();
    camara.hayCamara = false;

    fireEvent.click(screen.getByRole('button', { name: 'Permitir la cámara' }));
    await asentar(fixture);

    expect(screen.getByRole('alert').textContent).toContain(
      'Este navegador no da acceso a la cámara',
    );
  });

  it('con el permiso negado explica como recuperarlo', async () => {
    const { camara, fixture } = await renderCaptura();
    camara.concedePermiso = false;

    fireEvent.click(screen.getByRole('button', { name: 'Permitir la cámara' }));
    await asentar(fixture);

    expect(screen.getByRole('alert').textContent).toContain('Sin permiso de cámara');
  });

  it('con la camara abierta muestra en que toma va', async () => {
    await conCamaraAbierta();

    expect(screen.getByText(/Toma 1 de 8/)).toBeTruthy();
    expect(screen.getByText(/Frontal/)).toBeTruthy();
  });

  it('el numero de fotogramas se elige antes de abrir la camara', async () => {
    const { fixture } = await renderCaptura();

    fireEvent.click(screen.getByRole('button', { name: '4 fotogramas' }));
    fireEvent.click(screen.getByRole('button', { name: 'Permitir la cámara' }));
    await asentar(fixture);

    expect(screen.getByText(/Toma 1 de 4/)).toBeTruthy();
  });

  it('sin sensor se puede capturar igual: el nivel no es un bloqueo', async () => {
    const { sensor, fixture } = await conCamaraAbierta();
    sensor.haySensor = false;

    fireEvent.click(screen.getByRole('button', { name: 'Activar el nivel' }));
    await asentar(fixture);

    expect(screen.getByText(/Sin nivel: este dispositivo no da la inclinación/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Tomar la foto' }).hasAttribute('disabled')).toBe(
      false,
    );
  });

  it('una toma se acepta y el contador avanza', async () => {
    const { fixture } = await conCamaraAbierta();

    fireEvent.click(screen.getByRole('button', { name: 'Tomar la foto' }));
    await asentar(fixture);
    fireEvent.click(await screen.findByRole('button', { name: 'Aceptar y seguir' }));
    await asentar(fixture);

    expect(screen.getByText(/Toma 2 de 8/)).toBeTruthy();
  });

  it('repetir una toma no reinicia la secuencia ni deja la imagen colgada', async () => {
    const { camara, fixture } = await conCamaraAbierta();

    fireEvent.click(screen.getByRole('button', { name: 'Tomar la foto' }));
    await asentar(fixture);
    fireEvent.click(await screen.findByRole('button', { name: 'Repetir esta toma' }));
    await asentar(fixture);

    expect(screen.getByText(/Toma 1 de 8/)).toBeTruthy();
    expect(camara.liberadas).toEqual(['blob:toma-1']);
    expect(screen.getByRole('button', { name: 'Tomar la foto' })).toBeTruthy();
  });

  it('a partir de la segunda toma aparece el fantasma de la anterior', async () => {
    const { fixture, container } = await conCamaraAbierta();

    expect(container.querySelector('ts-superposicion-guia img')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Tomar la foto' }));
    await asentar(fixture);
    fireEvent.click(await screen.findByRole('button', { name: 'Aceptar y seguir' }));
    await asentar(fixture);

    expect(container.querySelector('ts-superposicion-guia img')?.getAttribute('src')).toBe(
      'blob:toma-1',
    );
  });

  it('el obturador se bloquea cuando el telefono se sale de la inclinacion de la primera toma', async () => {
    const { sensor, fixture } = await conCamaraAbierta();

    fireEvent.click(screen.getByRole('button', { name: 'Activar el nivel' }));
    await asentar(fixture);

    // La primera toma fija la referencia con el telefono asi.
    sensor.emitir(90, 0);
    await asentar(fixture);
    fireEvent.click(screen.getByRole('button', { name: 'Tomar la foto' }));
    await asentar(fixture);
    fireEvent.click(await screen.findByRole('button', { name: 'Aceptar y seguir' }));
    await asentar(fixture);

    // Y ahora se tuerce mucho mas de los tres grados de tolerancia.
    for (let i = 0; i < 40; i++) {
      sensor.emitir(70, 0);
    }
    await asentar(fixture);

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Tomar la foto' }).hasAttribute('disabled')).toBe(
        true,
      ),
    );
    expect(screen.getByText(/El obturador se habilita cuando el teléfono vuelve/)).toBeTruthy();
  });

  it('al completar las tomas prometidas lo dice y no ofrece disparar mas', async () => {
    const { fixture } = await renderCaptura();

    fireEvent.click(screen.getByRole('button', { name: '4 fotogramas' }));
    fireEvent.click(screen.getByRole('button', { name: 'Permitir la cámara' }));
    await asentar(fixture);

    for (let toma = 0; toma < 4; toma++) {
      fireEvent.click(screen.getByRole('button', { name: 'Tomar la foto' }));
      await asentar(fixture);
      fireEvent.click(await screen.findByRole('button', { name: 'Aceptar y seguir' }));
      await asentar(fixture);
    }

    expect(screen.getByText(/Listas las 4 tomas/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Tomar la foto' })).toBeNull();
  });

  it('cada toma aceptada se guarda en disco: cerrar la pestana no cuesta las fotos', async () => {
    const { fixture, almacenLocal } = await conCamaraAbierta();

    fireEvent.click(screen.getByRole('button', { name: 'Tomar la foto' }));
    await asentar(fixture);
    fireEvent.click(await screen.findByRole('button', { name: 'Aceptar y seguir' }));
    await asentar(fixture);

    expect(almacenLocal.guardados).toHaveLength(1);
    expect(almacenLocal.guardados[0].orden).toBe(0);
    expect(almacenLocal.sesiones).toHaveLength(1);
  });

  it('si el disco rechaza la toma la captura sigue, avisando que no hay respaldo', async () => {
    const { fixture, almacenLocal } = await renderCaptura();
    almacenLocal.guardarFotograma = async () => {
      throw new Error('sin espacio');
    };

    fireEvent.click(screen.getByRole('button', { name: 'Permitir la cámara' }));
    await asentar(fixture);
    fireEvent.click(screen.getByRole('button', { name: 'Tomar la foto' }));
    await asentar(fixture);
    fireEvent.click(await screen.findByRole('button', { name: 'Aceptar y seguir' }));
    await asentar(fixture);

    expect(screen.getByText(/No se pudieron guardar las tomas en este dispositivo/)).toBeTruthy();
    expect(screen.getByText(/Toma 2 de 8/)).toBeTruthy();
  });

  it('procesa, sube y completa el set, y lo deja en revision antes de publicar', async () => {
    const { fixture, procesador, repositorio } = await renderCaptura();
    await capturarCuatro(fixture);

    fireEvent.click(screen.getByRole('button', { name: 'Procesar y subir el set' }));
    await asentarVarias(fixture);

    expect(procesador.renderizados).toBe(4);
    expect(repositorio.subidas).toHaveLength(4);
    expect(repositorio.completadoCon).toHaveLength(4);
    // El backend recibe el tamano de salida, no el de la camara.
    expect(repositorio.completadoCon[0].ancho).toBe(1000);
    expect(await screen.findByText('Revisa la rotación completa')).toBeTruthy();
    // Publicar es un paso aparte: el set todavia no esta publicado.
    expect(repositorio.publicados).toEqual([]);
  });

  it('el set se abre en el backend despues de procesar, no antes', async () => {
    const { fixture, procesador, repositorio } = await renderCaptura();
    procesador.deteccion = { ok: false, motivo: 'PRODUCTO_CORTADO' };
    await capturarCuatro(fixture);

    fireEvent.click(screen.getByRole('button', { name: 'Procesar y subir el set' }));
    await asentarVarias(fixture);

    // Un recorte que falla no deja un BORRADOR huerfano en la base de datos.
    expect(repositorio.abiertos).toEqual([]);
    expect(screen.getByRole('alert').textContent).toContain('el producto toca el borde del marco');
  });

  it('publicar deja el set publicado y lo dice', async () => {
    const { fixture, repositorio, almacenLocal } = await renderCaptura();
    await capturarCuatro(fixture);

    fireEvent.click(screen.getByRole('button', { name: 'Procesar y subir el set' }));
    await asentarVarias(fixture);
    fireEvent.click(await screen.findByRole('button', { name: 'Publicar el set' }));
    await asentar(fixture);

    expect(repositorio.publicados).toEqual(['set-1']);
    expect(screen.getByText(/La ficha del producto ya muestra el visor 360/)).toBeTruthy();
    // Ya esta a salvo en el servidor: lo de disco deja de hacer falta.
    expect(almacenLocal.olvidados).toHaveLength(1);
  });

  it('si la subida falla, las tomas siguen guardadas y se puede reintentar', async () => {
    const { fixture, repositorio, almacenLocal } = await renderCaptura();
    repositorio.fallaLaSubida = true;
    await capturarCuatro(fixture);

    fireEvent.click(screen.getByRole('button', { name: 'Procesar y subir el set' }));
    await asentarVarias(fixture);

    expect(screen.getByRole('alert').textContent).toContain('Las tomas siguen guardadas');
    expect(almacenLocal.olvidados).toEqual([]);
    expect(screen.getByRole('button', { name: 'Procesar y subir el set' })).toBeTruthy();
  });

  it('una captura a medias del mismo producto se ofrece para continuar, y continua', async () => {
    // Dos de las cuatro tomas quedaron en disco de una sesión anterior.
    const { fixture } = await renderCaptura((almacen) => {
      almacen.sesiones.push({
        sesionId: 's1',
        productoId: PRODUCTO,
        fotogramasPrometidos: 4,
        objetivo: { beta: 90, gamma: 0 },
        actualizadaEn: 1,
      });
      for (let orden = 0; orden < 2; orden++) {
        almacen.guardados.push({
          sesionId: 's1',
          orden,
          blob: new Blob([`vieja-${orden}`], { type: 'image/webp' }),
          ancho: 1920,
          alto: 1920,
          inclinacion: { beta: 90, gamma: 0 },
        });
      }
    });
    await asentarVarias(fixture, 4);

    expect(await screen.findByText(/Quedó una captura a medias de este producto/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Continuar donde iba' }));
    await asentarVarias(fixture, 4);
    fireEvent.click(screen.getByRole('button', { name: 'Permitir la cámara' }));
    await asentarVarias(fixture, 4);

    // Sigue en la tercera de cuatro, no empieza de cero.
    expect(screen.getByText(/Toma 3 de 4/)).toBeTruthy();
  });

  it('descartar la captura a medias la borra del disco y empieza de cero', async () => {
    const { fixture, almacenLocal } = await renderCaptura((almacen) => {
      almacen.sesiones.push({
        sesionId: 's1',
        productoId: PRODUCTO,
        fotogramasPrometidos: 4,
        objetivo: null,
        actualizadaEn: 1,
      });
    });
    await asentarVarias(fixture, 4);

    fireEvent.click(screen.getByRole('button', { name: 'Empezar de nuevo' }));
    await asentarVarias(fixture, 4);

    expect(almacenLocal.olvidados).toEqual(['s1']);
    expect(screen.getByText('Antes de empezar')).toBeTruthy();
  });
});
