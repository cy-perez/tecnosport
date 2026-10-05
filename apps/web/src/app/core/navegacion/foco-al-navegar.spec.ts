import { ApplicationRef, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { render } from '@testing-library/angular';
import { pantallaDe, usarFocoAlNavegar } from './foco-al-navegar';

describe('pantallaDe', () => {
  it('quita el idioma, los parámetros de consulta y el fragmento', () => {
    expect(pantallaDe('/es/productos?linea=ROPA#arriba')).toBe('/productos');
    expect(pantallaDe('/en/productos')).toBe('/productos');
    expect(pantallaDe('/es')).toBe('/');
  });
});

@Component({ template: '<h1>Catálogo</h1>' })
class Catalogo {}

@Component({ template: '<h1>Carrito</h1>' })
class Carrito {}

@Component({ template: '<p>Sin título</p>' })
class SinTitulo {}

@Component({
  imports: [RouterOutlet],
  template: '<main id="contenido" tabindex="-1"><router-outlet /></main>',
})
class Anfitrion {
  constructor() {
    usarFocoAlNavegar();
  }
}

/** `afterNextRender` corre en el `tick` de la aplicación, que el fixture no dispara solo. */
async function navegar(router: Router, url: string): Promise<void> {
  await router.navigateByUrl(url);
  TestBed.inject(ApplicationRef).tick();
}

async function montar() {
  await render(Anfitrion, {
    providers: [
      provideRouter([
        { path: ':idioma/productos', component: Catalogo },
        { path: ':idioma/carrito', component: Carrito },
        { path: ':idioma/vacia', component: SinTitulo },
      ]),
    ],
  });
  const router = TestBed.inject(Router);
  await navegar(router, '/es/productos');
  return router;
}

describe('usarFocoAlNavegar', () => {
  it('la primera navegación no mueve el foco: es la carga de la página', async () => {
    await montar();
    await new Promise((resolver) => requestAnimationFrame(resolver));

    expect(document.activeElement).toBe(document.body);
  });

  it('al cambiar de pantalla lleva el foco al título de la nueva', async () => {
    const router = await montar();

    await navegar(router, '/es/carrito');

    await vi.waitFor(() => expect(document.activeElement?.textContent).toBe('Carrito'));
    expect(document.activeElement?.tagName).toBe('H1');
    expect(document.activeElement?.getAttribute('tabindex')).toBe('-1');
  });

  it('sin título, al contenido principal', async () => {
    const router = await montar();

    await navegar(router, '/es/vacia');

    await vi.waitFor(() => expect(document.activeElement?.id).toBe('contenido'));
  });

  /** Filtrar o cambiar de idioma no es cambiar de pantalla: arrancar el foco ahí estorba. */
  it('no se mueve al cambiar solo los parámetros de consulta o el idioma', async () => {
    const router = await montar();
    const boton = document.createElement('button');
    document.body.appendChild(boton);
    boton.focus();

    await navegar(router, '/es/productos?linea=ROPA');
    await navegar(router, '/en/productos?linea=ROPA');
    await new Promise((resolver) => requestAnimationFrame(resolver));

    expect(document.activeElement).toBe(boton);
    boton.remove();
  });
});
