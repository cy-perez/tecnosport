import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { describe, expect, it, vi } from 'vitest';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { REPOSITORIO_SESION } from '../../core/autenticacion/repositorio-sesion.puerto';
import { Categoria } from '../../features/catalogo/domain/producto.model';
import {
  REPOSITORIO_CATEGORIAS,
  RepositorioCategorias,
} from '../../features/catalogo/domain/repositorio-categorias.puerto';
import { MenuLateral } from './menu-lateral';

/**
 * El árbol de prueba tiene las tres formas que importan: una hoja de primer nivel (Celulares), una
 * rama con hojas debajo (Dama › Blusas / Busos) y una línea sin nada cargado (Calzado).
 */
const ARBOL: Categoria[] = [
  { id: 'c1', nombre: 'Celulares', slug: 'celulares', linea: 'TECNOLOGIA', padreId: null },
  { id: 'd0', nombre: 'Dama', slug: 'ropa-dama', linea: 'ROPA', padreId: null },
  { id: 'd1', nombre: 'Blusas', slug: 'ropa-dama-blusas', linea: 'ROPA', padreId: 'd0' },
  { id: 'd2', nombre: 'Busos', slug: 'ropa-dama-busos', linea: 'ROPA', padreId: 'd0' },
];

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return ARBOL;
  }
}

class RepositorioSesionFalso {
  async iniciarSesion() {
    throw new Error('No usado en estas pruebas.');
  }
  async cambiarClave() {
    throw new Error('No usado en estas pruebas.');
  }
  async cerrarSesion() {
    return undefined;
  }
  async refrescar() {
    return null;
  }
}

async function renderMenu() {
  return render(MenuLateral, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en },
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_CATEGORIAS, useClass: RepositorioCategoriasFalso },
      { provide: REPOSITORIO_SESION, useClass: RepositorioSesionFalso },
    ],
  });
}

function panel(): HTMLElement {
  return screen.getByRole('navigation', { name: 'Menú del sitio' });
}

function botonDe(nombre: string | RegExp): HTMLButtonElement {
  return screen.getByRole('button', { name: nombre }) as HTMLButtonElement;
}

