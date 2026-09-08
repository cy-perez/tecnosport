import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import { TsBoton, VarianteBoton } from './ts-boton';

// Con contenido proyectado, que es como se usa de verdad: `ng-content` no se
// puede llenar pasando `inputs` a `render(TsBoton)`. Y con señales, no con
// campos sueltos: el proyecto es zoneless, así que asignar un campo normal no
// marca la vista como sucia y el cambio no se pintaría.
@Component({
  imports: [TsBoton],
  template: `
    <ts-boton
      [variante]="variante()"
      [tipo]="tipo()"
      [cargando]="cargando()"
      [deshabilitado]="deshabilitado()"
      [presionado]="presionado()"
      [etiquetaCargando]="etiquetaCargando()"
      [etiquetaAccesible]="etiquetaAccesible()"
      [clase]="clase()"
    >
      Agregar al carrito
    </ts-boton>
  `,
})
class Anfitrion {
  readonly variante = signal<VarianteBoton>('primario');
  readonly tipo = signal<'button' | 'submit'>('button');
  readonly cargando = signal(false);
  readonly deshabilitado = signal(false);
  readonly presionado = signal<boolean | null>(null);
  readonly etiquetaCargando = signal<string | null>(null);
  readonly etiquetaAccesible = signal<string | null>(null);
  readonly clase = signal('');
}

function boton(): HTMLButtonElement {
  return screen.getByRole('button') as HTMLButtonElement;
}

@Component({
  imports: [TsBoton],
  template: `
    <ts-boton variante="primario" [enlace]="['/checkout']">Ir a pagar</ts-boton>
  `,
})
class AnfitrionEnlace {}

describe('TsBoton', () => {
  it('proyecta su contenido como nombre accesible', async () => {
    await render(Anfitrion);

    expect(screen.getByRole('button', { name: 'Agregar al carrito' })).toBeTruthy();
  });

  it('es de tipo button por omisión, para no enviar un formulario sin querer', async () => {
    await render(Anfitrion);

    expect(boton().type).toBe('button');
  });

  // El caso documentado en apps/web/CLAUDE.md: un aria-label puesto en
  // <ts-boton> caería en el host, no en el <button> real.
  it('etiquetaAccesible llega al button real, no al host', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.etiquetaAccesible.set('Aumentar cantidad');
    await fixture.whenStable();

    expect(screen.getByRole('button', { name: 'Aumentar cantidad' })).toBeTruthy();
  });

  it('cargando deshabilita el control y lo anuncia como ocupado', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.cargando.set(true);
    await fixture.whenStable();

    expect(boton().disabled).toBe(true);
    expect(boton().getAttribute('aria-busy')).toBe('true');
  });

  it('cargando reemplaza el contenido por la etiqueta que le pasan', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.cargando.set(true);
    fixture.componentInstance.etiquetaCargando.set('Agregando…');
    await fixture.whenStable();

    expect(screen.getByRole('button', { name: 'Agregando…' })).toBeTruthy();
  });

  it('sin etiquetaCargando conserva su contenido mientras carga', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.cargando.set(true);
    await fixture.whenStable();

    expect(screen.getByRole('button', { name: 'Agregar al carrito' })).toBeTruthy();
  });

  it('deshabilitado también deshabilita el control', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.deshabilitado.set(true);
    await fixture.whenStable();

    expect(boton().disabled).toBe(true);
  });

  it('no expone aria-pressed cuando no es un botón de alternancia', async () => {
    await render(Anfitrion);

    expect(boton().hasAttribute('aria-pressed')).toBe(false);
  });

  it('expone aria-pressed false, que no es lo mismo que no exponerlo', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.presionado.set(false);
    await fixture.whenStable();

    expect(boton().getAttribute('aria-pressed')).toBe('false');
  });

  // Las clases se verifican porque son el estilo: si una variante deja de
  // aplicar su color, no hay nada más que lo atrape.
  it('cada variante aplica su color de fondo', async () => {
    const esperado: Record<VarianteBoton, string> = {
      primario: 'bg-ts-primario',
      secundario: 'bg-transparent',
      texto: 'bg-transparent',
      peligro: 'bg-ts-error',
    };
    const { fixture } = await render(Anfitrion);

    for (const [variante, clase] of Object.entries(esperado)) {
      fixture.componentInstance.variante.set(variante as VarianteBoton);
      await fixture.whenStable();
      expect(boton().className).toContain(clase);
    }
  });

  // docs/04-ui-marca.md: foco visible en el 100% de los enfocables, y el
  // chaflán es la firma de la marca.
  it('conserva el anillo de foco y el chaflán', async () => {
    await render(Anfitrion);

    expect(boton().className).toContain('focus-visible:outline-2');
    expect(boton().className).toContain('chaflan');
  });

  // `min-h-0` no existe en este proyecto (la escala de espacio por omisión
  // está borrada), así que el mínimo táctil se pone por variante en vez de
  // anularse en la base. Esta prueba es la que fija esa decisión.
  it('cumple el objetivo táctil mínimo, salvo la variante de texto', async () => {
    const { fixture } = await render(Anfitrion);
    expect(boton().className).toContain('min-h-tactil');

    fixture.componentInstance.variante.set('texto');
    await fixture.whenStable();
    expect(boton().className).not.toContain('min-h-tactil');
  });

  it('la variante secundaria recupera el borde que la base quita', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.variante.set('secundario');
    await fixture.whenStable();

    expect(boton().className).toContain('border-ts-borde-control');
    expect(boton().className).not.toContain('border-0');
  });

  // MODO ENLACE. Existe porque seis pantallas envolvian <ts-boton> en un <a>
  // para navegar: HTML invalido —<a> no admite contenido interactivo
  // descendiente— y dos paradas de tabulacion por accion. Se encontro
  // recorriendo el carrito en el navegador, no con una prueba.
  it('con destino renderiza un enlace y ningun boton', async () => {
    await render(AnfitrionEnlace, { providers: [provideRouter([])] });

    const enlace = screen.getByRole('link', { name: 'Ir a pagar' });
    expect(enlace.tagName).toBe('A');
    expect(enlace.getAttribute('href')).toBe('/checkout');
    // Lo que de verdad se estaba arreglando: una sola parada de tabulacion.
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('el enlace conserva las clases de su variante, anillo de foco incluido', async () => {
    await render(AnfitrionEnlace, { providers: [provideRouter([])] });

    const clases = screen.getByRole('link', { name: 'Ir a pagar' }).className;
    expect(clases).toContain('bg-ts-primario');
    expect(clases).toContain('chaflan');
    expect(clases).toContain('focus-visible:outline-ts-foco');
  });

  // El contenido proyectado vive en un <ng-template> compartido por las dos
  // ramas. Si esa indireccion no proyectara, el enlace saldria vacio y esta
  // prueba —que lo busca por su nombre accesible— no encontraria nada.
  it('el contenido proyectado llega a la rama de enlace', async () => {
    await render(AnfitrionEnlace, { providers: [provideRouter([])] });

    expect(screen.getByRole('link', { name: 'Ir a pagar' }).textContent?.trim()).toBe(
      'Ir a pagar',
    );
  });

  it('sin destino sigue siendo un boton', async () => {
    await render(Anfitrion);

    expect(screen.queryByRole('link')).toBeNull();
    expect(screen.getByRole('button', { name: 'Agregar al carrito' })).toBeTruthy();
  });

  it('la clase de quien llama gana sobre la base', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.clase.set('px-48');
    await fixture.whenStable();

    expect(boton().className).toContain('px-48');
    expect(boton().className).not.toContain('px-24');
  });
});
