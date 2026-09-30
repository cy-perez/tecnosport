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

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina([borradorDePrueba()]);

    await screen.findByText('Bolso tote en cuero sintético');
    await esperarSinViolaciones(container);
  });
});
