import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen, waitFor } from '@testing-library/angular';
import esAdmin from '../../../../assets/i18n/scopes/admin/es.json';
import esCaptura from '../../../../assets/i18n/scopes/captura360/es.json';
import en from '../../../../assets/i18n/en.json';
import es from '../../../../assets/i18n/es.json';
import { CapturaStore } from '../application/captura.store';
import { CAMARA, Camara, FotogramaCrudo } from '../domain/camara.puerto';
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
    return { url: `blob:toma-${this.tomas}`, ancho: 1920, alto: 1920 };
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

async function renderCaptura() {
  const camara = new CamaraFalsa();
  const sensor = new SensorFalso();

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
      { provide: CAMARA, useValue: camara },
      { provide: SENSOR_ORIENTACION, useValue: sensor },
      { provide: PANTALLA_DESPIERTA, useClass: PantallaFalsa },
      CapturaStore,
    ],
  });

  return { ...resultado, camara, sensor };
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
});
