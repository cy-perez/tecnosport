import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import {
  REPOSITORIO_RETRACTOS,
  RepositorioRetractos,
} from '../../domain/repositorio-retractos.puerto';
import { MedioReembolso, SolicitudRetracto } from '../../domain/retracto.model';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { PanelRetracto } from './panel-retracto';

function solicitud(overrides: Partial<SolicitudRetracto> = {}): SolicitudRetracto {
  return {
    id: 's1',
    pedidoId: 'p1',
    radicadaEn: '2026-09-14T15:00:00Z',
    radicadaPor: 'admin:1',
    motivo: null,
    verdictoAlRadicar: 'EN_PLAZO',
    estado: 'RADICADA',
    productoRecibidoEn: null,
    limiteDeReintegro: null,
    reembolso: null,
    ...overrides,
  };
}

class RepositorioRetractosFalso implements RepositorioRetractos {
  radicados: { pedidoId: string; motivo: string | null }[] = [];
  recibidos: string[] = [];
  reembolsos: { solicitudId: string; monto: number; medio: MedioReembolso; comprobante: string | null }[] =
    [];

  constructor(private solicitudes: SolicitudRetracto[] = []) {}

  async listarDePedido(): Promise<readonly SolicitudRetracto[]> {
    return this.solicitudes;
  }

  async radicar(pedidoId: string, motivo: string | null): Promise<SolicitudRetracto> {
    this.radicados.push({ pedidoId, motivo });
    return solicitud();
  }

  async recibirProducto(solicitudId: string): Promise<SolicitudRetracto> {
    this.recibidos.push(solicitudId);
    return solicitud({ estado: 'PRODUCTO_RECIBIDO' });
  }

  async registrarReembolso(
    solicitudId: string,
    monto: number,
    medio: MedioReembolso,
    comprobante: string | null,
  ): Promise<SolicitudRetracto> {
    this.reembolsos.push({ solicitudId, monto, medio, comprobante });
    return solicitud({ estado: 'REEMBOLSADA' });
  }
}

async function renderPanel(
  solicitudes: SolicitudRetracto[],
  estadoPedido = 'ENTREGADO',
  totalPedido = 50_000,
) {
  const repositorio = new RepositorioRetractosFalso(solicitudes);
  const resultado = await render(PanelRetracto, {
    inputs: { pedidoId: 'p1', estadoPedido, totalPedido },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_RETRACTOS, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

describe('PanelRetracto', () => {
  it('sobre un pedido entregado y sin solicitudes, ofrece radicar', async () => {
    await renderPanel([]);

    expect(await screen.findByRole('button', { name: 'Radicar retracto' })).toBeTruthy();
  });

  it('sobre un pedido que no se ha entregado, no ofrece nada', async () => {
    await renderPanel([], 'PAGO_PENDIENTE');

    expect(
      await screen.findByText('El retracto solo aplica a un pedido ya entregado.'),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Radicar retracto' })).toBeNull();
  });

  it('un motivo en blanco viaja como nulo, no como cadena vacia', async () => {
    // El articulo 47 concede el retracto sin justificar: guardar "" seria inventar un motivo.
    const { repositorio } = await renderPanel([]);
    fireEvent.click(await screen.findByRole('button', { name: 'Radicar retracto' }));

    await vi.waitFor(() =>
      expect(repositorio.radicados).toEqual([{ pedidoId: 'p1', motivo: null }]),
    );
  });

  it('con una solicitud radicada, el siguiente paso es que vuelva el producto', async () => {
    const { repositorio } = await renderPanel([solicitud()]);

    fireEvent.click(await screen.findByRole('button', { name: 'El producto volvio' }));

    await vi.waitFor(() => expect(repositorio.recibidos).toEqual(['s1']));
  });

  it('un plazo indeterminado no se muestra como vencido', async () => {
    // Es la distincion que sostiene todo el diseno: sin festivos cargados no se puede afirmar
    // que vencio, y decirlo seria negarle un derecho a alguien que quiza esta a tiempo.
    await renderPanel([solicitud({ verdictoAlRadicar: 'INDETERMINADO' })]);

    expect(await screen.findByText('Plazo indeterminado')).toBeTruthy();
    expect(screen.queryByText('Fuera de plazo')).toBeNull();
    expect(
      screen.getByText(
        'Paso el limite mas temprano posible, pero sin el calendario de festivos cargado no se puede afirmar que vencio.',
      ),
    ).toBeTruthy();
  });

  it('con el producto recibido, registra el reembolso con el medio elegido', async () => {
    const { repositorio } = await renderPanel([
      solicitud({
        estado: 'PRODUCTO_RECIBIDO',
        productoRecibidoEn: '2026-09-16T15:00:00Z',
        limiteDeReintegro: '2026-10-02T05:00:00Z',
      }),
    ]);

    const monto = await screen.findByLabelText('Monto a reembolsar');
    fireEvent.input(monto, { target: { value: '50000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Registrar reembolso' }));

    await vi.waitFor(() =>
      expect(repositorio.reembolsos).toEqual([
        {
          solicitudId: 's1',
          monto: 50_000,
          medio: 'TRANSFERENCIA_BANCARIA',
          comprobante: null,
        },
      ]),
    );
  });

  it('con el plazo de reintegro vencido, lo dice', async () => {
    await renderPanel([
      solicitud({
        estado: 'PRODUCTO_RECIBIDO',
        productoRecibidoEn: '2020-01-01T15:00:00Z',
        limiteDeReintegro: '2020-01-16T05:00:00Z',
      }),
    ]);

    expect(
      await screen.findByText('El plazo de quince dias para reintegrar ya vencio.'),
    ).toBeTruthy();
  });

  it('una solicitud ya reembolsada muestra su constancia', async () => {
    await renderPanel([
      solicitud({
        estado: 'REEMBOLSADA',
        reembolso: {
          monto: 50_000,
          medio: 'TRANSFERENCIA_BANCARIA',
          comprobante: 'TRF-9912',
          registradoEn: '2026-09-20T15:00:00Z',
          registradoPor: 'admin:1',
        },
      }),
    ]);

    expect(await screen.findByText(/TRF-9912/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Registrar reembolso' })).toBeNull();
  });

  it('una solicitud rechazada no bloquea radicar otra', async () => {
    await renderPanel([solicitud({ estado: 'RECHAZADA' })]);

    expect(await screen.findByRole('button', { name: 'Radicar retracto' })).toBeTruthy();
  });

  it('el formulario de reembolso no tiene violaciones de WCAG 2.2 AA', async () => {
    const { container } = await renderPanel([
      solicitud({
        estado: 'PRODUCTO_RECIBIDO',
        productoRecibidoEn: '2026-09-16T15:00:00Z',
        limiteDeReintegro: '2026-10-02T05:00:00Z',
      }),
    ]);
    await screen.findByLabelText('Monto a reembolsar');

    await esperarSinViolaciones(container);
  });
});
