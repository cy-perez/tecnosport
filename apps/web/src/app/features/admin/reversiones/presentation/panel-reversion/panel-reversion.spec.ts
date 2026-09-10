import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { MedioReintegro } from '../../../retractos/domain/retracto.model';
import {
  REPOSITORIO_REVERSIONES,
  RepositorioReversiones,
} from '../../domain/repositorio-reversiones.puerto';
import {
  CausalReversion,
  DesenlaceReversion,
  SolicitudReversion,
} from '../../domain/reversion.model';
import { PanelReversion } from './panel-reversion';

function reversion(overrides: Partial<SolicitudReversion> = {}): SolicitudReversion {
  return {
    id: 'r1',
    solicitudId: 's1',
    pedidoId: 'p1',
    causal: 'PRODUCTO_NO_ENTREGADO',
    fechaDelHecho: '2026-09-10T15:00:00Z',
    radicadaEn: '2026-09-12T15:00:00Z',
    verdictoAlRadicar: 'EN_PLAZO',
    estado: 'RADICADA',
    gestionadaEn: null,
    gestionadaPor: null,
    gestion: null,
    desenlace: null,
    resueltaEn: null,
    reintegroId: null,
    ...overrides,
  };
}

class RepositorioReversionesFalso implements RepositorioReversiones {
  radicadas: {
    pedidoId: string;
    causal: CausalReversion;
    fechaDelHecho: string;
    descripcion: string;
  }[] = [];
  gestiones: { reversionId: string; gestion: string }[] = [];
  resueltas: {
    reversionId: string;
    desenlace: DesenlaceReversion;
    resumenParaElComprador: string;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }[] = [];

  constructor(private readonly solicitudes: SolicitudReversion[] = []) {}

  async listarDePedido(): Promise<readonly SolicitudReversion[]> {
    return this.solicitudes;
  }

  async radicar(entrada: {
    pedidoId: string;
    causal: CausalReversion;
    fechaDelHecho: string;
    descripcion: string;
  }): Promise<SolicitudReversion> {
    this.radicadas.push(entrada);
    return reversion();
  }

  async registrarGestion(reversionId: string, gestion: string): Promise<SolicitudReversion> {
    this.gestiones.push({ reversionId, gestion });
    return reversion({ estado: 'GESTIONADA', gestion });
  }

  async resolver(entrada: {
    reversionId: string;
    desenlace: DesenlaceReversion;
    resumenParaElComprador: string;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<SolicitudReversion> {
    this.resueltas.push(entrada);
    return reversion({ estado: 'RESUELTA', desenlace: entrada.desenlace });
  }
}

async function renderPanel(solicitudes: SolicitudReversion[] = []) {
  const repositorio = new RepositorioReversionesFalso(solicitudes);
  const resultado = await render(PanelReversion, {
    inputs: { pedidoId: 'p1', totalPedido: 50_000 },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_REVERSIONES, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

describe('PanelReversion', () => {
  it('ofrece radicar con la causal invocada', async () => {
    const { repositorio } = await renderPanel([]);
    fireEvent.change(await screen.findByLabelText('Causal invocada'), {
      target: { value: 'FRAUDE' },
    });
    fireEvent.input(screen.getByLabelText('Fecha del hecho'), {
      target: { value: '2026-09-10T10:00' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Radicar reversión' }));

    await vi.waitFor(() => expect(repositorio.radicadas.length).toBe(1));
    expect(repositorio.radicadas[0].causal).toBe('FRAUDE');
    expect(repositorio.radicadas[0].pedidoId).toBe('p1');
  });

  /**
   * A diferencia del retracto y la garantia, no hay estado del pedido que lo impida: una de las
   * causales es justamente que el producto nunca llego.
   */
  it('no exige que el pedido este entregado', async () => {
    await renderPanel([]);

    expect(await screen.findByRole('button', { name: 'Radicar reversión' })).toBeTruthy();
  });

  /** "Facilitamos el tramite" es una promesa de conducta: sin texto no queda prueba de nada. */
  it('la gestion pide por escrito que se hizo', async () => {
    const { repositorio } = await renderPanel([reversion()]);
    fireEvent.input(await screen.findByLabelText('Qué se hizo'), {
      target: { value: 'Se radico ante Wompi con el soporte' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Registrar gestión' }));

    await vi.waitFor(() =>
      expect(repositorio.gestiones).toEqual([
        { reversionId: 'r1', gestion: 'Se radico ante Wompi con el soporte' },
      ]),
    );
  });

  /**
   * Si revierte el emisor, la plata vuelve por la red de pagos: el formulario ni siquiera ofrece
   * los campos de dinero, y lo que viaja no lleva monto. Sin esta prueba, un formulario que mandara
   * siempre el monto dejaria constancias de pagos que no hicimos.
   */
  it('si revierte el emisor no pide monto y viaja sin datos de dinero', async () => {
    const { repositorio } = await renderPanel([reversion()]);
    await screen.findByRole('button', { name: 'Resolver reversión' });

    expect(screen.queryByLabelText('Monto devuelto')).toBeNull();

    fireEvent.input(screen.getByLabelText('Qué se le respondió al comprador'), {
      target: { value: 'El emisor confirmo la reversion' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Resolver reversión' }));

    await vi.waitFor(() => expect(repositorio.resueltas.length).toBe(1));
    expect(repositorio.resueltas[0]).toEqual({
      pedidoId: 'p1',
      reversionId: 'r1',
      desenlace: 'REVERTIDO_POR_EL_EMISOR',
      resumenParaElComprador: 'El emisor confirmo la reversion',
      monto: null,
      medio: null,
      comprobante: null,
    });
  });

  it('si devolvemos nosotros pide el monto y lo precarga con el total', async () => {
    await renderPanel([reversion()]);
    fireEvent.change(await screen.findByLabelText('Cómo terminó'), {
      target: { value: 'REINTEGRADO_DIRECTAMENTE' },
    });

    const monto = await screen.findByLabelText<HTMLInputElement>('Monto devuelto');
    expect(monto.value).toBe('50000');
  });

  it('un veredicto indeterminado se explica en vez de darse por vencido', async () => {
    await renderPanel([reversion({ verdictoAlRadicar: 'INDETERMINADO' })]);

    expect(
      await screen.findByText(
        esAdmin.reversiones.verdicto.indeterminado_ayuda,
      ),
    ).toBeTruthy();
  });

  it('una reversion resuelta muestra su desenlace y ya no ofrece resolverla', async () => {
    await renderPanel([
      reversion({ estado: 'RESUELTA', desenlace: 'RECHAZADA', gestion: 'Se radico ante el emisor' }),
    ]);

    expect(await screen.findByText('Rechazada')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Resolver reversión' })).toBeNull();
  });

  it('el panel no tiene violaciones de WCAG 2.2 AA', async () => {
    const { container } = await renderPanel([reversion()]);
    await screen.findByRole('button', { name: 'Resolver reversión' });

    await esperarSinViolaciones(container);
  });
});
