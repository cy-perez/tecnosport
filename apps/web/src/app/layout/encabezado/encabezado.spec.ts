import { Component } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { REPOSITORIO_SESION } from '../../core/autenticacion/repositorio-sesion.puerto';
import { REPOSITORIO_CARRITO } from '../../features/carrito/domain/repositorio-carrito.puerto';
import { Encabezado } from './encabezado';

// Una ruta comodín, porque las pruebas hacen clic en `routerLink` de verdad:
// con `provideRouter([])` la navegación revienta con NG04002 antes de que
// llegue el manejador que cierra el panel.
@Component({ template: '' })
class PantallaVacia {}

class RepositorioCarritoFalso {
  async obtener() {
    throw new Error('No usado en estas pruebas.');
  }
}

class RepositorioSesionFalso {
  async iniciarSesion() {
    throw new Error('No usado en estas pruebas.');
  }
  async cerrarSesion() {
    return undefined;
  }
  async refrescar() {
    throw new Error('No usado en estas pruebas.');
  }
}

async function renderEncabezado() {
  return render(Encabezado, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en },
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([{ path: '**', component: PantallaVacia }]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_CARRITO, useClass: RepositorioCarritoFalso },
      { provide: REPOSITORIO_SESION, useClass: RepositorioSesionFalso },
    ],
  });
}

function botonMenu(): HTMLButtonElement {
  return screen.getByRole('button', { name: /men/i }) as HTMLButtonElement;
}

describe('Encabezado', () => {
  it('conserva el landmark de banner', async () => {
    const { container } = await renderEncabezado();

    // No es ceremonia: al reestructurar para el menú móvil el `<header>` se
    // perdió por un `<div>` y con él el landmark.
    expect(container.querySelector('header')).toBeTruthy();
  });

  it('el menú arranca cerrado y el botón lo anuncia', async () => {
    await renderEncabezado();

    expect(botonMenu().getAttribute('aria-expanded')).toBe('false');
    expect(botonMenu().getAttribute('aria-controls')).toBe('menu-movil');
    expect(document.getElementById('menu-movil')).toBeNull();
  });

  it('al abrirlo, el estado y la etiqueta cambian con él', async () => {
    const { fixture } = await renderEncabezado();

    expect(botonMenu().getAttribute('aria-label')).toBe('Abrir el menú');

    fireEvent.click(botonMenu());
    await fixture.whenStable();

    expect(botonMenu().getAttribute('aria-expanded')).toBe('true');
    expect(botonMenu().getAttribute('aria-label')).toBe('Cerrar el menú');
    expect(document.getElementById('menu-movil')).toBeTruthy();
  });

  it('el botón alterna: un segundo clic lo cierra', async () => {
    const { fixture } = await renderEncabezado();

    fireEvent.click(botonMenu());
    await fixture.whenStable();
    fireEvent.click(botonMenu());
    await fixture.whenStable();

    expect(document.getElementById('menu-movil')).toBeNull();
  });

  it('Escape cierra el menú', async () => {
    const { fixture } = await renderEncabezado();
    fireEvent.click(botonMenu());
    await fixture.whenStable();

    fireEvent.keyDown(document.getElementById('menu-movil')!, { key: 'Escape' });
    await fixture.whenStable();

    expect(document.getElementById('menu-movil')).toBeNull();
  });

  // Lo que se espera al elegir un enlace: que el panel no se quede abierto
  // encima de la página a la que acabas de navegar.
  it('elegir un enlace del panel lo cierra', async () => {
    const { fixture } = await renderEncabezado();
    fireEvent.click(botonMenu());
    await fixture.whenStable();

    const panel = document.getElementById('menu-movil')!;
    fireEvent.click(panel.querySelector('a')!);
    await fixture.whenStable();

    expect(document.getElementById('menu-movil')).toBeNull();
  });

  // Los enlaces se definen una sola vez en un `ng-template` y se instancian en
  // la barra y en el panel. Con el menú abierto hay dos copias de cada uno, y
  // eso es correcto: una sola está visible según el ancho.
  it('el mismo enlace existe en la barra y en el panel, sin duplicar la plantilla', async () => {
    const { fixture } = await renderEncabezado();
    expect(screen.getAllByRole('link', { name: 'Catálogo' })).toHaveLength(1);

    fireEvent.click(botonMenu());
    await fixture.whenStable();

    expect(screen.getAllByRole('link', { name: 'Catálogo' })).toHaveLength(2);
  });

  it('el carrito se queda en la barra, no baja al menú', async () => {
    await renderEncabezado();

    const carrito = screen.getByRole('link', { name: /Carrito/ });
    expect(carrito.closest('#menu-movil')).toBeNull();
  });

  it('el botón de menú cumple el objetivo táctil y desaparece en escritorio', async () => {
    await renderEncabezado();

    expect(botonMenu().className).toContain('min-h-tactil');
    expect(botonMenu().className).toContain('min-w-tactil');
    // jsdom no evalúa media queries: se comprueba que la clase esté puesta.
    expect(botonMenu().className).toContain('desde-movil:hidden');
  });

  // La animación de salida se probó en el navegador, que es donde importa;
  // esto fija el camino que estuvo roto: con movimiento reducido **no se anima
  // nada**, se elimina y punto. Reducir la duración a 0,01 ms dejaba una
  // animación real corriendo, y con ciclos rápidos el panel llegó a quedarse
  // montado — un menú que no se cierra es peor que uno sin animación.
  it('con movimiento reducido, el panel se elimina sin animarse', async () => {
    document.documentElement.setAttribute('data-movimiento', 'reducido');
    try {
      const { fixture } = await renderEncabezado();
      fireEvent.click(botonMenu());
      await fixture.whenStable();
      expect(document.getElementById('menu-movil')).toBeTruthy();

      fireEvent.click(botonMenu());
      await fixture.whenStable();

      expect(document.getElementById('menu-movil')).toBeNull();
    } finally {
      document.documentElement.removeAttribute('data-movimiento');
    }
  });
});
