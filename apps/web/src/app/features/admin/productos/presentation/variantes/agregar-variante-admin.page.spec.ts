import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { Atributo } from '../../../../catalogo/domain/producto.model';
import {
  REPOSITORIO_ATRIBUTOS,
  RepositorioAtributos,
} from '../../../../catalogo/domain/repositorio-atributos.puerto';
import {
  AgregarVarianteAdmin,
  ProductoAdmin,
  ProductosPaginadosAdmin,
} from '../../domain/producto-admin.model';
import {
  REPOSITORIO_PRODUCTOS_ADMIN,
  RepositorioProductosAdmin,
} from '../../domain/repositorio-productos-admin.puerto';
import { AgregarVarianteAdminPage } from './agregar-variante-admin.page';

const COLOR: Atributo = { id: 'a1', nombre: 'Color', tipo: 'COLOR', valoresPermitidos: [] };
const TALLA: Atributo = {
  id: 'a2',
  nombre: 'Talla',
  tipo: 'TEXTO',
  valoresPermitidos: ['S', 'M', 'L'],
};

class RepositorioAtributosFalso implements RepositorioAtributos {
  async listarTodas(): Promise<Atributo[]> {
    return [COLOR, TALLA];
  }
}

class RepositorioProductosAdminFalso implements RepositorioProductosAdmin {
  llamadasAgregarVariante: AgregarVarianteAdmin[] = [];

  constructor(private errorAlAgregar = false) {}

  async listar(): Promise<ProductosPaginadosAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async crear(): Promise<ProductoAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async obtener(): Promise<ProductoAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async editar(): Promise<ProductoAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async agregarVariante(comando: AgregarVarianteAdmin): Promise<void> {
    this.llamadasAgregarVariante.push(comando);
    if (this.errorAlAgregar) {
      throw new Error('falló');
    }
  }

  async subirImagenPrincipal(): Promise<never> {
    throw new Error('No usado en estas pruebas.');
  }
}

function activatedRouteConProductoId(productoId: string) {
  const paramMap = convertToParamMap({ productoId });
  return { paramMap: of(paramMap), snapshot: { paramMap } };
}

async function renderPagina(repositorioProductos: RepositorioProductosAdmin, productoId = 'p1') {
  return render(AgregarVarianteAdminPage, {
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
      { provide: REPOSITORIO_PRODUCTOS_ADMIN, useValue: repositorioProductos },
      { provide: REPOSITORIO_ATRIBUTOS, useValue: new RepositorioAtributosFalso() },
      { provide: ActivatedRoute, useValue: activatedRouteConProductoId(productoId) },
    ],
  });
}


describe('AgregarVarianteAdminPage', () => {
  it('el botón crear arranca deshabilitado con el formulario vacío', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());

    expect(screen.getByRole('button', { name: 'Crear variante' }).hasAttribute('disabled')).toBe(
      true,
    );
  });

  it('al elegir un atributo de tipo color, muestra el campo de color', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());

    fireEvent.click(screen.getByRole('button', { name: 'Agregar atributo' }));
    await screen.findByRole('option', { name: 'Color' });
    fireEvent.change(screen.getByLabelText('Atributo'), { target: { value: 'a1' } });

    expect(await screen.findByLabelText('Color (hex)')).toBeTruthy();
  });

  it('al enviar exitosamente, agrega la variante y navega de vuelta a editar producto', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    const { fixture } = await renderPagina(repositorio, 'p1');
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'TS-CAM-AZ-M' } });
    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '89900' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear variante' }));
    await vi.waitFor(() => expect(repositorio.llamadasAgregarVariante).toHaveLength(1));

    expect(repositorio.llamadasAgregarVariante).toEqual([
      {
        productoId: 'p1',
        sku: 'TS-CAM-AZ-M',
        precio: 89900,
        tasaIva: 0.19,
        codigoBarras: null,
        existenciaInicial: 0,
        atributos: [],
      },
    ]);
    expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'productos', 'p1', 'editar']);
  });

  it('con un error del servidor, muestra el mensaje genérico', async () => {
    await renderPagina(new RepositorioProductosAdminFalso(true));

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'TS-1' } });
    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '1000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear variante' }));

    // `findByText`, no `await esperar(50)`: la espera fija pasaba en aislamiento
    // y fallaba en la suite completa, porque 50 ms no alcanzan cuando la
    // máquina está cargada. `findByText` sondea hasta que el mensaje aparece.
    expect(
      await screen.findByText('No se pudo agregar la variante. Intenta de nuevo.'),
    ).toBeTruthy();
  });
});
