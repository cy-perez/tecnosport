import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import {
  EstadoSolicitudAtencion,
  SolicitudAtencion,
  TipoSolicitud,
} from '../../domain/atencion.model';
import {
  REPOSITORIO_ATENCION,
  RepositorioAtencion,
} from '../../domain/repositorio-atencion.puerto';
import { BandejaAtencionPage } from './bandeja-atencion.page';

const EN_UNA_SEMANA = new Date(Date.now() + 7 * 86_400_000).toISOString();
const HACE_UNA_SEMANA = new Date(Date.now() - 7 * 86_400_000).toISOString();

function solicitud(overrides: Partial<SolicitudAtencion> = {}): SolicitudAtencion {
  return {
    id: 's1',
    numeroRadicado: 'TS-PQR-2026-000001',
    tipo: 'PETICION',
    correo: 'cliente@tecnosport.co',
    pedidoId: null,
    recibidaEn: '2026-09-10T14:00:00Z',
    radicadaEn: '2026-09-12T14:00:00Z',
    radicadaPor: 'admin:1',
    asunto: 'No me llego el pedido',
    estado: 'RADICADA',
    limiteDeRespuesta: EN_UNA_SEMANA,
    verdicto: 'EN_PLAZO',
    prorroga: null,
    respuesta: null,
    ...overrides,
  };
}

class RepositorioAtencionFalso implements RepositorioAtencion {
  radicadas: {
    tipo: TipoSolicitud;
    correo: string;
    pedidoId: string | null;
    recibidaEn: string | null;
    asunto: string;
  }[] = [];
  respondidas: { solicitudId: string; resumen: string }[] = [];
  prorrogadas: { solicitudId: string; motivo: string }[] = [];
  filtros: (EstadoSolicitudAtencion | null)[] = [];

  constructor(private readonly solicitudes: SolicitudAtencion[] = []) {}

  async listar(estado: EstadoSolicitudAtencion | null): Promise<readonly SolicitudAtencion[]> {
    this.filtros.push(estado);
    return estado === null
      ? this.solicitudes.filter((s) => s.estado !== 'RESPONDIDA')
      : this.solicitudes.filter((s) => s.estado === estado);
  }

  async radicar(entrada: {
    tipo: TipoSolicitud;
    correo: string;
    pedidoId: string | null;
    recibidaEn: string | null;
    asunto: string;
  }): Promise<SolicitudAtencion> {
    this.radicadas.push(entrada);
    return solicitud();
  }

  async responder(solicitudId: string, resumen: string): Promise<SolicitudAtencion> {
    this.respondidas.push({ solicitudId, resumen });
    return solicitud({ estado: 'RESPONDIDA' });
  }

  async prorrogar(solicitudId: string, motivo: string): Promise<SolicitudAtencion> {
    this.prorrogadas.push({ solicitudId, motivo });
    return solicitud({ estado: 'PRORROGADA' });
  }
}

async function renderBandeja(solicitudes: SolicitudAtencion[] = []) {
  const repositorio = new RepositorioAtencionFalso(solicitudes);
  const resultado = await render(BandejaAtencionPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_ATENCION, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

describe('BandejaAtencionPage', () => {
  it('muestra el radicado, que es el numero con el que el interesado puede volver a preguntar', async () => {
    await renderBandeja([solicitud()]);

    expect(await screen.findByText(/TS-PQR-2026-000001/)).toBeTruthy();
  });

  it('sin solicitudes abiertas lo dice, en vez de dejar la pantalla en blanco', async () => {
    await renderBandeja([]);

    expect(await screen.findByText('No hay solicitudes abiertas.')).toBeTruthy();
  });

  /**
   * El plazo que ya paso tiene que verse distinto del que falta: la bandeja existe para eso, y una
   * lista donde todo se ve igual no sirve para lo unico que hace falta mirar.
   */
  it('marca lo que ya paso de plazo', async () => {
    await renderBandeja([
      solicitud({ limiteDeRespuesta: HACE_UNA_SEMANA, verdicto: 'INDETERMINADO' }),
    ]);

    expect(await screen.findByText('El plazo para responder ya pasó.')).toBeTruthy();
  });

  /**
   * `INDETERMINADO` no se pinta como vencido: sin el calendario de festivos cargado, afirmar que un
   * plazo vencio seria acusar de un incumplimiento que quiza no ocurrio.
   */
  it('explica el veredicto indeterminado en vez de darlo por vencido', async () => {
    await renderBandeja([solicitud({ verdicto: 'INDETERMINADO' })]);

    expect(
      await screen.findByText(
        esAdmin.atencion.verdicto.indeterminado_ayuda,
      ),
    ).toBeTruthy();
  });

  it('radicar manda el tipo elegido, que es lo que decide el plazo', async () => {
    const { repositorio } = await renderBandeja([]);
    const tipo = await screen.findByLabelText('Tipo de solicitud');
    fireEvent.change(tipo, { target: { value: 'CONSULTA_DATOS' } });
    fireEvent.input(screen.getByLabelText('Correo de quien la presenta'), {
      target: { value: 'titular@tecnosport.co' },
    });
    fireEvent.input(screen.getByLabelText('Asunto'), { target: { value: 'Quiero mis datos' } });
    fireEvent.click(screen.getByRole('button', { name: 'Radicar' }));

    await vi.waitFor(() =>
      expect(repositorio.radicadas).toEqual([
        {
          tipo: 'CONSULTA_DATOS',
          correo: 'titular@tecnosport.co',
          pedidoId: null,
          recibidaEn: null,
          asunto: 'Quiero mis datos',
        },
      ]),
    );
  });

  /** Una peticion no admite prorroga: el texto publicado no la menciona para ese plazo. */
  it('solo ofrece prorrogar donde el plazo la admite', async () => {
    await renderBandeja([solicitud({ tipo: 'PETICION' })]);
    fireEvent.click(await screen.findByRole('button', { name: 'Atender' }));

    expect(screen.queryByRole('button', { name: 'Prorrogar el plazo' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Registrar respuesta' })).toBeTruthy();
  });

  it('ofrece prorrogar en una consulta de datos, que si la admite', async () => {
    await renderBandeja([solicitud({ tipo: 'CONSULTA_DATOS' })]);
    fireEvent.click(await screen.findByRole('button', { name: 'Atender' }));

    expect(screen.getByRole('button', { name: 'Prorrogar el plazo' })).toBeTruthy();
  });

  it('responder manda el resumen, que es lo que queda como constancia', async () => {
    const { repositorio } = await renderBandeja([solicitud()]);
    fireEvent.click(await screen.findByRole('button', { name: 'Atender' }));
    fireEvent.input(screen.getByLabelText('Qué se le respondió'), {
      target: { value: 'se reenvio la guia' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Registrar respuesta' }));

    await vi.waitFor(() =>
      expect(repositorio.respondidas).toEqual([
        { solicitudId: 's1', resumen: 'se reenvio la guia' },
      ]),
    );
  });

  it('el filtro de respondidas consulta ese estado y no lo abierto', async () => {
    const { repositorio } = await renderBandeja([solicitud({ estado: 'RESPONDIDA' })]);
    fireEvent.click(await screen.findByRole('button', { name: 'Respondidas' }));

    await vi.waitFor(() => expect(repositorio.filtros).toContain('RESPONDIDA'));
  });

  it('la bandeja no tiene violaciones de WCAG 2.2 AA', async () => {
    const { container } = await renderBandeja([solicitud()]);
    await screen.findByText(/TS-PQR-2026-000001/);

    await esperarSinViolaciones(container);
  });
});
