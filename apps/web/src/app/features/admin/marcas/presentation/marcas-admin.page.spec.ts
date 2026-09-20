import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import { Marca } from '../../../catalogo/domain/producto.model';
import {
  REPOSITORIO_MARCAS_ADMIN,
  RepositorioMarcasAdmin,
  ResultadoCrearMarca,
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
});
