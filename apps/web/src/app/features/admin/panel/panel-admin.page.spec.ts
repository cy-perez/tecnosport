import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../assets/i18n/en.json';
import es from '../../../../assets/i18n/es.json';
import enAdmin from '../../../../assets/i18n/scopes/admin/en.json';
import esAdmin from '../../../../assets/i18n/scopes/admin/es.json';
import {
  REPOSITORIO_SESION,
  RepositorioSesion,
} from '../../../core/autenticacion/repositorio-sesion.puerto';
import { SesionStore } from '../../../core/autenticacion/sesion.store';
import { Sesion } from '../../../core/autenticacion/sesion.model';
import { InventarioSinMedir } from '../productos/domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../productos/domain/repositorio-productos-admin.puerto';
import { RepositorioMedicionFalso } from '../../../../testing/productos-admin';
import { PanelAdminPage } from './panel-admin.page';

class RepositorioSesionFalso implements RepositorioSesion {
  llamadasCerrar = 0;

  async iniciarSesion(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }

  async refrescar(): Promise<Sesion | null> {
    return null;
  }

  async cerrarSesion(): Promise<void> {
    this.llamadasCerrar++;
  }
}

const NADA_SIN_MEDIR: InventarioSinMedir = { total: 0, totalEnPublicados: 0, items: [] };

async function renderPanel(inventario: InventarioSinMedir = NADA_SIN_MEDIR) {
  const sesion = new RepositorioSesionFalso();
  const resultado = await render(PanelAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin, 'admin/en': enAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_SESION, useValue: sesion },
      {
        provide: REPOSITORIO_PRODUCTOS_ADMIN,
        useValue: new RepositorioMedicionFalso(inventario),
      },
    ],
  });
  return { ...resultado, sesion };
}

describe('PanelAdminPage', () => {
  it('muestra el rol de la sesión activa y cierra sesión al hacer clic', async () => {
    const { fixture, sesion } = await renderPanel();
    const sesionStore = fixture.debugElement.injector.get(SesionStore);
    sesionStore.sesion.set({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt' });
    fixture.detectChanges();
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    expect(screen.getByText('Sesión activa como ADMIN.')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    await vi.waitFor(() => {
      expect(sesion.llamadasCerrar).toBe(1);
      expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'iniciar-sesion']);
    });
  });

  /**
   * El vigilante de lo que falta por medir. Nada falla cuando una variante no tiene medidas —se
   * vende igual, solo que con recogida en el punto—, así que sin este aviso el estado "temporal" se
   * queda para siempre y nadie se entera.
   */
  it('avisa cuántas variantes están sin medir y cuántas ya están publicadas', async () => {
    await renderPanel({
      total: 8,
      totalEnPublicados: 8,
      items: [],
    });

    const aviso = await screen.findByRole('status');
    expect(aviso.textContent).toContain('8');
    expect(screen.getByRole('link', { name: esAdmin.panel.sinMedir.enlace })).toBeTruthy();
  });

  /**
   * Con la cuenta en cero no hay aviso **ni enlace**, y las dos cosas a la vez son el punto: la
   * pantalla de medir no tiene nada que enseñar, y un enlace permanente a una lista vacía enseña a
   * ignorar el sitio donde algún día sí habrá algo.
   */
  it('sin nada que medir no enseña el aviso ni el enlace', async () => {
    await renderPanel();

    await screen.findByRole('button', { name: 'Cerrar sesión' });
    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.queryByRole('link', { name: esAdmin.panel.sinMedir.enlace })).toBeNull();
  });
});
