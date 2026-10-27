import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import { PORTAPAPELES, Portapapeles } from '../../../../core/portapapeles/portapapeles';
import { ProductoAdminDetalle } from '../../productos/domain/producto-admin.model';
import { EnlaceDeCompraAdmin } from '../domain/enlace-de-compra-admin.model';
import {
  REPOSITORIO_ENLACES_DE_COMPRA_ADMIN,
  RepositorioEnlacesDeCompraAdmin,
} from '../domain/repositorio-enlaces-de-compra-admin.puerto';
import { EnlacesDeCompraAdminPage } from './bandeja/enlaces-de-compra-admin.page';
import { TsColoresTecnologia } from './colores/ts-colores-tecnologia';

function enlace(cambios: Partial<EnlaceDeCompraAdmin> = {}): EnlaceDeCompraAdmin {
  return {
    id: 'e1',
    token: 'token-1',
    varianteId: 'v-morado',
    producto: 'Apple iPhone 18 Pro',
    slug: 'apple-iphone-18-pro',
    detalle: '256 GB · Morado',
    estado: 'VIGENTE',
    creadoEn: '2026-10-24T15:00:00Z',
    expiraEn: '2026-10-25T15:00:00Z',
    pedidoId: null,
    ...cambios,
  };
}

class RepositorioFalso implements RepositorioEnlacesDeCompraAdmin {
  readonly generados: string[] = [];
  readonly anulados: string[] = [];
  readonly paletas: { productoId: string; colores: readonly string[] }[] = [];

  constructor(private enlaces: EnlaceDeCompraAdmin[] = []) {}

  async listar(): Promise<EnlaceDeCompraAdmin[]> {
    return this.enlaces;
  }
  async generar(varianteId: string): Promise<EnlaceDeCompraAdmin> {
    this.generados.push(varianteId);
    return enlace({ varianteId });
  }
  async anular(enlaceId: string): Promise<EnlaceDeCompraAdmin> {
    this.anulados.push(enlaceId);
    this.enlaces = this.enlaces.map((e) => (e.id === enlaceId ? { ...e, estado: 'ANULADO' } : e));
    return this.enlaces.find((e) => e.id === enlaceId) as EnlaceDeCompraAdmin;
  }
  async definirPaleta(productoId: string, colores: readonly string[]): Promise<number> {
    this.paletas.push({ productoId, colores });
    return 2;
  }
}

class PortapapelesFalso implements Portapapeles {
  readonly copiados: string[] = [];
  async copiar(texto: string): Promise<void> {
    this.copiados.push(texto);
  }
}

const TRANSLOCO = TranslocoTestingModule.forRoot({
  langs: { es, en, 'admin/es': esAdmin } as never,
  translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
  preloadLangs: true,
});

function proveedores(repositorio: RepositorioFalso, portapapeles: PortapapelesFalso) {
  return [
    provideRouter([]),
    provideTanStackQuery(new QueryClient()),
    { provide: REPOSITORIO_ENLACES_DE_COMPRA_ADMIN, useValue: repositorio },
    { provide: PORTAPAPELES, useValue: portapapeles },
  ];
}

const IPHONE: ProductoAdminDetalle = {
  id: 'p1',
  nombre: 'Apple iPhone 18 Pro',
  descripcion: '',
  slug: 'apple-iphone-18-pro',
  estado: 'PUBLICADO',
  marca: { id: 'm1', nombre: 'Apple' },
  categoria: {
    id: 'c1',
    nombre: 'Celulares',
    slug: 'celulares',
    linea: 'TECNOLOGIA',
  },
  imagenPrincipalUrl: null,
  totalVariantes: 2,
  estadoDisponibilidad: 'DISPONIBLE',
  vistoPorUltimaVez: null,
  imagenPrincipalVarianteId: null,
  galeria: [],
  tallaSirveHasta: null,
  paleta: ['Negro', 'Morado'],
  variantes: [
    {
      id: 'v-negro',
      sku: 'IPH-NE',
      precioContado: 6_000_000,
      precioPublicado: 6_197_000,
      atributos: [{ nombre: 'Color', valor: 'Negro', colorHex: null }],
      disponibilidadPorConfirmar: false,
    },
    {
      id: 'v-morado',
      sku: 'IPH-MO',
      precioContado: 6_000_000,
      precioPublicado: 6_197_000,
      atributos: [
        { nombre: 'Almacenamiento', valor: '256', colorHex: null },
        { nombre: 'Color', valor: 'Morado', colorHex: null },
      ],
      disponibilidadPorConfirmar: true,
    },
  ],
};

@Component({
  imports: [TsColoresTecnologia],
  template: `<ts-colores-tecnologia [producto]="producto()" />`,
})
class Anfitrion {
  readonly producto = signal(IPHONE);
}

