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
      [expandido]="expandido()"
      [controla]="controla()"
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
  readonly expandido = signal<boolean | null>(null);
  readonly controla = signal<string | null>(null);
  readonly etiquetaCargando = signal<string | null>(null);
  readonly etiquetaAccesible = signal<string | null>(null);
  readonly clase = signal('');
}

function boton(): HTMLButtonElement {
  return screen.getByRole('button') as HTMLButtonElement;
}

@Component({
  imports: [TsBoton],
  template: ` <ts-boton variante="primario" [enlace]="['/checkout']">Ir a pagar</ts-boton> `,
})
class AnfitrionEnlace {}

// `ocupado` no está en el anfitrión de arriba a propósito: ese ya liga once entradas y añadirle
// una duodécima señal solo para una prueba lo vuelve ilegible. Aquí va fijo en `true`.
@Component({
  imports: [TsBoton],
  template: ` <ts-boton [ocupado]="true">Publicar</ts-boton> `,
})
class AnfitrionOcupado {}

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

  /**
   * El anillo entra **haya o no `etiquetaCargando`**, y ese es el caso que importa: en los botones
   * que cargan sin cambiar de texto —la mayoría de las acciones de fila del panel— el `aria-busy`
   * lo dice todo para quien usa lector de pantalla y **nada** para quien mira la pantalla. Antes de
   * que existiera `ts-cargando`, un botón ocupado se veía exactamente igual que uno en reposo.
   */
  it('mientras carga pinta el anillo, con o sin etiqueta propia', async () => {
    const { fixture } = await render(Anfitrion);
    expect(boton().querySelector('ts-cargando')).toBeNull();

    fixture.componentInstance.cargando.set(true);
    await fixture.whenStable();
    expect(boton().querySelector('ts-cargando')).toBeTruthy();

    fixture.componentInstance.etiquetaCargando.set('Agregando…');
    await fixture.whenStable();
    expect(boton().querySelector('ts-cargando')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Agregando…' })).toBeTruthy();
  });

  /**
   * `ocupado` marca el botón sin deshabilitarlo —es lo que usan las acciones de fila para no
   * perder el foco—, así que también tiene que enseñar el anillo: si no, ese caso se queda otra vez
   * sin señal visible.
   */
  it('ocupado también pinta el anillo, sin deshabilitar el control', async () => {
    const { fixture } = await render(AnfitrionOcupado);
    await fixture.whenStable();

    expect(boton().querySelector('ts-cargando')).toBeTruthy();
    expect(boton().disabled).toBe(false);
    expect(boton().getAttribute('aria-busy')).toBe('true');
  });

  it('deshabilitado también deshabilita el control', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.deshabilitado.set(true);
    await fixture.whenStable();

    expect(boton().disabled).toBe(true);
  });

  // Mismo caso que `etiquetaAccesible`: un `[attr.aria-expanded]` en
  // `<ts-boton>` caería en el host y el `<button>` real no lo llevaría.
  it('expandido y controla llegan al button real como aria-expanded y aria-controls', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.expandido.set(false);
    fixture.componentInstance.controla.set('filtros');
    await fixture.whenStable();

    expect(boton().getAttribute('aria-expanded')).toBe('false');
    expect(boton().getAttribute('aria-controls')).toBe('filtros');

    fixture.componentInstance.expandido.set(true);
    await fixture.whenStable();

    expect(boton().getAttribute('aria-expanded')).toBe('true');
  });

  it('sin expandido no pinta aria-expanded: no todo botón es un disclosure', async () => {
    await render(Anfitrion);

    expect(boton().hasAttribute('aria-expanded')).toBe(false);
    expect(boton().hasAttribute('aria-controls')).toBe(false);
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
      acento: 'bg-ts-acento',
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
  // Esta prueba afirmaba lo contrario —"salvo la variante de texto"— y hacía
  // bien su trabajo: falló en cuanto se le dio el mínimo a `texto`, que es el
  // cambio que se quería. La exención venía del SCSS y se tradujo sin
  // revisarla; medida en el navegador a 380 px producía "Limpiar filtros" en
  // 126 x 37 y "Eliminar" del carrito en 85 x 37.
  // `docs/04-ui-marca.md` pide 44 px sin distinguir variantes.
  it('las cinco variantes cumplen el objetivo táctil mínimo', async () => {
    const { fixture } = await render(Anfitrion);

    for (const variante of ['primario', 'secundario', 'texto', 'peligro', 'acento'] as const) {
      fixture.componentInstance.variante.set(variante);
      await fixture.whenStable();

      expect(boton().className, `variante ${variante}`).toContain('min-h-tactil');
    }
  });

  /**
   * El motivo de que `acento` sea una variante y no un `clase="bg-ts-acento"` de quien llama: en
   * tema oscuro `--color-foco` y `--color-acento` son el mismo ámbar, así que el anillo de `BASE`
   * sería invisible justo sobre este fondo. Que `cn` descarte el de la base y deje este es lo que
   * la prueba fija; que el color contraste de verdad lo vigila `npm run contrastes`.
   */
  it('la variante de acento cambia el anillo de foco al color de encima del ámbar', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.variante.set('acento');
    await fixture.whenStable();

    expect(boton().className).toContain('focus-visible:outline-ts-sobre-acento');
    expect(boton().className).not.toContain('focus-visible:outline-ts-foco');
  });

  it('la variante secundaria pinta el borde con el color de control', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.variante.set('secundario');
    await fixture.whenStable();

    expect(boton().className).toContain('border-ts-borde-control');
    expect(boton().className).not.toContain('border-transparent');
  });

  // Medido en el navegador: con `border-0` en la base y `border` solo en la
  // secundaria, la opción elegida del selector de variante (primaria) medía
  // 44 px y las demás 46, un escalón en la misma fila. El borde de 1 px es
  // de todas las variantes; solo cambia su color.
  it('las cinco variantes llevan el mismo borde de 1 px, pintado o transparente', async () => {
    const { fixture } = await render(Anfitrion);

    for (const variante of ['primario', 'secundario', 'texto', 'peligro', 'acento'] as const) {
      fixture.componentInstance.variante.set(variante);
      await fixture.whenStable();

      const clases = boton().className.split(' ');
      expect(clases, `variante ${variante}`).toContain('border');
      expect(clases, `variante ${variante}`).not.toContain('border-0');
    }
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

    expect(screen.getByRole('link', { name: 'Ir a pagar' }).textContent?.trim()).toBe('Ir a pagar');
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
