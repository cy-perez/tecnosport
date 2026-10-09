import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, within } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { Categoria, Marca } from '../../../../catalogo/domain/producto.model';
import {
  REPOSITORIO_CATEGORIAS,
  RepositorioCategorias,
} from '../../../../catalogo/domain/repositorio-categorias.puerto';
import {
  REPOSITORIO_MARCAS,
  RepositorioMarcas,
} from '../../../../catalogo/domain/repositorio-marcas.puerto';
import { REPOSITORIO_PROVEEDORES_ADMIN } from '../../../proveedores/domain/repositorio-proveedores-admin.puerto';
import {
  proveedorDePrueba,
  RepositorioProveedoresAdminFalso,
} from '../../../proveedores/presentation/apoyo-proveedores.spec-util';
import { BorradorTecnologia } from '../../domain/borrador-tecnologia.model';
import { REPOSITORIO_BORRADORES_TECNOLOGIA } from '../../domain/repositorio-borradores-tecnologia.puerto';
import {
  borradorTecnologiaDePrueba,
  RepositorioBorradoresTecnologiaFalso,
} from '../apoyo-tecnologia.spec-util';
import { DetalleTecnologiaAdminPage } from './detalle-tecnologia-admin.page';

const SAMSUNG: Marca = { id: 'm-sam', nombre: 'Samsung' };
const CELULARES: Categoria = {
  id: 'c-cel',
  nombre: 'Celulares',
  slug: 'tecnologia-celulares',
  linea: 'TECNOLOGIA',
  padreId: null,
  hashtags: [],
  escalaTallas: [],
};

class RepositorioMarcasFalso implements RepositorioMarcas {
  async listarTodas(): Promise<Marca[]> {
    return [SAMSUNG, { id: 'm-gen', nombre: 'Genérica' }];
  }
}

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [CELULARES];
  }
}

function rutaCon(id: string) {
  const paramMap = convertToParamMap({ id });
  return { paramMap: of(paramMap), snapshot: { paramMap } };
}

async function renderPagina(borrador: BorradorTecnologia = borradorTecnologiaDePrueba()) {
  const repositorio = new RepositorioBorradoresTecnologiaFalso([borrador]);
  const resultado = await render(DetalleTecnologiaAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: ActivatedRoute, useValue: rutaCon(borrador.id) },
      { provide: REPOSITORIO_BORRADORES_TECNOLOGIA, useValue: repositorio },
      {
        provide: REPOSITORIO_PROVEEDORES_ADMIN,
        useValue: new RepositorioProveedoresAdminFalso([proveedorDePrueba()]),
      },
      { provide: REPOSITORIO_MARCAS, useValue: new RepositorioMarcasFalso() },
      { provide: REPOSITORIO_CATEGORIAS, useValue: new RepositorioCategoriasFalso() },
    ],
  });
  await screen.findByRole('heading', { name: 'Samsung Galaxy A17 5G' });
  return { ...resultado, repositorio };
}

function grupoDe(titulo: string) {
  return within(screen.getByRole('group', { name: titulo }));
}

const UNA_SIM = 'Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM';
const DUAL = 'Samsung Galaxy A17 5G 8GB RAM 256GB Dual SIM';

