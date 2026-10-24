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
import { BorradorTecnologia } from '../../domain/borrador-tecnologia.model';
import { REPOSITORIO_BORRADORES_TECNOLOGIA } from '../../domain/repositorio-borradores-tecnologia.puerto';
import {
  borradorTecnologiaDePrueba,
  RepositorioBorradoresTecnologiaFalso,
} from '../apoyo-tecnologia.spec-util';
import { ListaTecnologiaAdminPage } from './lista-tecnologia-admin.page';

async function renderPagina(
  borradores: BorradorTecnologia[] = [],
  repositorio = new RepositorioBorradoresTecnologiaFalso(borradores),
) {
  const resultado = await render(ListaTecnologiaAdminPage, {
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
      { provide: REPOSITORIO_BORRADORES_TECNOLOGIA, useValue: repositorio },
      {
        provide: REPOSITORIO_PROVEEDORES_ADMIN,
        useValue: new RepositorioProveedoresAdminFalso([proveedorDePrueba()]),
      },
    ],
  });
  return { ...resultado, repositorio };
}

describe('ListaTecnologiaAdminPage', () => {
  it('por omisión lista los que esperan revisión, con sus configuraciones', async () => {
    await renderPagina([
      borradorTecnologiaDePrueba(),
      borradorTecnologiaDePrueba({ id: 'bt-2', titulo: 'JBL Flip 7', estado: 'RECHAZADO' }),
    ]);

    const tabla = within(await screen.findByRole('table'));
    expect(tabla.getByText('Samsung Galaxy A17 5G')).toBeTruthy();
    expect(tabla.queryByText('JBL Flip 7')).toBeNull();
    expect(tabla.getByText('2')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Revisar Samsung Galaxy A17 5G' })).toBeTruthy();
  });

  it('dice cuántas fotos tiene cada modelo y cuántas le faltan', async () => {
    await renderPagina([
      borradorTecnologiaDePrueba({ cantidadDeFotos: 3, fotosQueFaltan: 1, fotos: [] }),
      borradorTecnologiaDePrueba({
        id: 'bt-2',
        titulo: 'Galaxy A17 128GB',
        productoId: 'p-1',
        cantidadDeFotos: 0,
        fotosQueFaltan: 0,
        fotos: [],
      }),
    ]);

    const tabla = within(await screen.findByRole('table'));
    expect(
      tabla.getByRole('columnheader', { name: esAdmin.tecnologia.columnas.fotos }),
    ).toBeTruthy();
    expect(tabla.getByText('3 (faltan 1)')).toBeTruthy();
    expect(tabla.getByText(esAdmin.tecnologia.columnas.fotosNoAplica)).toBeTruthy();
  });

  it('marca los que completan un producto que ya se vende', async () => {
    await renderPagina([borradorTecnologiaDePrueba({ productoId: 'prod-9' })]);

    expect(await screen.findByText(esAdmin.tecnologia.completaProducto)).toBeTruthy();
  });

  it('sin modelos lo dice', async () => {
    await renderPagina([]);

    expect(await screen.findByText(esAdmin.tecnologia.ninguno)).toBeTruthy();
  });

  it('filtrar por estado navega con el query param', async () => {
    const { fixture } = await renderPagina([borradorTecnologiaDePrueba()]);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await screen.findByText('Samsung Galaxy A17 5G');

    fireEvent.change(screen.getByLabelText(esAdmin.tecnologia.filtroEstado), {
      target: { value: 'RECHAZADO' },
    });

    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { estado: 'RECHAZADO' } }),
    );
  });

  describe('borrar los no aprobados (24/10/2026)', () => {
    const LI = esAdmin.tecnologia.limpiar;
    const aprobado = borradorTecnologiaDePrueba({
      id: 'bt-ok',
      titulo: 'OPPO A6k',
      estado: 'APROBADO',
      productoId: 'p-1',
    });
    const sinAprobar = [
      borradorTecnologiaDePrueba(),
      borradorTecnologiaDePrueba({ id: 'bt-2', titulo: 'JBL Flip 7' }),
      borradorTecnologiaDePrueba({ id: 'bt-3', titulo: 'JBL Go 5', estado: 'RECHAZADO' }),
    ];

    it('dice cuántos hay, pide confirmación y los borra por tandas sin tocar los aprobados', async () => {
      const repositorio = new RepositorioBorradoresTecnologiaFalso([...sinAprobar, aprobado]);
      repositorio.tandaDeBorrado = 2;
      await renderPagina([], repositorio);

      expect(await screen.findByText(LI.explicacion.replace('{{cantidad}}', '3'))).toBeTruthy();
      fireEvent.click(screen.getByRole('button', { name: LI.accion }));
      expect(repositorio.eliminados).toEqual([]);
      fireEvent.click(screen.getByRole('button', { name: LI.confirmar }));

      expect(await screen.findByText(LI.hecho.replace('{{borrados}}', '3'))).toBeTruthy();
      expect(repositorio.eliminados).toEqual(['bt-1', 'bt-2', 'bt-3']);
      expect(await repositorio.listar('APROBADO')).toEqual([aprobado]);
    });

    it('cancelar no borra nada', async () => {
      const { repositorio } = await renderPagina(sinAprobar);

      fireEvent.click(await screen.findByRole('button', { name: LI.accion }));
      fireEvent.click(screen.getByRole('button', { name: LI.cancelar }));

      expect(screen.queryByRole('button', { name: LI.confirmar })).toBeNull();
      expect(repositorio.eliminados).toEqual([]);
    });

    it('sin ninguno sin aprobar no ofrece borrar', async () => {
      await renderPagina([aprobado]);

      await screen.findByText(esAdmin.tecnologia.ninguno);
      expect(screen.queryByRole('button', { name: LI.accion })).toBeNull();
    });
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina([borradorTecnologiaDePrueba()]);

    await screen.findByText('Samsung Galaxy A17 5G');
    await esperarSinViolaciones(container);
  });
});
