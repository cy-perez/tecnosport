import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { PedidoAdmin, PedidosPaginadosAdmin } from '../../domain/pedido-admin.model';
import {
  REPOSITORIO_PEDIDOS_ADMIN,
  RepositorioPedidosAdmin,
} from '../../domain/repositorio-pedidos-admin.puerto';
import {
  REPOSITORIO_RETRACTOS,
  RepositorioRetractos,
} from '../../../retractos/domain/repositorio-retractos.puerto';
import { SolicitudRetracto } from '../../../retractos/domain/retracto.model';
import {
  REPOSITORIO_GARANTIAS,
  RepositorioGarantias,
} from '../../../garantias/domain/repositorio-garantias.puerto';
import { ReclamacionGarantia } from '../../../garantias/domain/garantia.model';
import { ListaPedidosAdminPage } from './lista-pedidos-admin.page';

/**
 * La fila expandida incluye `PanelRetracto`, que inyecta su propio puerto. Sin este doble, abrir
 * el detalle revienta — y esa es justamente la señal de que el panel quedó enganchado de verdad
 * en la lista y no colgando de una ruta que nadie visita.
 */
class RepositorioRetractosVacio implements RepositorioRetractos {
  async listarDePedido(): Promise<readonly SolicitudRetracto[]> {
    return [];
  }

  async radicar(): Promise<SolicitudRetracto> {
    throw new Error('no usado en estas pruebas');
  }

  async recibirProducto(): Promise<SolicitudRetracto> {
    throw new Error('no usado en estas pruebas');
  }

  async registrarReintegro(): Promise<SolicitudRetracto> {
    throw new Error('no usado en estas pruebas');
  }
}

/** Mismo motivo que el de retractos: `PanelGarantia` tambien vive en la fila expandida. */
class RepositorioGarantiasVacio implements RepositorioGarantias {
  async listarDePedido(): Promise<readonly ReclamacionGarantia[]> {
    return [];
  }

  async radicar(): Promise<ReclamacionGarantia> {
    throw new Error('no usado en estas pruebas');
  }

  async resolver(): Promise<ReclamacionGarantia> {
    throw new Error('no usado en estas pruebas');
  }
}

function pedidoDePrueba(overrides: Partial<PedidoAdmin> = {}): PedidoAdmin {
  return {
    id: 'p1',
    numeroPedido: 'TS-2026-000123',
    usuarioId: null,
    correo: 'cliente@example.com',
    lineas: [
      {
        id: 'l1',
        varianteId: 'v1',
        sku: 'SKU-1',
        nombre: 'Camiseta',
        cantidad: 1,
        precioUnitario: { valor: 50_000, moneda: 'COP' },
        tasaIva: 0.19,
        imagenUrl: null,
      },
    ],
    tipoEntrega: 'ENVIO_A_DOMICILIO',
    direccion: null,
    metodoPago: 'TRANSFERENCIA_MANUAL',
    estado: 'PAGO_PENDIENTE',
    total: { valor: 50_000, moneda: 'COP' },
    creadoEn: '2026-01-01T12:00:00Z',
    datosTransferencia: null,
    envio: null,
    historial: [],
    ...overrides,
  };
}

class RepositorioPedidosAdminFalso implements RepositorioPedidosAdmin {
  llamadasListar = 0;
  llamadasConciliarTransferencia: string[] = [];

  constructor(
    private items: PedidoAdmin[],
    private totalPaginas = 1,
  ) {}

  async listar(): Promise<PedidosPaginadosAdmin> {
    this.llamadasListar++;
    return {
      items: this.items,
      pagina: 0,
      totalPaginas: this.totalPaginas,
      totalPedidos: this.items.length,
    };
  }

  async conciliarTransferencia(pedidoId: string): Promise<PedidoAdmin> {
    this.llamadasConciliarTransferencia.push(pedidoId);
    return this.items[0];
  }

  async verificarContraentrega(): Promise<PedidoAdmin> {
    return this.items[0];
  }

  async despachar(): Promise<PedidoAdmin> {
    return this.items[0];
  }

