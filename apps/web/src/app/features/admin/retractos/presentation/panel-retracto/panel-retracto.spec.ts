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
import { MedioReintegro, SolicitudRetracto } from '../../domain/retracto.model';
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
    medioPreferido: null,
    preferenciaRespetada: null,
    reintegro: null,
    ...overrides,
  };
}

class RepositorioRetractosFalso implements RepositorioRetractos {
  radicados: {
    pedidoId: string;
    motivo: string | null;
    medioPreferido: MedioReintegro | null;
  }[] = [];
  recibidos: string[] = [];
  reintegros: {
    solicitudId: string;
    monto: number;
    medio: MedioReintegro;
    medioPreferido: MedioReintegro | null;
    comprobante: string | null;
  }[] = [];

  constructor(private solicitudes: SolicitudRetracto[] = []) {}

  async listarDePedido(): Promise<readonly SolicitudRetracto[]> {
    return this.solicitudes;
  }

  async radicar(
    pedidoId: string,
    motivo: string | null,
    medioPreferido: MedioReintegro | null,
  ): Promise<SolicitudRetracto> {
    this.radicados.push({ pedidoId, motivo, medioPreferido });
    return solicitud();
  }

  async recibirProducto(solicitudId: string): Promise<SolicitudRetracto> {
    this.recibidos.push(solicitudId);
    return solicitud({ estado: 'PRODUCTO_RECIBIDO' });
  }

