import { Component } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import esAdmin from '../../../../assets/i18n/scopes/admin/es.json';
import { PestanasAdmin, pestanaActiva } from './pestanas-admin';

/** Cualquier URL resuelve a esto: lo que se prueba es la barra, no a dónde lleva. */
@Component({ template: '' })
class Ninguna {}

async function renderPestanas(url = '/es/admin/panel') {
  const resultado = await render(PestanasAdmin, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [provideRouter([{ path: '**', component: Ninguna }])],
  });
  await resultado.fixture.debugElement.injector.get(Router).navigateByUrl(url);
  await resultado.fixture.whenStable();
  return resultado;
}

describe('pestanaActiva', () => {
  it('marca la sección cuando la URL es exactamente la suya', () => {
    expect(pestanaActiva('/es/admin/pedidos')).toBe('admin.panel.ir_a_pedidos');
  });

  /**
   * El caso que `routerLinkActive` no sabe resolver, y el motivo de que esto sea una función:
   * `/admin/productos/existencias` encaja con `Productos` y con `Existencias` a la vez. Gana la
   * más larga, o se encienden dos pestañas.
   */
  it('con dos coincidencias gana la más específica', () => {
    expect(pestanaActiva('/es/admin/productos/existencias')).toBe('admin.panel.ir_a_existencias');
    expect(pestanaActiva('/es/admin/productos/medidas')).toBe('admin.panel.ir_a_medidas');
  });

  /**
   * La otra mitad del mismo problema: con `exact: true` estas dos apagarían Productos, y quien
   * edita un producto sigue estando en productos.
   */
  it('las pantallas hijas siguen marcando su sección', () => {
    expect(pestanaActiva('/es/admin/productos/crear')).toBe('admin.panel.ir_a_productos');
    expect(pestanaActiva('/es/admin/productos/abc-123/editar')).toBe('admin.panel.ir_a_productos');
    expect(pestanaActiva('/es/admin/productos/abc-123/captura-360')).toBe(
      'admin.panel.ir_a_productos',
    );
  });

  it('ignora los parámetros de consulta', () => {
    expect(pestanaActiva('/es/admin/pedidos?estado=PAGADO&pagina=2')).toBe(
      'admin.panel.ir_a_pedidos',
    );
  });

  it('fuera del panel no marca ninguna', () => {
    expect(pestanaActiva('/es/productos')).toBeNull();
    expect(pestanaActiva('/es/admin/iniciar-sesion')).toBeNull();
  });
});

describe('PestanasAdmin', () => {
  /**
   * Las listas de existencias y de medidas se alcanzan siempre, haya o no aviso en el panel. El
   * acceso permanente lo comprobaba `panel-admin.page.spec.ts` mientras los enlaces vivían en esa
   * pantalla; desde que son pestañas, le toca a esta.
   */
  it('ofrece todas las secciones del panel', async () => {
    await renderPestanas();

    for (const nombre of [
      esAdmin.panel.ir_a_panel,
      esAdmin.panel.ir_a_pedidos,
      esAdmin.panel.ir_a_productos,
      esAdmin.panel.ir_a_existencias,
      esAdmin.panel.ir_a_medidas,
      esAdmin.panel.ir_a_marcas,
      esAdmin.panel.ir_a_categorias,
      esAdmin.panel.ir_a_atencion,
      esAdmin.panel.ir_a_envios,
      esAdmin.panel.ir_a_clave,
    ]) {
      expect(screen.getByRole('link', { name: nombre })).toBeTruthy();
    }
  });

  /**
   * `aria-current` y no el color: el fondo elevado y la sombra no los ve quien navega con lector
   * de pantalla, y una barra de diez enlaces sin saber en cuál se está no dice nada.
   */
  it('marca la actual con aria-current y solo esa', async () => {
    await renderPestanas('/es/admin/marcas');

    const actuales = screen
      .getAllByRole('link')
      .filter((enlace) => enlace.getAttribute('aria-current') === 'page');
    expect(actuales).toHaveLength(1);
    expect(actuales[0].textContent?.trim()).toBe(esAdmin.panel.ir_a_marcas);
  });

  it('es un landmark de navegación con nombre', async () => {
    await renderPestanas();

    expect(screen.getByRole('navigation', { name: esAdmin.panel.navegacion })).toBeTruthy();
  });
});