  async marcarEntregado(): Promise<PedidoAdmin> {
    return this.items[0];
  }

  async rechazarEnEntrega(): Promise<PedidoAdmin> {
    return this.items[0];
  }

  async conciliarRecaudo(): Promise<PedidoAdmin> {
    return this.items[0];
  }
}

// El filtro de estado vive en la URL (ADR-0011): para distinguir "no hay
// pedidos en ese estado" de "todavía no hay pedidos" hay que poder fijarlo,
// mismo recurso que ya usa rejilla.page.spec.ts.
async function renderLista(
  items: PedidoAdmin[],
  queryParams: Record<string, string> = {},
  totalPaginas = 1,
) {
  const repositorio = new RepositorioPedidosAdminFalso(items, totalPaginas);
  const resultado = await render(ListaPedidosAdminPage, {
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
      {
        provide: ActivatedRoute,
        useValue: { queryParams: of(queryParams), snapshot: { queryParams } },
      },
      { provide: REPOSITORIO_PEDIDOS_ADMIN, useValue: repositorio },
      { provide: REPOSITORIO_RETRACTOS, useValue: new RepositorioRetractosVacio() },
      { provide: REPOSITORIO_GARANTIAS, useValue: new RepositorioGarantiasVacio() },
    ],
  });
  return { ...resultado, repositorio };
}


describe('ListaPedidosAdminPage', () => {
  it('lista los pedidos con sus columnas principales', async () => {
    await renderLista([pedidoDePrueba()]);

    expect(await screen.findByText('TS-2026-000123')).toBeTruthy();
    expect(screen.getByText('cliente@example.com')).toBeTruthy();
    expect(screen.getByRole('cell', { name: 'Pago pendiente' })).toBeTruthy();
  });

  it('sin pedidos y sin filtro, lo dice en vez de dejar una tabla vacía', async () => {
    await renderLista([], {}, 0);

    expect(await screen.findByText('Todavía no hay pedidos.')).toBeTruthy();
    // Ni encabezados de tabla ni paginador: una tabla vacía es ruido para un
    // lector de pantalla, y no hay páginas que recorrer.
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Siguiente' })).toBeNull();
  });

  it('sin pedidos pero con filtro de estado, ofrece quitarlo', async () => {
    await renderLista([], { estado: 'PAGADO' }, 0);

    expect(
      await screen.findByText('No hay pedidos en ese estado. Prueba con «Todos».'),
    ).toBeTruthy();
  });

  it('cambiar el filtro de estado navega con ese query param', async () => {
    const { fixture } = await renderLista([pedidoDePrueba()]);
    await screen.findByText('TS-2026-000123');
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.change(screen.getByLabelText('Estado'), { target: { value: 'PAGADO' } });

    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { estado: 'PAGADO' } }),
    );
  });

  it('la paginación deshabilita "Anterior" en la primera página de una sola', async () => {
    await renderLista([pedidoDePrueba()]);
    await screen.findByText('TS-2026-000123');

    expect(screen.getByRole('button', { name: 'Anterior' }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button', { name: 'Siguiente' }).hasAttribute('disabled')).toBe(true);
  });

  it('ver detalle expande la fila con las líneas del pedido', async () => {
    await renderLista([pedidoDePrueba()]);
    await screen.findByText('TS-2026-000123');

    fireEvent.click(screen.getByRole('button', { name: 'Ver detalle' }));

    expect(await screen.findByText(/Camiseta/)).toBeTruthy();
  });

  it('conciliar transferencia llama al repositorio y refresca la lista', async () => {
    const { repositorio } = await renderLista([pedidoDePrueba()]);
    await screen.findByText('TS-2026-000123');
    fireEvent.click(screen.getByRole('button', { name: 'Ver detalle' }));

    fireEvent.click(await screen.findByRole('button', { name: 'Conciliar transferencia' }));
    await vi.waitFor(() => expect(repositorio.llamadasConciliarTransferencia).toEqual(['p1']));
    expect(repositorio.llamadasListar).toBeGreaterThan(1);
  });
});
