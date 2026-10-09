import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import { Marca } from '../../../catalogo/domain/producto.model';
import {
  REPOSITORIO_MARCAS_ADMIN,
  RepositorioMarcasAdmin,
  ResultadoCrearMarca,
  ResultadoRenombrarMarca,
} from '../domain/repositorio-marcas-admin.puerto';
import { MarcasAdminPage } from './marcas-admin.page';

/**
 * Doble escrito a mano, sin librería de dobles (docs/06-testing.md).
 *
 * Compara el nombre **sin distinguir mayúsculas**, como el índice único de `V56`: un doble que
 * comparara exacto dejaría sin probar justo el caso que el 409 existe para atrapar.
 */
class RepositorioMarcasAdminFalso implements RepositorioMarcasAdmin {
  readonly creadas: string[] = [];

  constructor(private marcas: Marca[] = []) {}

  async listarTodas(): Promise<Marca[]> {
    return this.marcas;
  }

  async crear(nombre: string): Promise<ResultadoCrearMarca> {
    this.creadas.push(nombre);
    if (this.marcas.some((marca) => marca.nombre.toLowerCase() === nombre.toLowerCase())) {
      return { tipo: 'YA_EXISTE' };
    }
    const marca: Marca = { id: 'm-' + this.marcas.length, nombre };
    this.marcas = [...this.marcas, marca];
    return { tipo: 'CREADA', marca };
  }

  readonly renombradas: { id: string; nombre: string }[] = [];
  readonly eliminadas: string[] = [];
  /** Las que tienen productos: el servidor responde 409 al borrarlas. */
  readonly conProductos = new Set<string>();

  async renombrar(id: string, nombre: string): Promise<ResultadoRenombrarMarca> {
    this.renombradas.push({ id, nombre });
    const otra = this.marcas.find(
      (marca) => marca.id !== id && marca.nombre.toLowerCase() === nombre.toLowerCase(),
    );
    if (otra) {
      return { tipo: 'YA_EXISTE' };
    }
    const marca: Marca = { id, nombre: nombre.trim() };
    this.marcas = this.marcas.map((m) => (m.id === id ? marca : m));
    return { tipo: 'RENOMBRADA', marca };
  }

  async eliminar(id: string): Promise<void> {
    this.eliminadas.push(id);
    if (this.conProductos.has(id)) {
      throw new ErrorHttp(409, 'tiene productos', 'MARCA_CON_PRODUCTOS');
    }
    this.marcas = this.marcas.filter((m) => m.id !== id);
  }
}

