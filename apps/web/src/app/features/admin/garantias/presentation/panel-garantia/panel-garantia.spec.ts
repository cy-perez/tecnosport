import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { MedioReintegro } from '../../../retractos/domain/retracto.model';
import { DesenlaceGarantia, ReclamacionGarantia } from '../../domain/garantia.model';
import {
  REPOSITORIO_GARANTIAS,
  RepositorioGarantias,
} from '../../domain/repositorio-garantias.puerto';
import { PanelGarantia } from './panel-garantia';

function reclamacion(overrides: Partial<ReclamacionGarantia> = {}): ReclamacionGarantia {
  return {
    id: 'g1',
    solicitudId: 's1',
    pedidoId: 'p1',
    varianteId: 'v1',
    entregadoEn: '2026-01-15T15:00:00Z',
    radicadaEn: '2026-06-10T15:00:00Z',
    mesesDeTermino: 12,
    finDelTermino: '2027-01-16T05:00:00Z',
    vigencia: 'CUBIERTA',
    descripcionDelFallo: 'La costura se abrio',
    estado: 'RADICADA',
    desenlace: null,
    resueltaEn: null,
    resueltaPor: null,
    reintegroId: null,
    ...overrides,
  };
}

class RepositorioGarantiasFalso implements RepositorioGarantias {
  radicadas: { pedidoId: string; varianteId: string; descripcionDelFallo: string }[] = [];
  resueltas: {
    reclamacionId: string;
    desenlace: DesenlaceGarantia;
    resumenParaElComprador: string;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }[] = [];

  constructor(private readonly reclamaciones: ReclamacionGarantia[] = []) {}

  async listarDePedido(): Promise<readonly ReclamacionGarantia[]> {
    return this.reclamaciones;
  }

  async radicar(
    pedidoId: string,
    varianteId: string,
    descripcionDelFallo: string,
  ): Promise<ReclamacionGarantia> {
    this.radicadas.push({ pedidoId, varianteId, descripcionDelFallo });
    return reclamacion();
  }

  async resolver(entrada: {
    reclamacionId: string;
    desenlace: DesenlaceGarantia;
    resumenParaElComprador: string;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<ReclamacionGarantia> {
    this.resueltas.push(entrada);
    return reclamacion({ estado: 'RESUELTA', desenlace: entrada.desenlace });
  }
}

async function renderPanel(
  reclamaciones: ReclamacionGarantia[] = [],
  estadoPedido = 'ENTREGADO',
) {
  const repositorio = new RepositorioGarantiasFalso(reclamaciones);
  const resultado = await render(PanelGarantia, {
    inputs: {
      pedidoId: 'p1',
      estadoPedido,
      lineas: [{ varianteId: 'v1', nombre: 'Camiseta running' }],
      totalPedido: 50_000,
    },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_GARANTIAS, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

describe('PanelGarantia', () => {
  it('sobre un pedido entregado ofrece radicar la garantia', async () => {
    await renderPanel([]);

    expect(await screen.findByRole('button', { name: 'Radicar garantía' })).toBeTruthy();
  });

  /** La garantia se cuenta desde la entrega: sin entrega no hay termino que empezar a contar. */
  it('sobre un pedido sin entregar no ofrece nada', async () => {
    await renderPanel([], 'DESPACHADO');

    expect(
      await screen.findByText(
        'La garantía se cuenta desde la entrega: solo aplica a un pedido ya entregado.',
      ),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Radicar garantía' })).toBeNull();
  });

  it('radicar manda la linea elegida y el fallo', async () => {
    const { repositorio } = await renderPanel([]);
    fireEvent.change(await screen.findByLabelText('Producto del pedido'), {
      target: { value: 'v1' },
    });
    fireEvent.input(screen.getByLabelText('Qué falló'), {
      target: { value: 'La costura se abrio' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Radicar garantía' }));

    await vi.waitFor(() =>
      expect(repositorio.radicadas).toEqual([
        { pedidoId: 'p1', varianteId: 'v1', descripcionDelFallo: 'La costura se abrio' },
      ]),
    );
  });

  /**
   * `INDETERMINADA` se explica con palabras. Pintarla como vencida seria negarle un derecho a
   * alguien que quiza lo tiene, que es justo lo que el tercer valor existe para evitar.
   */
  it('explica la vigencia indeterminada en vez de darla por vencida', async () => {
    await renderPanel([
      reclamacion({ vigencia: 'INDETERMINADA', mesesDeTermino: null, finDelTermino: null }),
    ]);

    expect(
      await screen.findByText(
        'El término de esa categoría no está cargado, así que no se puede afirmar que la garantía venció. Decide una persona.',
      ),
    ).toBeTruthy();
    expect(screen.queryByText('Fuera del término')).toBeNull();
  });

  /**
   * Reparar no mueve dinero, asi que ni siquiera ofrece los campos. Sin esta prueba, un formulario
   * que mandara siempre el monto pasaria igual — y dejaria constancias de dinero devuelto donde no
   * salio un peso.
   */
  it('reparar no pide monto y viaja sin datos de dinero', async () => {
    const { repositorio } = await renderPanel([reclamacion()]);
    await screen.findByRole('button', { name: 'Resolver garantía' });

    expect(screen.queryByLabelText('Monto a devolver')).toBeNull();

    fireEvent.input(screen.getByLabelText('Qué se le respondió al comprador'), {
      target: { value: 'Se reparo la costura' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Resolver garantía' }));

    await vi.waitFor(() =>
      expect(repositorio.resueltas).toEqual([
        {
          pedidoId: 'p1',
          reclamacionId: 'g1',
          desenlace: 'REPARACION',
          resumenParaElComprador: 'Se reparo la costura',
          monto: null,
          medio: null,
          comprobante: null,
        },
      ]),
    );
  });

  it('elegir devolver el dinero pide el monto y lo precarga con el total', async () => {
    await renderPanel([reclamacion()]);
    const desenlace = await screen.findByLabelText('Qué se hizo');
    fireEvent.change(desenlace, { target: { value: 'REINTEGRO' } });

    const monto = await screen.findByLabelText<HTMLInputElement>('Monto a devolver');
    expect(monto.value).toBe('50000');
  });

  it('una garantia ya resuelta muestra su desenlace y no ofrece resolverla otra vez', async () => {
    await renderPanel([reclamacion({ estado: 'RESUELTA', desenlace: 'REPOSICION' })]);

    expect(await screen.findByText('Reponer')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Resolver garantía' })).toBeNull();
  });

  it('el panel no tiene violaciones de WCAG 2.2 AA', async () => {
    const { container } = await renderPanel([reclamacion()]);
    await screen.findByRole('button', { name: 'Resolver garantía' });

    await esperarSinViolaciones(container);
  });
});