  async registrarReintegro(
    solicitudId: string,
    monto: number,
    medio: MedioReintegro,
    medioPreferido: MedioReintegro | null,
    comprobante: string | null,
  ): Promise<SolicitudRetracto> {
    this.reintegros.push({ solicitudId, monto, medio, medioPreferido, comprobante });
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
      expect(repositorio.radicados).toEqual([
        { pedidoId: 'p1', motivo: null, medioPreferido: null },
      ]),
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
        esAdmin.retractos.verdicto.indeterminado_ayuda,
      ),
    ).toBeTruthy();
  });

  it('con el producto recibido, registra el reintegro con el medio elegido', async () => {
    const { repositorio } = await renderPanel([
      solicitud({
        estado: 'PRODUCTO_RECIBIDO',
        productoRecibidoEn: '2026-09-16T15:00:00Z',
        limiteDeReintegro: '2026-10-02T05:00:00Z',
      }),
    ]);

    const monto = await screen.findByLabelText('Monto a reembolsar');
    fireEvent.input(monto, { target: { value: '50000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Registrar reintegro' }));

    await vi.waitFor(() =>
      expect(repositorio.reintegros).toEqual([
        {
          solicitudId: 's1',
          monto: 50_000,
          medio: 'TRANSFERENCIA_BANCARIA',
          medioPreferido: null,
          comprobante: null,
        },
      ]),
    );
  });

  // ---- El medio que pidio el comprador (Ley 2439 de 2024) ----

  it('al radicar, manda el medio que pidio el comprador', async () => {
    const { repositorio } = await renderPanel([]);
    const preferido = await screen.findByLabelText(
      esAdmin.retractos.acciones.medio_preferido,
    );

    fireEvent.change(preferido, { target: { value: 'EFECTIVO' } });
    fireEvent.click(screen.getByRole('button', { name: 'Radicar retracto' }));

    await vi.waitFor(() =>
      expect(repositorio.radicados).toEqual([
        { pedidoId: 'p1', motivo: null, medioPreferido: 'EFECTIVO' },
      ]),
    );
  });

  /**
   * La advertencia tiene que salir **mientras se elige**, no al guardar: quien atiende tiene que
   * poder cambiar de idea antes de mover la plata. No bloquea — puede haber un motivo real, como
   * una cuenta que rebota.
   */
  it('advierte si el medio elegido no es el que pidio el comprador, y no bloquea', async () => {
    const { repositorio } = await renderPanel([
      solicitud({
        estado: 'PRODUCTO_RECIBIDO',
        productoRecibidoEn: '2026-09-16T15:00:00Z',
        medioPreferido: 'TRANSFERENCIA_BANCARIA',
      }),
    ]);

    const medio = await screen.findByLabelText(esAdmin.retractos.acciones.medio);
    fireEvent.change(medio, { target: { value: 'EFECTIVO' } });

    expect(await screen.findByRole('alert')).toBeTruthy();

    const monto = screen.getByLabelText(esAdmin.retractos.acciones.monto);
    fireEvent.input(monto, { target: { value: '50000' } });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.retractos.acciones.registrar_reintegro }));

    await vi.waitFor(() => expect(repositorio.reintegros).toHaveLength(1));
  });

  /** Ya anotado, el backend se niega a cambiarlo: ofrecer el control invitaria a intentarlo. */
  it('con la preferencia ya anotada, no ofrece volver a elegirla', async () => {
    await renderPanel([
      solicitud({
        estado: 'PRODUCTO_RECIBIDO',
        productoRecibidoEn: '2026-09-16T15:00:00Z',
        medioPreferido: 'WOMPI',
      }),
    ]);

    await screen.findByLabelText(esAdmin.retractos.acciones.medio);
    expect(screen.queryByLabelText(esAdmin.retractos.acciones.medio_preferido)).toBeNull();
  });

  /** Y despues del hecho queda leible, que es lo que sirve el dia de la reclamacion. */
  it('si el reintegro no respeto la preferencia, lo deja dicho', async () => {
    await renderPanel([
      solicitud({
        estado: 'REEMBOLSADA',
        medioPreferido: 'TRANSFERENCIA_BANCARIA',
        preferenciaRespetada: false,
        reintegro: {
          monto: 50_000,
          medio: 'EFECTIVO',
          comprobante: null,
          registradoEn: '2026-09-20T15:00:00Z',
          registradoPor: 'admin:1',
        },
      }),
    ]);

    expect(await screen.findByText(esAdmin.retractos.preferencia_no_respetada)).toBeTruthy();
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

  /**
   * El mismo aviso, pero el día en que el plazo se acaba de agotar. Fechas relativas al reloj y no
   * fijas, a propósito: lo que se prueba es la distancia al límite, y con una fecha fija el caso
   * dejaría de ser el que interesa en cuanto pasara el tiempo.
   *
   * Esta prueba nació de un defecto real que la de arriba no podía atrapar. `diasParaReintegrar`
   * era `Math.ceil((limite - ahora) / 86_400_000)`, y con el límite vencido hace unas horas eso da
   * `-0`: la plantilla lo escondía todo porque `@if (dias; as ...)` lo ve como falso, y
   * `plazoVencido` decía que no porque `-0 < 0` también es falso. Durante las primeras
   * veinticuatro horas de incumplimiento el panel no decía nada. La prueba de arriba usa el año
   * 2020, o sea miles de días negativos, y pasaba tan tranquila.
   */
  it('un plazo vencido hace horas también lo dice', async () => {
    const haceCincoHoras = new Date(Date.now() - 5 * 60 * 60 * 1000).toISOString();
    await renderPanel([
      solicitud({
        estado: 'PRODUCTO_RECIBIDO',
        productoRecibidoEn: new Date(Date.now() - 16 * 24 * 60 * 60 * 1000).toISOString(),
        limiteDeReintegro: haceCincoHoras,
      }),
    ]);

    expect(
      await screen.findByText('El plazo de quince dias para reintegrar ya vencio.'),
    ).toBeTruthy();
  });

  /** Y con el plazo vivo dice cuánto queda, que es la otra mitad y no tenía prueba propia. */
  it('con el plazo de reintegro corriendo, dice cuántos días quedan', async () => {
    const enTresDias = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000 - 60_000).toISOString();
    await renderPanel([
      solicitud({
        estado: 'PRODUCTO_RECIBIDO',
        productoRecibidoEn: new Date(Date.now() - 12 * 24 * 60 * 60 * 1000).toISOString(),
        limiteDeReintegro: enTresDias,
      }),
    ]);

    expect(
      await screen.findByText('Quedan 3 dias calendario para reintegrar el dinero.'),
    ).toBeTruthy();
  });

  it('una solicitud ya reembolsada muestra su constancia', async () => {
    await renderPanel([
      solicitud({
        estado: 'REEMBOLSADA',
        reintegro: {
          monto: 50_000,
          medio: 'TRANSFERENCIA_BANCARIA',
          comprobante: 'TRF-9912',
          registradoEn: '2026-09-20T15:00:00Z',
          registradoPor: 'admin:1',
        },
      }),
    ]);

    expect(await screen.findByText(/TRF-9912/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Registrar reintegro' })).toBeNull();
  });

  it('una solicitud rechazada no bloquea radicar otra', async () => {
    await renderPanel([solicitud({ estado: 'RECHAZADA' })]);

    expect(await screen.findByRole('button', { name: 'Radicar retracto' })).toBeTruthy();
  });

  it('el formulario de reintegro no tiene violaciones de WCAG 2.2 AA', async () => {
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
