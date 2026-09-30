import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
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
import { Borrador, FotoBorrador } from '../../domain/borrador.model';
import { REPOSITORIO_BORRADORES_ADMIN } from '../../domain/repositorio-borradores-admin.puerto';
import {
  borradorDePrueba,
  fotoDePrueba,
  RepositorioBorradoresAdminFalso,
} from '../apoyo-borradores.spec-util';
import { DetalleBorradorAdminPage } from './detalle-borrador-admin.page';

const MARCA: Marca = { id: 'm1', nombre: 'Genérica' };
const CATEGORIA: Categoria = {
  id: 'c1',
  nombre: 'Bolsos de mano',
  slug: 'bolsos-de-mano',
  linea: 'BOLSOS',
  padreId: null,
  hashtags: [],
};

class RepositorioMarcasFalso implements RepositorioMarcas {
  async listarTodas(): Promise<Marca[]> {
    return [MARCA];
  }
}

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [CATEGORIA];
  }
}

function rutaCon(id: string) {
  const paramMap = convertToParamMap({ id });
  return { paramMap: of(paramMap), snapshot: { paramMap } };
}

async function renderPagina(
  borrador: Borrador = borradorDePrueba(),
  fotos: FotoBorrador[] = [fotoDePrueba('f-1'), fotoDePrueba('f-2')],
  textos: string[] = ['Bolso tote 53.000 sirve hasta L', 'Disponible en negro y café'],
) {
  const repositorio = new RepositorioBorradoresAdminFalso([borrador], fotos, textos);
  const resultado = await render(DetalleBorradorAdminPage, {
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
      { provide: REPOSITORIO_BORRADORES_ADMIN, useValue: repositorio },
      {
        provide: REPOSITORIO_PROVEEDORES_ADMIN,
        useValue: new RepositorioProveedoresAdminFalso([proveedorDePrueba()]),
      },
      { provide: REPOSITORIO_MARCAS, useValue: new RepositorioMarcasFalso() },
      { provide: REPOSITORIO_CATEGORIAS, useValue: new RepositorioCategoriasFalso() },
      { provide: ActivatedRoute, useValue: rutaCon(borrador.id) },
    ],
  });
  return { ...resultado, repositorio };
}

const a = esAdmin.borradores.aprobar;
const d = esAdmin.borradores.datos;

async function llenarAprobacion() {
  await screen.findByRole('option', { name: 'Genérica' });
  fireEvent.change(screen.getByLabelText(a.marca), { target: { value: 'm1' } });
  fireEvent.change(screen.getByLabelText(a.categoria), { target: { value: 'c1' } });
  fireEvent.input(screen.getByLabelText(a.existenciaInicial), { target: { value: '2' } });
}

