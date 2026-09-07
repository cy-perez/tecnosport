import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
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


describe('PanelAdminPage', () => {
  it('muestra el rol de la sesión activa y cierra sesión al hacer clic', async () => {
    const repositorio = new RepositorioSesionFalso();
    const { fixture } = await render(PanelAdminPage, {
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { es, en, 'admin/es': esAdmin, 'admin/en': enAdmin } as never,
          translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
          preloadLangs: true,
        }),
      ],
      providers: [provideRouter([]), { provide: REPOSITORIO_SESION, useValue: repositorio }],
    });
    const sesionStore = fixture.debugElement.injector.get(SesionStore);
    sesionStore.sesion.set({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt' });
    fixture.detectChanges();
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    expect(screen.getByText('Sesión activa como ADMIN.')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    await vi.waitFor(() => {
      expect(repositorio.llamadasCerrar).toBe(1);
      expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'iniciar-sesion']);
    });
  });
});
