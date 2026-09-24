import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { describe, expect, it, vi } from 'vitest';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import enAdmin from '../../../../../assets/i18n/scopes/admin/en.json';
import esAdmin from '../../../../../assets/i18n/scopes/admin/es.json';
import { Categoria } from '../../../catalogo/domain/producto.model';
import {
  CambioDeCategoria,
  NuevaCategoria,
  REPOSITORIO_CATEGORIAS_ADMIN,
  RepositorioCategoriasAdmin,
  ResultadoEscritura,
} from '../domain/repositorio-categorias-admin.puerto';
import { CategoriasAdminPage } from './categorias-admin.page';

const ARBOL: Categoria[] = [
  { id: 'c1', nombre: 'Celulares', slug: 'celulares', linea: 'TECNOLOGIA', padreId: null },
  { id: 'd0', nombre: 'Dama', slug: 'ropa-dama', linea: 'ROPA', padreId: null },
  { id: 'd1', nombre: 'Blusas', slug: 'ropa-dama-blusas', linea: 'ROPA', padreId: 'd0' },
];

class RepositorioCategoriasAdminFalso implements RepositorioCategoriasAdmin {
  readonly creadas: NuevaCategoria[] = [];
  readonly editadas: CambioDeCategoria[] = [];
  readonly borradas: string[] = [];

  /** Lo que la siguiente escritura va a responder. Por omisión, que salió bien. */
  respuesta: ResultadoEscritura = { tipo: 'OK', categoria: null };

  async listarTodas(): Promise<Categoria[]> {
    return ARBOL;
  }

  async crear(nueva: NuevaCategoria): Promise<ResultadoEscritura> {
    this.creadas.push(nueva);
    return this.respuesta;
  }

  async editar(cambio: CambioDeCategoria): Promise<ResultadoEscritura> {
    this.editadas.push(cambio);
    return this.respuesta;
  }

  async eliminar(id: string): Promise<ResultadoEscritura> {
    this.borradas.push(id);
    return this.respuesta;
  }
}

