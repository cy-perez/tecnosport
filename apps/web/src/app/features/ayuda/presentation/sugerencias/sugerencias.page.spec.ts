import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen, waitFor } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import ayudaEn from '../../../../../assets/i18n/scopes/ayuda/en.json';
import ayudaEs from '../../../../../assets/i18n/scopes/ayuda/es.json';
import { DemasiadosIntentosError } from '../../../../core/autenticacion/sesion.errores';
import {
  NuevaSugerencia,
  REPOSITORIO_SUGERENCIAS,
  RepositorioSugerencias,
} from '../../domain/repositorio-sugerencias.puerto';
import { SugerenciasPage } from './sugerencias.page';

/** Doble escrito a mano: guarda lo que le mandan y puede fallar a voluntad. */
class RepositorioSugerenciasFalso implements RepositorioSugerencias {
  enviadas: NuevaSugerencia[] = [];
  fallaCon: Error | null = null;

  async enviar(sugerencia: NuevaSugerencia): Promise<void> {
    if (this.fallaCon) {
      throw this.fallaCon;
    }
    this.enviadas.push(sugerencia);
  }
}

async function renderSugerencias(repositorio = new RepositorioSugerenciasFalso()) {
  const resultado = await render(SugerenciasPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'ayuda/es': ayudaEs, 'ayuda/en': ayudaEn } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [provideRouter([]), { provide: REPOSITORIO_SUGERENCIAS, useValue: repositorio }],
  });
  await resultado.fixture.whenStable();
  return { ...resultado, repositorio };
}

function escribir(etiqueta: string | RegExp, valor: string): void {
  fireEvent.input(screen.getByLabelText(etiqueta), { target: { value: valor } });
}

describe('SugerenciasPage', () => {
  /**
   * El aviso de que esto no es una PQR va arriba y con su enlace. Es lo que separa un buzón de
   * sugerencias de un canal de reclamos sin plazo: si alguien lo quita, esto lo dice.
   */
  it('avisa de que los reclamos van por el canal de atención, con enlace', async () => {
    await renderSugerencias();

    expect(screen.getByText(ayudaEs.sugerencias.aviso_titulo)).toBeTruthy();
    expect(screen.getByRole('link', { name: ayudaEs.sugerencias.aviso_enlace })).toBeTruthy();
  });

  /**
   * El caso central: sin correo no hay dato personal, así que no se pide autorización. Enseñar una
   * casilla de habeas data ahí sería pedir permiso para un tratamiento que no ocurre.
   */
  it('sin correo no pide la autorización y manda la sugerencia anónima', async () => {
    const { repositorio } = await renderSugerencias();

    escribir(/Tu sugerencia/, 'Sería bueno poder filtrar por talla.');
    expect(screen.queryByLabelText(ayudaEs.sugerencias.autoriza_datos)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: ayudaEs.sugerencias.enviar }));

    await waitFor(() => {
      expect(repositorio.enviadas).toEqual([
        {
          mensaje: 'Sería bueno poder filtrar por talla.',
          correo: null,
          autorizaDatos: false,
        },
      ]);
    });
  });

  it('al escribir un correo aparece la casilla de autorización', async () => {
    await renderSugerencias();

    expect(screen.queryByLabelText(ayudaEs.sugerencias.autoriza_datos)).toBeNull();

    escribir(/Tu correo/, 'ana@ejemplo.com');

    expect(await screen.findByLabelText(ayudaEs.sugerencias.autoriza_datos)).toBeTruthy();
  });

  /**
   * Con correo y sin marcar: no se manda nada y se dice por qué. Es el defecto que este proyecto ya
   * corrigió dos veces —"Sin marcar la casilla, «Continuar» no hacía nada y no decía por qué"— así
   * que aquí se fija desde el principio: el botón no se deshabilita, se valida al pulsar.
   */
  it('con correo y sin marcar la casilla no envía, y explica qué falta', async () => {
    const { repositorio } = await renderSugerencias();

    escribir(/Tu sugerencia/, 'Gracias por el envío rápido.');
    escribir(/Tu correo/, 'ana@ejemplo.com');
    fireEvent.click(screen.getByRole('button', { name: ayudaEs.sugerencias.enviar }));

    expect(
      await screen.findByText(ayudaEs.sugerencias.errores.autorizacion_requerida),
    ).toBeTruthy();
    expect(repositorio.enviadas).toEqual([]);
  });

  it('con correo y con la casilla marcada manda las tres cosas', async () => {
    const { repositorio } = await renderSugerencias();

    escribir(/Tu sugerencia/, 'Gracias.');
    escribir(/Tu correo/, '  ana@ejemplo.com  ');
    fireEvent.click(await screen.findByLabelText(ayudaEs.sugerencias.autoriza_datos));
    fireEvent.click(screen.getByRole('button', { name: ayudaEs.sugerencias.enviar }));

    await waitFor(() => {
      expect(repositorio.enviadas).toEqual([
        { mensaje: 'Gracias.', correo: 'ana@ejemplo.com', autorizaDatos: true },
      ]);
    });
  });

  it('un mensaje vacío no se envía y lo dice', async () => {
    const { repositorio } = await renderSugerencias();

    fireEvent.click(screen.getByRole('button', { name: ayudaEs.sugerencias.enviar }));

    expect(await screen.findByText(ayudaEs.sugerencias.errores.mensaje_requerido)).toBeTruthy();
    expect(repositorio.enviadas).toEqual([]);
  });

  /**
   * El acuse distingue los dos casos, y no es cosmético: a quien no dejó correo hay que decirle que
   * no habrá respuesta, o se queda esperándola.
   */
  it('el acuse dice que no habrá respuesta cuando la sugerencia fue anónima', async () => {
    await renderSugerencias();

    escribir(/Tu sugerencia/, 'Falta el color negro.');
    fireEvent.click(screen.getByRole('button', { name: ayudaEs.sugerencias.enviar }));

    expect(await screen.findByText(ayudaEs.sugerencias.exito_anonimo)).toBeTruthy();
  });

  /**
   * El 429 llega traducido desde el adaptador y la pantalla lo dice con su propio texto. Sin esto,
   * un límite alcanzado se anunciaría como "inténtalo de nuevo en un momento", que manda a
   * reintentar exactamente lo que el límite acaba de rechazar.
   */
  it('distingue el límite de intentos de un fallo cualquiera', async () => {
    const repositorio = new RepositorioSugerenciasFalso();
    repositorio.fallaCon = new DemasiadosIntentosError();
    await renderSugerencias(repositorio);

    escribir(/Tu sugerencia/, 'Hola.');
    fireEvent.click(screen.getByRole('button', { name: ayudaEs.sugerencias.enviar }));

    expect(await screen.findByText(ayudaEs.sugerencias.error_limite)).toBeTruthy();
  });

  it('un fallo del servidor deja el formulario puesto y lo dice', async () => {
    const repositorio = new RepositorioSugerenciasFalso();
    repositorio.fallaCon = new Error('500');
    await renderSugerencias(repositorio);

    escribir(/Tu sugerencia/, 'Hola.');
    fireEvent.click(screen.getByRole('button', { name: ayudaEs.sugerencias.enviar }));

    expect(await screen.findByText(ayudaEs.sugerencias.error_generico)).toBeTruthy();
    // Lo escrito sigue ahí: perder el texto de quien acaba de escribir trescientas palabras por un
    // fallo de red sería la peor forma de fallar de esta pantalla.
    expect((screen.getByLabelText(/Tu sugerencia/) as HTMLTextAreaElement).value).toBe('Hola.');
  });
});
