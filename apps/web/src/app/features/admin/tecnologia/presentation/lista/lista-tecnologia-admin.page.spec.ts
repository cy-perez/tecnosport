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

async function renderPagina(borradores: BorradorTecnologia[] = []) {
  return render(ListaTecnologiaAdminPage, {
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
      {
        provide: REPOSITORIO_BORRADORES_TECNOLOGIA,
        useValue: new RepositorioBorradoresTecnologiaFalso(borradores),
      },
      {
        provide: REPOSITORIO_PROVEEDORES_ADMIN,
        useValue: new RepositorioProveedoresAdminFalso([proveedorDePrueba()]),
      },
    ],
  });
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

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina([borradorTecnologiaDePrueba()]);

    await screen.findByText('Samsung Galaxy A17 5G');
    await esperarSinViolaciones(container);
  });
});