describe('MenuLateral', () => {
  it('es un landmark de navegación con nombre propio', async () => {
    await renderMenu();

    // Con dos `<nav>` en la página —este y el del encabezado— cada uno tiene que decir cuál es, o
    // un lector de pantalla ofrece "navegación" dos veces sin distinguirlas.
    expect(panel()).toBeTruthy();
  });

  it('arranca recogido y se despliega al acercar el puntero', async () => {
    const { fixture } = await renderMenu();
    expect(panel().className).toContain('w-menu-riel');

    fireEvent.mouseEnter(panel());
    await fixture.whenStable();

    expect(panel().className).toContain('w-menu-lateral');
  });

  it('se recoge al retirar el puntero', async () => {
    const { fixture } = await renderMenu();
    fireEvent.mouseEnter(panel());
    await fixture.whenStable();

    fireEvent.mouseLeave(panel());
    await fixture.whenStable();

    expect(panel().className).toContain('w-menu-riel');
  });

  /**
   * La regla de accesibilidad del proyecto, y el motivo de que el estado sea una señal y no un
   * `:hover` de CSS: un desplegable que solo responde al ratón no lo puede usar quien navega con
   * Tab. Es lo que se podía romper sin que nadie lo notara, porque con el ratón se ve bien.
   */
  it('también se despliega cuando el foco entra por teclado', async () => {
    const { fixture } = await renderMenu();

    fireEvent.focusIn(botonDe('Catálogo'));
    await fixture.whenStable();

    expect(panel().className).toContain('w-menu-lateral');
  });

  /**
   * `focusout` dispara también al pasar el foco de un hijo a otro dentro del menú: recoger ahí
   * cerraría el panel en mitad de un recorrido con Tab.
   */
  it('no se recoge si el foco solo salta de un control a otro de dentro', async () => {
    const { fixture } = await renderMenu();
    fireEvent.focusIn(botonDe('Catálogo'));
    await fixture.whenStable();

    fireEvent.focusOut(botonDe('Catálogo'), { relatedTarget: botonDe('Tecnología') });
    await fixture.whenStable();

    expect(panel().className).toContain('w-menu-lateral');
  });

  it('se recoge cuando el foco se va de verdad', async () => {
    const { fixture } = await renderMenu();
    fireEvent.focusIn(botonDe('Catálogo'));
    await fixture.whenStable();

    fireEvent.focusOut(botonDe('Catálogo'), { relatedTarget: document.body });
    await fixture.whenStable();

    expect(panel().className).toContain('w-menu-riel');
  });

  /**
   * Fijar es una decisión y desplegar es un gesto: lo primero no se deshace solo. Es lo que
   * distingue este botón de acercar el puntero, y lo que se rompería sin darse cuenta si alguien
   * simplificara los dos estados en uno.
   */
  it('con el menú fijado, retirar el puntero no lo recoge', async () => {
    const { fixture } = await renderMenu();

    fireEvent.click(botonDe(/Fijar el menú/));
    await fixture.whenStable();
    expect(panel().className).toContain('w-menu-lateral');

    fireEvent.mouseLeave(panel());
    await fixture.whenStable();

    expect(panel().className).toContain('w-menu-lateral');
  });

  it('el botón de fijar es un interruptor y anuncia su estado', async () => {
    const { fixture } = await renderMenu();

    const fijar = botonDe(/Fijar el menú/);
    // `aria-pressed` y no `aria-expanded`: esto no abre una región, cambia un modo.
    expect(fijar.getAttribute('aria-pressed')).toBe('false');

    fireEvent.click(fijar);
    await fixture.whenStable();

    const soltar = botonDe(/Soltar el menú/);
    expect(soltar.getAttribute('aria-pressed')).toBe('true');

    fireEvent.click(soltar);
    await fixture.whenStable();
    fireEvent.mouseLeave(panel());
    await fixture.whenStable();

    expect(panel().className).toContain('w-menu-riel');
  });

  it('cada grupo se anuncia como un disclosure y apunta a su región', async () => {
    await renderMenu();

    const catalogo = botonDe('Catálogo');
    expect(catalogo.getAttribute('aria-expanded')).toBe('true');
    const regionId = catalogo.getAttribute('aria-controls');
    expect(regionId).toBe('menu-grupo-catalogo');
    expect(document.getElementById(regionId!)).toBeTruthy();
  });

  it('el botón de una rama la pliega y la despliega', async () => {
    const { fixture } = await renderMenu();

    const ropa = botonDe('Ropa');
    expect(ropa.getAttribute('aria-expanded')).toBe('false');

    fireEvent.click(ropa);
    await fixture.whenStable();
    expect(botonDe('Ropa').getAttribute('aria-expanded')).toBe('true');

    fireEvent.click(botonDe('Ropa'));
    await fixture.whenStable();
    expect(botonDe('Ropa').getAttribute('aria-expanded')).toBe('false');
  });

  /**
   * Las cuatro líneas salen aunque el árbol no traiga ni una categoría de tres de ellas. Es la
   * misma decisión que hizo a `ListarCategorias` dejar de esconder las vacías: una rama que
   * desaparece porque hoy no hay nada dice que el negocio no vende eso.
   */
  it('pinta las cuatro líneas, tengan o no categorías', async () => {
    await renderMenu();

    expect(await screen.findByRole('button', { name: 'Tecnología' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Ropa' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Calzado deportivo' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Bolsos' })).toBeTruthy();
  });

  it('una hoja enlaza a la rejilla ya filtrada por su slug', async () => {
    const { fixture } = await renderMenu();

    fireEvent.click(await screen.findByRole('button', { name: 'Tecnología' }));
    await fixture.whenStable();

    const enlace = await screen.findByRole('link', { name: 'Celulares' });
    expect(enlace.getAttribute('href')).toBe('/es/productos?categoria=celulares');
  });

  /**
   * De una rama no cuelga ningún producto —lo defiende el backend—, así que enlazarla llevaría a
   * una rejilla vacía siempre. Se pliega en vez de enlazar.
   */
  it('una rama con hojas pliega en vez de enlazar', async () => {
    const { fixture } = await renderMenu();

    fireEvent.click(await screen.findByRole('button', { name: 'Ropa' }));
    await fixture.whenStable();

    // `findBy*` y no `getBy*`: el árbol sale de la consulta, y `whenStable()` no espera a que
    // TanStack Query resuelva (apps/web/CLAUDE.md). Las cuatro líneas sí están desde el principio
    // porque salen de `LINEAS`; lo que cuelga de ellas, no.
    expect(await screen.findByRole('button', { name: 'Dama' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Dama' })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Dama' }));
    await fixture.whenStable();

    expect((await screen.findByRole('link', { name: 'Blusas' })).getAttribute('href')).toBe(
      '/es/productos?categoria=ropa-dama-blusas',
    );
  });

  it('sin sesión de admin, el grupo del panel no existe', async () => {
    await renderMenu();

    expect(screen.queryByRole('button', { name: 'Panel' })).toBeNull();
  });

  /**
   * jsdom no evalúa media queries, así que lo que se puede comprobar es que la clase esté puesta.
   * En el teléfono no hay puntero que acercar: el árbol vive en el panel del encabezado.
   */
  it('no se pinta por debajo del primer punto de quiebre', async () => {
    const { fixture } = await renderMenu();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.className).toContain('hidden');
    // `contents` y no `block`: `app-root` es una rejilla de tres filas y un cuarto hijo en el flujo
    // se lleva una cuarta fila implícita. Ver el `host` del componente.
    expect(host.className).toContain('desde-movil:contents');
  });

  /**
   * Quien pide menos movimiento no quiere una versión acelerada del barrido: quiere ninguna. Es el
   * mismo criterio que el panel móvil del encabezado, y aquí pesa más porque la distancia es de
   * 216 px.
   */
  it('con movimiento reducido, el ancho cambia sin transición', async () => {
    document.documentElement.setAttribute('data-movimiento', 'reducido');
    try {
      await renderMenu();
      // `afterNextRender` rellena la señal después del primer pintado, así que se espera por el
      // resultado y no con un `esperar(ms)` fijo.
      await vi.waitFor(() => expect(panel().style.transitionDuration).toBe('0ms'));
    } finally {
      document.documentElement.removeAttribute('data-movimiento');
    }
  });
});
