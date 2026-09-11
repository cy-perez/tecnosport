import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { MedioReintegro } from '../../../retractos/domain/retracto.model';
import {
  MotivoCancelacion,
  PedidoAdmin,
  PedidosPaginadosAdmin,
} from '../../domain/pedido-admin.model';
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
import {
  REPOSITORIO_REVERSIONES,
  RepositorioReversiones,
} from '../../../reversiones/domain/repositorio-reversiones.puerto';
import { SolicitudReversion } from '../../../reversiones/domain/reversion.model';
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

/** Y `PanelReversion`, que completa los tres paneles de la fila expandida. */
class RepositorioReversionesVacio implements RepositorioReversiones {
  async listarDePedido(): Promise<readonly SolicitudReversion[]> {
    return [];
  }

  async radicar(): Promise<SolicitudReversion> {
    throw new Error('no usado en estas pruebas');
  }

  async registrarGestion(): Promise<SolicitudReversion> {
    throw new Error('no usado en estas pruebas');
  }

  async resolver(): Promise<SolicitudReversion> {
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
    dineroRecibido: { valor: 50_000, moneda: 'COP' },
    yaDevuelto: { valor: 0, moneda: 'COP' },
    creadoEn: '2026-01-01T12:00:00Z',
    datosTransferencia: null,
    envio: null,
    historial: [],
    plazoDeEntrega: null,
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

  cancelaciones: {
    pedidoId: string;
    motivo: MotivoCancelacion;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }[] = [];

  async cancelar(entrada: {
    pedidoId: string;
    motivo: MotivoCancelacion;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<PedidoAdmin> {
    this.cancelaciones.push(entrada);
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
      { provide: REPOSITORIO_REVERSIONES, useValue: new RepositorioReversionesVacio() },
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

  /**
   * El plazo vencido se ve sin abrir el detalle: quien opera el panel tiene que enterarse de un
   * incumplimiento mirando la lista, no expandiendo pedido por pedido.
   */
  it('un plazo de entrega vencido se marca en la propia fila', async () => {
    await renderLista([
      pedidoDePrueba({
        estado: 'PAGADO',
        plazoDeEntrega: {
          inicio: '2026-01-01T12:00:00Z',
          limite: '2026-01-31T23:59:59Z',
          verdicto: 'VENCIDO',
          avisadoEn: null,
        },
      }),
    ]);

    expect(await screen.findByText('Plazo vencido')).toBeTruthy();
  });

  it('un pedido dentro del plazo no marca nada en la fila', async () => {
    await renderLista([
      pedidoDePrueba({
        estado: 'PAGADO',
        plazoDeEntrega: {
          inicio: '2026-01-01T12:00:00Z',
          limite: '2026-01-31T23:59:59Z',
          verdicto: 'EN_PLAZO',
          avisadoEn: null,
        },
      }),
    ]);
    await screen.findByText('TS-2026-000123');

    expect(screen.queryByText('Plazo vencido')).toBeNull();
  });

  it('el detalle dice que al comprador todavía no se le ha avisado', async () => {
    await renderLista([
      pedidoDePrueba({
        estado: 'PAGADO',
        plazoDeEntrega: {
          inicio: '2026-01-01T12:00:00Z',
          limite: '2026-01-31T23:59:59Z',
          verdicto: 'VENCIDO',
          avisadoEn: null,
        },
      }),
    ]);
    fireEvent.click(await screen.findByRole('button', { name: 'Ver detalle' }));

    expect(await screen.findByText(/Todavía no se le ha avisado/)).toBeTruthy();
    // Y que cancelar lo decide quien compró, no el panel (ADR-0028).
    expect(screen.getByText(/Terminar el contrato lo decide quien compró/)).toBeTruthy();
  });

  it('el detalle dice cuándo se le avisó, si ya se hizo', async () => {
    await renderLista([
      pedidoDePrueba({
        estado: 'PAGADO',
        plazoDeEntrega: {
          inicio: '2026-01-01T12:00:00Z',
          limite: '2026-01-31T23:59:59Z',
          verdicto: 'VENCIDO',
          avisadoEn: '2026-02-01T15:00:00Z',
        },
      }),
    ]);
    fireEvent.click(await screen.findByRole('button', { name: 'Ver detalle' }));

    expect(await screen.findByText(/Avisado el/)).toBeTruthy();
    expect(screen.queryByText(/Todavía no se le ha avisado/)).toBeNull();
  });

  it('un pedido sin plazo arrancado no muestra el bloque', async () => {
    await renderLista([pedidoDePrueba()]);
    fireEvent.click(await screen.findByRole('button', { name: 'Ver detalle' }));
    await screen.findByText(/Camiseta/);

    expect(screen.queryByText('Plazo de entrega')).toBeNull();
  });

  /**
   * Vencido y sin avisar es lo accionable; vencido y ya avisado es seguimiento. Pintarlos igual
   * obligaba a expandir fila por fila para distinguirlos — se vio en el navegador, con dos pedidos
   * sembrados que se veían idénticos.
   */
  it('un plazo vencido al que ya se le avisó no se marca igual que uno sin avisar', async () => {
    await renderLista([
      pedidoDePrueba({
        estado: 'EN_PREPARACION',
        plazoDeEntrega: {
          inicio: '2026-01-01T12:00:00Z',
          limite: '2026-02-01T05:00:00Z',
          verdicto: 'VENCIDO',
          avisadoEn: '2026-02-03T15:00:00Z',
        },
      }),
    ]);

    expect(await screen.findByText('Plazo vencido · ya avisado')).toBeTruthy();
    expect(screen.queryByText('Plazo vencido')).toBeNull();
  });

  /**
   * El límite que manda el servidor es el instante en que el plazo se agota, o sea el comienzo del
   * día siguiente. Pintarlo tal cual decía «1 de septiembre, 12:00 a. m.» y se leía como que había
   * hasta ese día, cuando el último era el 31 de agosto.
   */
  it('el detalle muestra el último día del plazo, no el instante en que se agota', async () => {
    await renderLista([
      pedidoDePrueba({
        estado: 'PAGADO',
        // 2026-09-01 00:00 en Medellín: el plazo se agotó al terminar el 31 de agosto.
        plazoDeEntrega: {
          inicio: '2026-08-01T12:00:00Z',
          limite: '2026-09-01T05:00:00Z',
          verdicto: 'VENCIDO',
          avisadoEn: null,
        },
      }),
    ]);
    fireEvent.click(await screen.findByRole('button', { name: 'Ver detalle' }));

    expect(await screen.findByText(/31 de agosto de 2026/)).toBeTruthy();
    expect(screen.queryByText(/1 de septiembre de 2026/)).toBeNull();
  });

  /**
   * De `DESPACHADO` solo se sale entregando o con el rechazo en la entrega, así que el panel no
   * ofrece cancelación y el texto no puede mandar a cancelar.
   */
  it('a un despachado vencido no se le dice que cancele el pedido', async () => {
    await renderLista([
      pedidoDePrueba({
        estado: 'DESPACHADO',
        plazoDeEntrega: {
          inicio: '2026-01-01T12:00:00Z',
          limite: '2026-02-01T05:00:00Z',
          verdicto: 'VENCIDO',
          avisadoEn: null,
        },
      }),
    ]);
    fireEvent.click(await screen.findByRole('button', { name: 'Ver detalle' }));

    expect(await screen.findByText(/no admite cancelación/)).toBeTruthy();
    expect(screen.queryByText(/cancela el pedido con el motivo/)).toBeNull();
  });

  /**
   * La celda de Estado salía vacía para todo pedido cancelado: `CLAVE_ETIQUETA_ESTADO` no tenía esa
   * entrada. Ninguna prueba lo vio porque ninguna sembraba un cancelado; se vio mirando la pantalla.
   */
  it('un pedido cancelado dice que está cancelado', async () => {
    await renderLista([pedidoDePrueba({ estado: 'CANCELADO' })]);

    expect(await screen.findByRole('cell', { name: 'Cancelado' })).toBeTruthy();
  });

  it('conciliar transferencia llama al repositorio y refresca la lista', async () => {
    const { repositorio } = await renderLista([pedidoDePrueba()]);
    await screen.findByText('TS-2026-000123');
    fireEvent.click(screen.getByRole('button', { name: 'Ver detalle' }));

    fireEvent.click(await screen.findByRole('button', { name: 'Conciliar transferencia' }));
    await vi.waitFor(() => expect(repositorio.llamadasConciliarTransferencia).toEqual(['p1']));
    expect(repositorio.llamadasListar).toBeGreaterThan(1);
  });
  /**
   * El pedido ya tenia el dinero recibido, asi que cancelarlo exige devolverlo: el formulario pide
   * el monto y lo que viaja lo lleva.
   */
  it('cancelar un pedido ya pagado pide el monto y lo manda', async () => {
    const { repositorio } = await renderLista([pedidoDePrueba({ estado: 'PAGADO' })]);
    fireEvent.click(await screen.findByRole('button', { name: 'Ver detalle' }));

    fireEvent.input(await screen.findByLabelText('Monto a devolver'), {
      target: { value: '50000' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar pedido' }));

    await vi.waitFor(() => expect(repositorio.cancelaciones.length).toBe(1));
    expect(repositorio.cancelaciones[0].motivo).toBe('NO_DISPONIBILIDAD');
    expect(repositorio.cancelaciones[0].monto).toBe(50_000);
    expect(repositorio.cancelaciones[0].medio).toBe('WOMPI');
  });

  /**
   * Un contraentrega sin despachar no cobro nada. Ni siquiera se ofrecen los campos, y lo que viaja
   * va sin monto: exigirlo obligaria a inventar un reintegro que nunca ocurrio.
   */
  it('cancelar un contraentrega sin despachar no pide monto y viaja sin dinero', async () => {
    const { repositorio } = await renderLista([
      pedidoDePrueba({ estado: 'CONFIRMADO_CONTRAENTREGA', metodoPago: 'CONTRAENTREGA' }),
    ]);
    fireEvent.click(await screen.findByRole('button', { name: 'Ver detalle' }));
    await screen.findByRole('button', { name: 'Cancelar pedido' });

    expect(screen.queryByLabelText('Monto a devolver')).toBeNull();
    expect(
      screen.getByText('Este pedido todavia no habia cobrado nada, así que no hay dinero que devolver.'),
    ).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar pedido' }));

    await vi.waitFor(() => expect(repositorio.cancelaciones.length).toBe(1));
    expect(repositorio.cancelaciones[0].monto).toBeNull();
    expect(repositorio.cancelaciones[0].medio).toBeNull();
  });

  /** Despues de despachar ya existen los caminos que corresponden: no se ofrece cancelar. */
  it('un pedido despachado no ofrece cancelacion', async () => {
    await renderLista([pedidoDePrueba({ estado: 'DESPACHADO' })]);
    fireEvent.click(await screen.findByRole('button', { name: 'Ver detalle' }));

    expect(screen.queryByRole('button', { name: 'Cancelar pedido' })).toBeNull();
  });
});
