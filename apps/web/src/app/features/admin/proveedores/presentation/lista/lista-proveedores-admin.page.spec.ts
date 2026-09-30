import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { Proveedor } from '../../domain/proveedor.model';
import { REPOSITORIO_PROVEEDORES_ADMIN } from '../../domain/repositorio-proveedores-admin.puerto';
import {
  proveedorDePrueba,
  RepositorioProveedoresAdminFalso,
} from '../apoyo-proveedores.spec-util';
import { ListaProveedoresAdminPage } from './lista-proveedores-admin.page';

async function renderPagina(proveedores: Proveedor[] = []) {
  const repositorio = new RepositorioProveedoresAdminFalso(proveedores);
  const resultado = await render(ListaProveedoresAdminPage, {
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
      { provide: REPOSITORIO_PROVEEDORES_ADMIN, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

describe('ListaProveedoresAdminPage', () => {
  it('lista los proveedores con su línea y su estado', async () => {
    await renderPagina([
      proveedorDePrueba(),
      proveedorDePrueba({ id: 'prov-2', nombre: 'Ropa Itagüí', linea: 'ROPA', activo: false }),
    ]);

    expect(await screen.findByText('Bolsos Medellín')).toBeTruthy();
    expect(screen.getByText('Ropa Itagüí')).toBeTruthy();
    expect(screen.getByText(esAdmin.proveedores.lineas.ROPA)).toBeTruthy();
    expect(screen.getByText(esAdmin.proveedores.inactivo)).toBeTruthy();
  });

  it('sin ninguno lo dice, y ofrece crear el primero', async () => {
    await renderPagina([]);

    expect(await screen.findByText(esAdmin.proveedores.ninguno)).toBeTruthy();
    expect(screen.getByRole('link', { name: esAdmin.proveedores.nuevo })).toBeTruthy();
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina([proveedorDePrueba()]);

    await screen.findByText('Bolsos Medellín');
    await esperarSinViolaciones(container);
  });
});