async function renderPagina(repositorio = new RepositorioCategoriasAdminFalso()) {
  const resultado = await render(CategoriasAdminPage, {
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
      { provide: REPOSITORIO_CATEGORIAS_ADMIN, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

/**
 * El texto de todas las regiones de alerta juntas. Son varias y permanentes: `ts-campo` monta la
 * suya vacía para cada control, que es lo que pide `apps/web/CLAUDE.md` —una región viva vive
 * siempre en el DOM y lo que cambia es su contenido—. Buscar "la" alerta encuentra cuatro.
 */
function textoDeLasAlertas(): string {
  return screen
    .getAllByRole('alert')
    .map((elemento) => elemento.textContent ?? '')
    .join(' ');
}

async function escribir(etiqueta: string | RegExp, valor: string): Promise<void> {
  const campo = await screen.findByLabelText(etiqueta);
  fireEvent.input(campo, { target: { value: valor } });
}

describe('CategoriasAdminPage', () => {
  it('pinta el árbol agrupado por línea', async () => {
    await renderPagina();

    expect(await screen.findByRole('heading', { name: 'Tecnología' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Ropa' })).toBeTruthy();
    // Las cuatro líneas se pintan aunque no tengan nada: el negocio las tiene, el menú también.
    expect(screen.getByRole('heading', { name: 'Calzado deportivo' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Bolsos' })).toBeTruthy();
  });

  /**
   * De una rama no cuelgan productos, y quien va a cargar uno necesita saberlo sin probar y que se
   * lo rechacen.
   */
  it('marca cuáles son ramas', async () => {
    const { container } = await renderPagina();
    await screen.findByRole('heading', { name: 'Ropa' });

    const filas = [...container.querySelectorAll('li')].map((li) => li.textContent ?? '');
    expect(filas.find((texto) => texto.includes('Dama'))).toContain('(rama)');
    expect(filas.find((texto) => texto.includes('Blusas'))).not.toContain('(rama)');
  });

  it('crea una categoría de primer nivel con su línea', async () => {
    const { fixture, repositorio } = await renderPagina();

    await escribir('Nombre', 'Impresoras');
    fireEvent.change(await screen.findByLabelText('Línea'), { target: { value: 'TECNOLOGIA' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear categoría' }));
    await fixture.whenStable();

    expect(repositorio.creadas).toEqual([
      { nombre: 'Impresoras', slug: undefined, linea: 'TECNOLOGIA', padreId: undefined },
    ]);
  });

  /** La línea sobra cuando hay madre: se hereda de ella, y el backend la ignora. */
  it('crea una subcategoría bajo la madre elegida, sin mandar línea', async () => {
    const { fixture, repositorio } = await renderPagina();
    await screen.findByRole('heading', { name: 'Ropa' });

    await escribir('Nombre', 'Faldas');
    fireEvent.change(screen.getByLabelText('Cuelga de'), { target: { value: 'd0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear categoría' }));
    await fixture.whenStable();

    expect(repositorio.creadas).toEqual([
      { nombre: 'Faldas', slug: undefined, linea: undefined, padreId: 'd0' },
    ]);
  });

  it('sin línea ni madre, no envía nada y dice qué falta', async () => {
    const { fixture, repositorio } = await renderPagina();

    await escribir('Nombre', 'Huérfana');
    fireEvent.click(screen.getByRole('button', { name: 'Crear categoría' }));
    await fixture.whenStable();

    expect(repositorio.creadas).toEqual([]);
    expect(textoDeLasAlertas()).toContain('Elige una línea');
  });

  /**
   * El slug está en enlaces ya compartidos: renombrar no puede arrastrarlo. El formulario lo carga
   * con el actual y lo manda tal cual, que es lo que el backend lee como "déjalo como está".
   */
  it('al editar, carga el slug actual y lo conserva', async () => {
    const { fixture, repositorio } = await renderPagina();
    await screen.findByRole('heading', { name: 'Tecnología' });

    fireEvent.click(screen.getAllByRole('button', { name: 'Editar' })[0]);
    await fixture.whenStable();

    await escribir('Nombre', 'Consolas de videojuegos');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    await fixture.whenStable();

    expect(repositorio.editadas).toHaveLength(1);
    expect(repositorio.editadas[0].nombre).toBe('Consolas de videojuegos');
    expect(repositorio.editadas[0].slug).toBeTruthy();
  });

  /**
   * Dos toques y no un modal: lo único que se puede perder de un clic aquí es una hoja vacía —el
   * backend rechaza el resto—, pero perderla en silencio sigue siendo perderla.
   */
  it('el primer toque de borrar pide confirmación y no borra', async () => {
    const { fixture, repositorio } = await renderPagina();
    await screen.findByRole('heading', { name: 'Tecnología' });

    fireEvent.click(screen.getAllByRole('button', { name: /Borrar/ })[0]);
    await fixture.whenStable();

    expect(repositorio.borradas).toEqual([]);
    expect(screen.getByRole('button', { name: /Confirmar el borrado/ })).toBeTruthy();
  });

  it('el segundo toque sí borra', async () => {
    const { fixture, repositorio } = await renderPagina();
    await screen.findByRole('heading', { name: 'Tecnología' });

    fireEvent.click(screen.getAllByRole('button', { name: /Borrar/ })[0]);
    await fixture.whenStable();
    fireEvent.click(screen.getByRole('button', { name: /Confirmar el borrado/ }));
    await fixture.whenStable();

    expect(repositorio.borradas).toHaveLength(1);
  });

  /**
   * Cada rechazo dice qué hacer, no solo que no se pudo. Es para lo que el adaptador los traduce a
   * un tipo con nombre en vez de dejar pasar el código HTTP.
   */
  it('un rechazo del árbol se enseña con su salida concreta', async () => {
    const repositorio = new RepositorioCategoriasAdminFalso();
    repositorio.respuesta = { tipo: 'TIENE_PRODUCTOS' };
    const { fixture } = await renderPagina(repositorio);
    await screen.findByRole('heading', { name: 'Tecnología' });

    fireEvent.click(screen.getAllByRole('button', { name: /Borrar/ })[0]);
    await fixture.whenStable();
    fireEvent.click(screen.getByRole('button', { name: /Confirmar el borrado/ }));
    await fixture.whenStable();

    await vi.waitFor(() =>
      expect(textoDeLasAlertas()).toContain('Muévelos a otra antes de seguir'),
    );
  });

  it('un slug repetido se explica por su nombre', async () => {
    const repositorio = new RepositorioCategoriasAdminFalso();
    repositorio.respuesta = { tipo: 'SLUG_REPETIDO' };
    const { fixture } = await renderPagina(repositorio);

    await escribir('Nombre', 'Relojes');
    fireEvent.change(await screen.findByLabelText('Línea'), { target: { value: 'TECNOLOGIA' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear categoría' }));
    await fixture.whenStable();

    await vi.waitFor(() => expect(textoDeLasAlertas()).toContain('Escribe uno distinto'));
  });
});