async function renderPagina(marcas: Marca[] = []) {
  const repositorio = new RepositorioMarcasAdminFalso(marcas);
  const resultado = await render(MarcasAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_MARCAS_ADMIN, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

describe('MarcasAdminPage', () => {
  it('lista las marcas que ya existen', async () => {
    await renderPagina([{ id: 'm1', nombre: 'Xiaomi' }]);

    expect(await screen.findByText('Xiaomi')).toBeTruthy();
  });

  it('sin ninguna marca lo dice, en vez de dejar la pantalla en blanco', async () => {
    await renderPagina([]);

    expect(await screen.findByText(esAdmin.marcas.ninguna)).toBeTruthy();
  });

  it('crea la marca y la confirma por su nombre', async () => {
    const { repositorio } = await renderPagina([]);

    fireEvent.input(screen.getByLabelText(esAdmin.marcas.nombre), {
      target: { value: 'Huawei' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.marcas.crear }));

    expect(await screen.findByText(/Huawei ya está en el catálogo/)).toBeTruthy();
    expect(repositorio.creadas).toEqual(['Huawei']);
  });

  /**
   * El caso que motivó `V56`: el servidor responde 409 y la pantalla tiene que decir por qué, no
   * "hubo un error". Y el nombre repetido no es una caída — llega como resultado, no como excepción.
   */
  it('dice que ya existe cuando el nombre solo cambia en las mayúsculas', async () => {
    await renderPagina([{ id: 'm1', nombre: 'Xiaomi' }]);

    fireEvent.input(await screen.findByLabelText(esAdmin.marcas.nombre), {
      target: { value: 'xiaomi' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.marcas.crear }));

    expect(await screen.findByText(esAdmin.marcas.yaExiste)).toBeTruthy();
  });

  /**
   * Sin nombre no se manda nada, y se dice qué falta. El botón no se deshabilita a propósito: un
   * `<button disabled>` sale del orden de tabulación y quien navega con teclado no se entera de por
   * qué no pasa nada.
   */
  it('con el nombre vacío dice qué falta y no llama al servidor', async () => {
    const { repositorio } = await renderPagina([]);

    fireEvent.click(await screen.findByRole('button', { name: esAdmin.marcas.crear }));

    expect(await screen.findByText(esAdmin.marcas.faltaNombre)).toBeTruthy();
    expect(repositorio.creadas).toEqual([]);
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina([{ id: 'm1', nombre: 'Xiaomi' }]);

    await screen.findByText('Xiaomi');
    await esperarSinViolaciones(container);
  });

  describe('renombrar', () => {
    const e = esAdmin.marcas.editar;

    it('cambia el nombre en la fila y lo confirma por el nombre nuevo', async () => {
      const { repositorio } = await renderPagina([{ id: 'm1', nombre: 'Xaomi' }]);

      fireEvent.click(await screen.findByRole('button', { name: 'Editar la marca Xaomi' }));
      const campo = screen.getByLabelText(/Nuevo nombre de Xaomi/);
      expect((campo as HTMLInputElement).value).toBe('Xaomi');
      fireEvent.input(campo, { target: { value: 'Xiaomi' } });
      fireEvent.click(screen.getByRole('button', { name: e.guardar }));

      expect(await screen.findByText(/la marca ahora se llama Xiaomi/)).toBeTruthy();
      expect(repositorio.renombradas).toEqual([{ id: 'm1', nombre: 'Xiaomi' }]);
      expect(await screen.findByRole('button', { name: 'Editar la marca Xiaomi' })).toBeTruthy();
    });

    it('con el nombre de otra marca dice que ya existe y no cierra la edición', async () => {
      await renderPagina([
        { id: 'm1', nombre: 'Xiaomi' },
        { id: 'm2', nombre: 'Redmi' },
      ]);

      fireEvent.click(await screen.findByRole('button', { name: 'Editar la marca Redmi' }));
      fireEvent.input(screen.getByLabelText(/Nuevo nombre de Redmi/), {
        target: { value: 'XIAOMI' },
      });
      fireEvent.click(screen.getByRole('button', { name: e.guardar }));

      expect(await screen.findByText(esAdmin.marcas.yaExiste)).toBeTruthy();
      expect(screen.getByLabelText(/Nuevo nombre de Redmi/)).toBeTruthy();
    });

    it('sin cambios no llama al servidor, y cancelar tampoco', async () => {
      const { repositorio } = await renderPagina([{ id: 'm1', nombre: 'Xiaomi' }]);

      fireEvent.click(await screen.findByRole('button', { name: 'Editar la marca Xiaomi' }));
      fireEvent.click(screen.getByRole('button', { name: e.guardar }));
      fireEvent.click(await screen.findByRole('button', { name: 'Editar la marca Xiaomi' }));
      fireEvent.click(screen.getByRole('button', { name: e.cancelar }));

      expect(repositorio.renombradas).toEqual([]);
    });

    it('con el nombre vacío dice qué falta', async () => {
      const { repositorio } = await renderPagina([{ id: 'm1', nombre: 'Xiaomi' }]);

      fireEvent.click(await screen.findByRole('button', { name: 'Editar la marca Xiaomi' }));
      fireEvent.input(screen.getByLabelText(/Nuevo nombre de Xiaomi/), { target: { value: '' } });
      fireEvent.click(screen.getByRole('button', { name: e.guardar }));

      expect(await screen.findByText(e.faltaNombre)).toBeTruthy();
      expect(repositorio.renombradas).toEqual([]);
    });
  });

  describe('eliminar', () => {
    const e = esAdmin.marcas.eliminar;

    it('pregunta y borra la marca sin productos', async () => {
      const { repositorio } = await renderPagina([
        { id: 'm1', nombre: 'Xaomi' },
        { id: 'm2', nombre: 'Xiaomi' },
      ]);

      fireEvent.click(await screen.findByRole('button', { name: 'Eliminar la marca Xaomi' }));
      expect(repositorio.eliminadas).toEqual([]);
      fireEvent.click(screen.getByRole('button', { name: e.confirmar }));

      expect(await screen.findByText('La marca Xaomi se eliminó.')).toBeTruthy();
      expect(repositorio.eliminadas).toEqual(['m1']);
      // La lista se vuelve a pedir: la fila se va cuando llega.
      await waitFor(() =>
        expect(screen.queryByRole('button', { name: 'Eliminar la marca Xaomi' })).toBeNull(),
      );
    });

    it('con productos dice qué hacer, con el texto del código', async () => {
      const { repositorio } = await renderPagina([{ id: 'm1', nombre: 'Xiaomi' }]);
      repositorio.conProductos.add('m1');

      fireEvent.click(await screen.findByRole('button', { name: 'Eliminar la marca Xiaomi' }));
      fireEvent.click(screen.getByRole('button', { name: e.confirmar }));

      const pregunta = within(screen.getByRole('group'));
      expect(await pregunta.findByText(esAdmin.errores.marca_con_productos)).toBeTruthy();
      expect(screen.getByText('Xiaomi')).toBeTruthy();
    });

    it('cancelar no borra nada', async () => {
      const { repositorio } = await renderPagina([{ id: 'm1', nombre: 'Xiaomi' }]);

      fireEvent.click(await screen.findByRole('button', { name: 'Eliminar la marca Xiaomi' }));
      fireEvent.click(screen.getByRole('button', { name: e.cancelar }));

      expect(screen.queryByRole('button', { name: e.confirmar })).toBeNull();
      expect(repositorio.eliminadas).toEqual([]);
    });

    it('con la pregunta abierta no tiene violaciones de accesibilidad', async () => {
      const { container } = await renderPagina([{ id: 'm1', nombre: 'Xiaomi' }]);

      fireEvent.click(await screen.findByRole('button', { name: 'Eliminar la marca Xiaomi' }));
      await esperarSinViolaciones(container);
    });
  });
});
