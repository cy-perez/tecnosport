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
import {
  ExistenciasDelCatalogo,
  InventarioSinMedir,
} from '../productos/domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../productos/domain/repositorio-productos-admin.puerto';
import { RepositorioMedicionFalso } from '../../../../testing/productos-admin';
import { esperarSinViolaciones } from '../../../../testing/axe';
import { PanelAdminPage } from './panel-admin.page';

class RepositorioSesionFalso implements RepositorioSesion {
  llamadasCerrar = 0;

  async iniciarSesion(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }

  async cambiarClave(): Promise<Sesion> {
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
const TODO_CON_EXISTENCIA: ExistenciasDelCatalogo = {
  total: 0,
  totalSinExistencia: 0,
  totalSinExistenciaEnPublicados: 0,
  items: [],
};

async function renderPanel(
  inventario: InventarioSinMedir = NADA_SIN_MEDIR,
  existencias: ExistenciasDelCatalogo = TODO_CON_EXISTENCIA,
) {
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
        useValue: new RepositorioMedicionFalso(inventario, existencias),
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

    // Se ancla en el enlace y no en el rol: la region vive siempre en el DOM desde el primer
    // render, asi que `findByRole('status')` resolveria al instante y vacia, antes de los datos.
    expect(await screen.findByRole('link', { name: esAdmin.panel.sinMedir.enlace })).toBeTruthy();
    expect(screen.getByRole('status').textContent).toContain('8');
  });

  /**
   * El otro vigilante. Avisó del descuadre entre catálogo y libro hasta `ADR-0050`, que borró la
   * columna del catálogo; ahora avisa de lo que sí puede ver un comprador: algo publicado sin una
   * sola unidad en el libro, que en la vitrina se ve agotado.
   */
  it('avisa cuántas variantes no tienen una sola unidad en el libro', async () => {
    await renderPanel(NADA_SIN_MEDIR, {
      total: 12,
      totalSinExistencia: 3,
      totalSinExistenciaEnPublicados: 2,
      items: [],
    });

    expect(
      await screen.findByRole('link', { name: esAdmin.panel.existencias.enlace }),
    ).toBeTruthy();
    expect(screen.getByRole('status').textContent).toContain('3');
  });

  /**
   * El enlace de existencias es permanente y el de sin-medir no, y la diferencia no es un olvido:
   * la lista de existencias nunca está vacía mientras haya catálogo, así que lleva siempre a algo.
   * Lo que desaparece cuando no falta existencia en ningún lado es el aviso.
   */
  it('con todo con existencia no hay aviso, pero el enlace a existencias sigue ahí', async () => {
    await renderPanel();

    await screen.findByRole('button', { name: 'Cerrar sesión' });
    expect(screen.queryByText(esAdmin.panel.existencias.enlace)).toBeNull();
    expect(screen.getByRole('link', { name: esAdmin.panel.ir_a_existencias })).toBeTruthy();
  });

  /**
   * Con la cuenta en cero no hay aviso **ni enlace**, y las dos cosas a la vez son el punto: la
   * pantalla de medir no tiene nada que enseñar, y un enlace permanente a una lista vacía enseña a
   * ignorar el sitio donde algún día sí habrá algo.
   */
  it('sin nada que medir no enseña el aviso ni el enlace', async () => {
    await renderPanel();

    await screen.findByRole('button', { name: 'Cerrar sesión' });
    // La region vive siempre en el DOM, asi que lo que se afirma es que calla y no que no exista:
    // un `role="status"` que nace ya lleno no lo anuncia NVDA (`docs/06-testing.md`).
    expect(screen.getByRole('status').textContent?.trim()).toBe('');
    expect(screen.queryByRole('link', { name: esAdmin.panel.sinMedir.enlace })).toBeNull();
  });

  /**
   * El aviso mira las publicadas y no el total. Un lote de borradores a medio cargar —el estado
   * normal mientras se sube uno— no le puede pasar nada a nadie, y encendía el aviso igual: uno
   * que está siempre encendido deja de leerse, y el día que haya un publicado agotado de verdad
   * nadie lo mira.
   *
   * <p>El aviso de sin-medir se pide a propósito y se espera primero: es lo que demuestra que las
   * consultas del panel ya resolvieron. Sin ese ancla, afirmar que algo no está en pantalla no
   * afirma nada — la primera versión de esta prueba pasaba igual con el defecto puesto.
   */
  it('no enseña el aviso de existencias si las que faltan son todas de borradores', async () => {
    await renderPanel(
      { total: 8, totalEnPublicados: 8, items: [] },
      {
        total: 9,
        totalSinExistencia: 4,
        totalSinExistenciaEnPublicados: 0,
        items: [],
      },
    );

    await screen.findByRole('link', { name: esAdmin.panel.sinMedir.enlace });
    expect(screen.queryByRole('link', { name: esAdmin.panel.existencias.enlace })).toBeNull();
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPanel();
    await screen.findByRole('button', { name: 'Cerrar sesión' });

    await esperarSinViolaciones(container);
  });

  /**
   * La region envuelve las dos cajas en vez de ser cada caja: un `role="status"` que nace ya lleno
   * no lo anuncia NVDA (medido el 22 de septiembre de 2026, `docs/06-testing.md`), y una caja
   * permanente con borde y relleno pintaria una barra vacia. Esta prueba falla si alguien devuelve
   * el `role` a la caja de adentro.
   */
  it('la region viva envuelve el aviso y no es el aviso', async () => {
    await renderPanel({ total: 8, totalEnPublicados: 8, items: [] });

    await screen.findByRole('link', { name: esAdmin.panel.sinMedir.enlace });

    const region = screen.getByRole('status');
    expect(region.textContent).toContain('8');
    expect(region.querySelector('.border')).toBeTruthy();
  });
});
