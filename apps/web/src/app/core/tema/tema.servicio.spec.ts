import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ServicioTema } from './tema.servicio';

/**
 * jsdom no carga `tokens.css`, así que el color de cada tema se pone en el estilo de `<html>` a mano:
 * lo que se prueba es que la meta lleva **lo que diga el token** en el momento de cambiar, no un
 * valor escrito en el servicio.
 */
function ponerSuperficie(valor: string): void {
  document.documentElement.style.setProperty('--color-superficie', valor);
}

function metaColor(): string | null {
  return document.querySelector('meta[name="theme-color"]')?.getAttribute('content') ?? null;
}

describe('ServicioTema y la barra del navegador', () => {
  let servicio: ServicioTema;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: PLATFORM_ID, useValue: 'browser' }] });
    servicio = TestBed.inject(ServicioTema);
  });

  afterEach(() => {
    document.documentElement.style.removeProperty('--color-superficie');
    document.documentElement.removeAttribute('data-tema');
    document.querySelectorAll('meta[name="theme-color"]').forEach((meta) => meta.remove());
  });

  it('al cambiar de tema, la meta toma el color del token, y lo vuelve a tomar al volver', () => {
    ponerSuperficie('rgb(25, 30, 38)');
    servicio.elegir('oscuro');
    expect(metaColor()).toBe('rgb(25, 30, 38)');

    ponerSuperficie('rgb(255, 255, 255)');
    servicio.alternar();
    expect(document.documentElement.getAttribute('data-tema')).toBe('claro');
    expect(metaColor()).toBe('rgb(255, 255, 255)');
  });

  it('reutiliza la meta que trae index.html en vez de añadir otra', () => {
    const meta = document.createElement('meta');
    meta.setAttribute('name', 'theme-color');
    meta.setAttribute('content', 'valor-de-index');
    document.head.appendChild(meta);
    ponerSuperficie('rgb(1, 2, 3)');

    servicio.elegir('claro');

    expect(document.querySelectorAll('meta[name="theme-color"]')).toHaveLength(1);
    expect(metaColor()).toBe('rgb(1, 2, 3)');
  });

  it('sin token que leer no vacía la meta', () => {
    const meta = document.createElement('meta');
    meta.setAttribute('name', 'theme-color');
    meta.setAttribute('content', 'valor-de-index');
    document.head.appendChild(meta);

    servicio.elegir('oscuro');

    expect(metaColor()).toBe('valor-de-index');
  });
});
