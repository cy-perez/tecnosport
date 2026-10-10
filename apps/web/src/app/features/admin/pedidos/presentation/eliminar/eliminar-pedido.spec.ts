import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, within } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { REPOSITORIO_PEDIDOS_ADMIN } from '../../domain/repositorio-pedidos-admin.puerto';
import { EliminarPedido } from './eliminar-pedido';

/** De las doce operaciones del puerto, este componente usa una. */
class EliminacionFalsa {
  readonly eliminados: string[] = [];
  fallo: unknown = null;

  async eliminar(pedidoId: string): Promise<void> {
    if (this.fallo) {
      throw this.fallo;
    }
    this.eliminados.push(pedidoId);
  }
}

async function renderPanel() {
  const repositorio = new EliminacionFalsa();
  const eliminado = vi.fn();
  const resultado = await render(EliminarPedido, {
    inputs: { pedidoId: 'p-1', numeroPedido: 'TS-2026-000123' },
    on: { eliminado },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_PEDIDOS_ADMIN, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio, eliminado };
}

async function abrirYConfirmar() {
  fireEvent.click(screen.getByRole('button', { name: esAdmin.pedidos.eliminar.accion }));
  const caja = within(screen.getByRole('group', { name: '¿Eliminar el pedido TS-2026-000123?' }));
  fireEvent.click(caja.getByRole('button', { name: esAdmin.pedidos.eliminar.confirmar }));
}

describe('EliminarPedido', () => {
  it('pregunta, elimina y avisa con el número para que la lista lo diga', async () => {
    const { repositorio, eliminado } = await renderPanel();

    await abrirYConfirmar();

    await vi.waitFor(() => expect(eliminado).toHaveBeenCalledWith('TS-2026-000123'));
    expect(repositorio.eliminados).toEqual(['p-1']);
  });

  it('cancelar cierra la pregunta sin eliminar', async () => {
    const { repositorio } = await renderPanel();

    fireEvent.click(screen.getByRole('button', { name: esAdmin.pedidos.eliminar.accion }));
    fireEvent.click(screen.getByRole('button', { name: esAdmin.pedidos.eliminar.cancelar }));

    expect(screen.queryByRole('group')).toBeNull();
    expect(repositorio.eliminados).toEqual([]);
  });

  /** Cancelado después de cobrar: el servidor dice por qué, y la pregunta se queda abierta. */
  it('el 409 del servidor se dice con su texto', async () => {
    const { repositorio, eliminado } = await renderPanel();
    repositorio.fallo = new ErrorHttp(409, 'no eliminable', 'PEDIDO_NO_ELIMINABLE');

    await abrirYConfirmar();

    const alerta = await screen.findByText(esAdmin.errores.pedido_no_eliminable);
    expect(alerta.getAttribute('role')).toBe('alert');
    expect(eliminado).not.toHaveBeenCalled();
    expect(screen.getByRole('group')).toBeTruthy();
  });

  it('no tiene violaciones de accesibilidad con la pregunta abierta', async () => {
    const { container } = await renderPanel();

    fireEvent.click(screen.getByRole('button', { name: esAdmin.pedidos.eliminar.accion }));
    await esperarSinViolaciones(container);
  });
});