describe('TsColoresTecnologia', () => {
  it('carga la paleta en el campo y la guarda un color por línea', async () => {
    const repositorio = new RepositorioFalso();
    await render(Anfitrion, {
      imports: [TRANSLOCO],
      providers: proveedores(repositorio, new PortapapelesFalso()),
    });

    const campo = (await screen.findByLabelText(
      'Paleta del modelo, un color por línea',
    )) as HTMLTextAreaElement;
    expect(campo.value).toBe('Negro\nMorado');

    fireEvent.input(campo, { target: { value: 'Negro\n Morado \n\nPlata' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar paleta' }));

    await vi.waitFor(() =>
      expect(repositorio.paletas).toEqual([
        { productoId: 'p1', colores: ['Negro', 'Morado', 'Plata'] },
      ]),
    );
    expect(
      await screen.findByText('Paleta guardada. Nacieron 2 variantes nuevas para completarla.'),
    ).toBeTruthy();
  });

  it('solo ofrece enlace para los colores por confirmar, y lo deja copiar con su mensaje', async () => {
    const repositorio = new RepositorioFalso();
    const portapapeles = new PortapapelesFalso();
    const { container } = await render(Anfitrion, {
      imports: [TRANSLOCO],
      providers: proveedores(repositorio, portapapeles),
    });

    expect(
      screen.queryByRole('button', { name: 'Generar enlace de compra de Color · Negro' }),
    ).toBeNull();
    fireEvent.click(
      await screen.findByRole('button', { name: 'Generar enlace de compra de 256 · Morado' }),
    );

    await vi.waitFor(() => expect(repositorio.generados).toEqual(['v-morado']));
    const url = await screen.findByText(/\/es\/productos\/apple-iphone-18-pro\?enlace=token-1$/);
    expect(url).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Copiar mensaje para WhatsApp' }));
    await vi.waitFor(() => expect(portapapeles.copiados).toHaveLength(1));
    expect(portapapeles.copiados[0]).toContain('Apple iPhone 18 Pro (256 GB · Morado)');
    expect(portapapeles.copiados[0]).toContain('?enlace=token-1');
    expect(await screen.findByText('Copiado al portapapeles.')).toBeTruthy();
    await esperarSinViolaciones(container);
  });

  /** El tenis que se vende bajo confirmación (ADR-0089): las tallas, sin paleta. */
  it('en calzado ofrece las tallas por confirmar y no la paleta', async () => {
    const repositorio = new RepositorioFalso();
    const { fixture } = await render(Anfitrion, {
      imports: [TRANSLOCO],
      providers: proveedores(repositorio, new PortapapelesFalso()),
    });
    fixture.componentInstance.producto.set({
      ...IPHONE,
      nombre: 'Tenis importados estilo Nike Air Force 1',
      categoria: { id: 'c2', nombre: 'Unisex', slug: 'calzado-unisex', linea: 'CALZADO' },
      paleta: [],
      variantes: [
        {
          id: 'v-40',
          sku: 'PRV-40',
          precioContado: 162_000,
          precioPublicado: 169_000,
          atributos: [{ nombre: 'Talla', valor: '40', colorHex: null }],
          disponibilidadPorConfirmar: true,
        },
      ],
    });

    expect(
      await screen.findByRole('heading', { name: 'Tallas por confirmar con el proveedor' }),
    ).toBeTruthy();
    expect(screen.queryByLabelText('Paleta del modelo, un color por línea')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Generar enlace de compra de 40' }));
    await vi.waitFor(() => expect(repositorio.generados).toEqual(['v-40']));
  });
});

describe('EnlacesDeCompraAdminPage', () => {
  it('lista los enlaces con su estado y anula el vigente', async () => {
    const repositorio = new RepositorioFalso([
      enlace(),
      enlace({ id: 'e2', token: 'token-2', estado: 'USADO', pedidoId: 'pedido-1' }),
    ]);
    const { container } = await render(EnlacesDeCompraAdminPage, {
      imports: [TRANSLOCO],
      providers: proveedores(repositorio, new PortapapelesFalso()),
    });

    expect(await screen.findByText(/^Vigente/)).toBeTruthy();
    expect(screen.getByText(/^Usado en un pedido/)).toBeTruthy();
    // Solo el vigente se puede anular o volver a copiar.
    const anular = screen.getAllByRole('button', { name: /^Anular el enlace de compra/ });
    expect(anular).toHaveLength(1);

    fireEvent.click(anular[0]);

    await vi.waitFor(() => expect(repositorio.anulados).toEqual(['e1']));
    expect(
      await screen.findByText('Enlace anulado: la unidad apartada vuelve a estar libre.'),
    ).toBeTruthy();
    await esperarSinViolaciones(container);
  });
});