describe('DetalleBorradorAdminPage', () => {
  it('muestra lo que escribió el proveedor, las fotos y las alertas', async () => {
    await renderPagina(borradorDePrueba({ alertas: ['CONFIANZA_BAJA'] }));

    expect(await screen.findByText('Bolso tote 53.000 sirve hasta L')).toBeTruthy();
    expect(screen.getAllByRole('img')).toHaveLength(2);
    expect(screen.getByText(esAdmin.borradores.alertas.CONFIANZA_BAJA)).toBeTruthy();
    expect(screen.getByText(/precio del proveedor: \$ 53\.000/)).toBeTruthy();
  });

  it('prellena los datos extraídos y el precio de venta con el sugerido', async () => {
    await renderPagina();

    const titulo = (await screen.findByLabelText(d.tituloProducto)) as HTMLInputElement;
    await vi.waitFor(() => expect(titulo.value).toBe('Bolso tote en cuero sintético'));
    expect((screen.getByLabelText(d.sirveHasta) as HTMLInputElement).value).toBe('L');
    expect((screen.getByLabelText(d.tonosNombrados) as HTMLInputElement).value).toBe('Negro, Café');
    expect((screen.getByLabelText(a.precioVenta) as HTMLInputElement).value).toBe('72000');
  });

  /**
   * El criterio de aceptación de la aprobación: crea el producto con el tono elegido por foto,
   * las unidades iniciales y el precio de venta definitivo. Sin marca ni categoría no se manda.
   */
  it('sin marca ni categoría dice qué falta y no aprueba', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.click(await screen.findByRole('button', { name: a.accion }));

    expect(await screen.findByText(a.faltanCampos)).toBeTruthy();
    expect(repositorio.aprobaciones).toEqual([]);
  });

  it('aprueba con el tono de cada foto, las unidades y el precio, y enlaza el producto', async () => {
    const { repositorio } = await renderPagina();
    await llenarAprobacion();

    fireEvent.change(screen.getByLabelText('Tono de la foto 1'), { target: { value: 'Negro' } });
    fireEvent.input(screen.getByLabelText(a.precioVenta), { target: { value: '75.000' } });
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    expect(await screen.findByRole('link', { name: a.verProducto })).toBeTruthy();
    expect(repositorio.aprobaciones).toHaveLength(1);
    expect(repositorio.aprobaciones[0].aprobacion).toMatchObject({
      marcaId: 'm1',
      categoriaId: 'c1',
      precioVenta: 75000,
      existenciaInicial: 2,
      altEs: 'Bolso tote en cuero sintético',
      fotos: [
        { mensajeId: 'f-1', tono: 'Negro' },
        { mensajeId: 'f-2', tono: null },
      ],
    });
  });

  it('guarda las correcciones de los datos extraídos', async () => {
    const { repositorio } = await renderPagina();
    const titulo = (await screen.findByLabelText(d.tituloProducto)) as HTMLInputElement;
    await vi.waitFor(() => expect(titulo.value).toBe('Bolso tote en cuero sintético'));

    fireEvent.input(titulo, { target: { value: 'Bolso tote negro' } });
    fireEvent.input(screen.getByLabelText(d.caracteristicas), {
      target: { value: 'Cierre magnético\nBolsillo interno' },
    });
    fireEvent.click(screen.getByRole('button', { name: d.guardar }));

    expect(await screen.findByText(d.guardado)).toBeTruthy();
    expect(repositorio.ediciones[0].cambios).toMatchObject({
      titulo: 'Bolso tote negro',
      tallas: { tipo: 'UNICA', sirveHasta: 'L', valores: [] },
      tonosNombrados: ['Negro', 'Café'],
      caracteristicas: ['Cierre magnético', 'Bolsillo interno'],
    });
  });

  it('rechazar exige un motivo y lo manda', async () => {
    const { repositorio } = await renderPagina();
    const r = esAdmin.borradores.rechazar;

    fireEvent.click(await screen.findByRole('button', { name: r.accion }));
    expect(await screen.findByText(r.faltaMotivo)).toBeTruthy();

    fireEvent.input(screen.getByLabelText(r.motivo), { target: { value: 'Foto borrosa' } });
    fireEvent.click(screen.getByRole('button', { name: r.accion }));

    expect(await screen.findByText(r.hecho)).toBeTruthy();
    expect(repositorio.rechazos).toEqual([{ id: 'b-1', motivo: 'Foto borrosa' }]);
  });

  /** Un borrador decidido no ofrece ninguna de las tres acciones: el servidor las rechazaría. */
  it('un borrador ya aprobado se muestra sin formularios y con el enlace al producto', async () => {
    await renderPagina(borradorDePrueba({ estado: 'APROBADO', productoId: 'p-9' }));

    expect(
      await screen.findByRole('link', { name: esAdmin.borradores.detalle.verProducto }),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: a.accion })).toBeNull();
    expect(screen.queryByRole('button', { name: d.guardar })).toBeNull();
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina();

    await screen.findByRole('option', { name: 'Genérica' });
    await esperarSinViolaciones(container);
  });
});