describe('DetalleTecnologiaAdminPage', () => {
  it('arranca con los colores que sugiere la lista y el precio de mercado', async () => {
    await renderPagina();

    const unaSim = grupoDe(UNA_SIM);
    expect((unaSim.getByLabelText('Negro') as HTMLInputElement).checked).toBe(true);
    expect((unaSim.getByLabelText('Gris') as HTMLInputElement).checked).toBe(false);
    expect(
      (unaSim.getByLabelText(esAdmin.tecnologia.detalle.precioVenta) as HTMLInputElement).value,
    ).toBe('849900');
    const dual = grupoDe(DUAL);
    expect((dual.getByLabelText('Negro') as HTMLInputElement).checked).toBe(false);
    expect(
      (dual.getByLabelText(esAdmin.tecnologia.detalle.precioVenta) as HTMLInputElement).value,
    ).toBe('');
  });

  it('aprobar guarda la elección y aprueba con la marca y la categoría que sugiere la lista', async () => {
    const { repositorio } = await renderPagina();
    await screen.findByRole('option', { name: 'Samsung' });

    fireEvent.click(grupoDe(UNA_SIM).getByLabelText('Gris'));
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.hecho)).toBeTruthy();
    expect(repositorio.elecciones[0].elecciones).toEqual([
      { sku: 'a17-1-sim', colores: ['Negro', 'Gris'], precioVenta: 849900 },
      { sku: 'a17-dual-sim', colores: [], precioVenta: null },
    ]);
    expect(repositorio.aprobaciones).toEqual([
      { id: 'bt-1', aprobacion: { marcaId: 'm-sam', categoriaId: 'c-cel' } },
    ]);
    expect(screen.getByRole('link', { name: esAdmin.tecnologia.detalle.verProducto })).toBeTruthy();
  });

  it('sin colores marcados no hay nada que vender, y no se envía', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.click(grupoDe(UNA_SIM).getByLabelText('Negro'));
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.nadaQueVender)).toBeTruthy();
    expect(repositorio.elecciones).toHaveLength(0);
    expect(repositorio.aprobaciones).toHaveLength(0);
  });

  it('una configuración con colores y sin precio no se aprueba', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.click(grupoDe(DUAL).getByLabelText('Azul'));
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.faltaPrecio)).toBeTruthy();
    expect(repositorio.aprobaciones).toHaveLength(0);
  });

  it('las configuraciones nuevas de un producto que ya se vende no piden marca ni categoría', async () => {
    const { repositorio } = await renderPagina(
      borradorTecnologiaDePrueba({ productoId: 'prod-9' }),
    );

    expect(screen.queryByLabelText(new RegExp(esAdmin.tecnologia.aprobar.marca))).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.hecho)).toBeTruthy();
    expect(repositorio.aprobaciones[0].aprobacion).toEqual({ marcaId: null, categoriaId: null });
  });

  it('sin paleta, los colores se escriben a mano', async () => {
    const { repositorio } = await renderPagina(
      borradorTecnologiaDePrueba({
        paleta: [],
        configuraciones: [borradorTecnologiaDePrueba().configuraciones[0]],
        productoId: 'prod-9',
      }),
    );

    const campo = screen.getByLabelText(
      esAdmin.tecnologia.detalle.coloresEscritos,
    ) as HTMLInputElement;
    expect(campo.value).toBe('Negro');
    fireEvent.input(campo, { target: { value: 'Negro, Azul claro' } });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    await screen.findByText(esAdmin.tecnologia.aprobar.hecho);
    expect(repositorio.elecciones[0].elecciones[0].colores).toEqual(['Negro', 'Azul claro']);
  });

  it('rechazar pide el motivo', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.rechazar.accion }));
    expect(await screen.findByText(esAdmin.tecnologia.rechazar.faltaMotivo)).toBeTruthy();
    expect(repositorio.rechazos).toHaveLength(0);

    fireEvent.input(screen.getByLabelText(new RegExp(esAdmin.tecnologia.rechazar.motivo)), {
      target: { value: 'No vendemos esta gama' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.rechazar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.rechazar.hecho)).toBeTruthy();
    expect(repositorio.rechazos).toEqual([{ id: 'bt-1', motivo: 'No vendemos esta gama' }]);
  });

  it('un precio mal escrito se dice en su campo y no se manda', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.input(grupoDe(UNA_SIM).getByLabelText(esAdmin.tecnologia.detalle.precioVenta), {
      target: { value: '849,900' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.detalle.guardar }));

    expect(
      (await screen.findAllByText(esAdmin.tecnologia.detalle.precioIlegible)).length,
    ).toBeGreaterThan(0);
    expect(repositorio.elecciones).toHaveLength(0);
  });

  it('no se aprueba al costo', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.input(grupoDe(UNA_SIM).getByLabelText(esAdmin.tecnologia.detalle.precioVenta), {
      target: { value: '675000' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.precioBajoCosto)).toBeTruthy();
    expect(repositorio.aprobaciones).toHaveLength(0);
  });

  it('una configuración que llega con la página abierta aparece sin perder lo marcado', async () => {
    const { repositorio, fixture } = await renderPagina();
    fireEvent.click(grupoDe(UNA_SIM).getByLabelText('Gris'));

    const actual = borradorTecnologiaDePrueba();
    repositorio.reemplazar({
      ...actual,
      configuraciones: [
        ...actual.configuraciones,
        {
          ...actual.configuraciones[0],
          sku: 'a17-esim',
          titulo: 'Samsung Galaxy A17 5G 8GB RAM 256GB eSIM',
        },
      ],
    });
    await fixture.debugElement.injector.get(QueryClient).invalidateQueries();

    expect(
      await screen.findByRole('group', {
        name: 'Colores de Samsung Galaxy A17 5G 8GB RAM 256GB eSIM',
      }),
    ).toBeTruthy();
    expect((grupoDe(UNA_SIM).getByLabelText('Gris') as HTMLInputElement).checked).toBe(true);
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina();

    await esperarSinViolaciones(container);
  });
});
