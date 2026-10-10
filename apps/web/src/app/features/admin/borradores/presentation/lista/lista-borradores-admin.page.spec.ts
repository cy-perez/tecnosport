import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, within } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { REPOSITORIO_PROVEEDORES_ADMIN } from '../../../proveedores/domain/repositorio-proveedores-admin.puerto';
import {
  proveedorDePrueba,
  RepositorioProveedoresAdminFalso,
} from '../../../proveedores/presentation/apoyo-proveedores.spec-util';
import { Borrador } from '../../domain/borrador.model';
import { REPOSITORIO_BORRADORES_ADMIN } from '../../domain/repositorio-borradores-admin.puerto';
import { borradorDePrueba, RepositorioBorradoresAdminFalso } from '../apoyo-borradores.spec-util';
import { ListaBorradoresAdminPage } from './lista-borradores-admin.page';

async function renderPagina(borradores: Borrador[] = []) {
  const repositorio = new RepositorioBorradoresAdminFalso(borradores);
  const resultado = await render(ListaBorradoresAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_BORRADORES_ADMIN, useValue: repositorio },
      {
        provide: REPOSITORIO_PROVEEDORES_ADMIN,
        useValue: new RepositorioProveedoresAdminFalso([proveedorDePrueba()]),
      },
    ],
  });
  return { ...resultado, repositorio };
}

describe('ListaBorradoresAdminPage', () => {
  it('lista cada borrador con su precio sugerido, su estado y sus alertas', async () => {
    await renderPagina([
      borradorDePrueba(),
      borradorDePrueba({
        id: 'b-2',
        titulo: 'Morral',
        estado: 'RECHAZADO',
        precioVentaSugerido: null,
        alertas: ['SIN_PRECIO', 'CONFIANZA_BAJA'],
      }),
    ]);

    const tabla = within(await screen.findByRole('table'));
    expect(tabla.getByText('Bolso tote en cuero sintético')).toBeTruthy();
    expect(tabla.getByText('$ 72.000')).toBeTruthy();
    expect(tabla.getByText(esAdmin.borradores.estados.RECHAZADO)).toBeTruthy();
    expect(tabla.getByText('2')).toBeTruthy();
    expect(screen.getAllByRole('link', { name: esAdmin.borradores.revisar })).toHaveLength(2);
  });

  it('sin borradores lo dice', async () => {
    await renderPagina([]);

    expect(await screen.findByText(esAdmin.borradores.ninguno)).toBeTruthy();
  });

  it('filtrar por estado navega con el query param y vuelve a la primera página', async () => {
    const { fixture } = await renderPagina([borradorDePrueba()]);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await screen.findByText('Bolso tote en cuero sintético');

    fireEvent.change(screen.getByLabelText(esAdmin.borradores.filtroEstado), {
      target: { value: 'EN_REVISION' },
    });

    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { estado: 'EN_REVISION' } }),
    );
  });

  describe('limpiar la bandeja', () => {
    const sinAprobar = () => [
      borradorDePrueba({ id: 'b-1' }),
      borradorDePrueba({ id: 'b-2', estado: 'RECHAZADO' }),
      borradorDePrueba({ id: 'b-3' }),
      borradorDePrueba({ id: 'b-4', estado: 'APROBADO', productoId: 'p-1' }),
    ];

    it('dice cuántos hay sin aprobar y los borra por tandas hasta terminar', async () => {
      const { repositorio } = await renderPagina(sinAprobar());
      repositorio.tandaDeBorrado = 1;

      fireEvent.click(
        await screen.findByRole('button', { name: esAdmin.borradores.limpiar.accion }),
      );
      const caja = within(screen.getByRole('group', { name: /¿Borrar los 3 borrador/ }));
      fireEvent.click(caja.getByRole('button', { name: esAdmin.borradores.limpiar.confirmar }));

      expect(await screen.findByText('Se borraron 3 borrador(es) sin aprobar.')).toBeTruthy();
      expect(repositorio.tandasPedidas).toBe(3);
      expect(await repositorio.contarSinAprobar()).toBe(0);
      expect(screen.queryByRole('group')).toBeNull();
      expect((await repositorio.listar()).items.map((b) => b.id)).toEqual(['b-4']);
    });

    it('sin borradores sin aprobar no ofrece el botón', async () => {
      await renderPagina([borradorDePrueba({ estado: 'APROBADO', productoId: 'p-1' })]);
      await screen.findByRole('table');

      expect(screen.queryByRole('button', { name: esAdmin.borradores.limpiar.accion })).toBeNull();
    });

    it('cancelar cierra la pregunta sin borrar nada', async () => {
      const { repositorio } = await renderPagina(sinAprobar());

      fireEvent.click(
        await screen.findByRole('button', { name: esAdmin.borradores.limpiar.accion }),
      );
      fireEvent.click(screen.getByRole('button', { name: esAdmin.borradores.limpiar.cancelar }));

      expect(screen.queryByRole('group')).toBeNull();
      expect(repositorio.tandasPedidas).toBe(0);
    });

    it('si una tanda no borra nada deja de pedir, aunque digan que quedan', async () => {
      const { repositorio } = await renderPagina(sinAprobar());
      const pedir = vi
        .spyOn(repositorio, 'eliminarSinAprobar')
        .mockResolvedValue({ eliminados: 0, quedan: 3 });

      fireEvent.click(
        await screen.findByRole('button', { name: esAdmin.borradores.limpiar.accion }),
      );
      fireEvent.click(screen.getByRole('button', { name: esAdmin.borradores.limpiar.confirmar }));

      expect(await screen.findByText(esAdmin.borradores.limpiar.ninguno)).toBeTruthy();
      expect(pedir).toHaveBeenCalledTimes(1);
      expect(screen.getByRole('group')).toBeTruthy();
    });

    it('si falla lo dice y deja la pregunta abierta para reintentar', async () => {
      const { repositorio } = await renderPagina(sinAprobar());
      vi.spyOn(repositorio, 'eliminarSinAprobar').mockRejectedValue(new Error('caído'));

      fireEvent.click(
        await screen.findByRole('button', { name: esAdmin.borradores.limpiar.accion }),
      );
      fireEvent.click(screen.getByRole('button', { name: esAdmin.borradores.limpiar.confirmar }));

      const alerta = await screen.findByText(esAdmin.borradores.limpiar.error);
      expect(alerta.getAttribute('role')).toBe('alert');
      expect(screen.getByRole('group')).toBeTruthy();
    });
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina([borradorDePrueba()]);

    await screen.findByText('Bolso tote en cuero sintético');
    await esperarSinViolaciones(container);
  });
});
