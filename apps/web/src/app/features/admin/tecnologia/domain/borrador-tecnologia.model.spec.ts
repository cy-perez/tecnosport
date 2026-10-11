import {
  BorradorTecnologia,
  ConfiguracionTecnologia,
  coloresIniciales,
  fotosQueFaltan,
  precioEscrito,
  precioInicial,
  problemaDeAprobacion,
  separarColoresEscritos,
} from './borrador-tecnologia.model';

function configuracion(cambios: Partial<ConfiguracionTecnologia> = {}): ConfiguracionTecnologia {
  return {
    sku: 'a17-1-sim',
    titulo: 'Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM',
    ram: '8GB',
    almacenamiento: '256GB',
    sim: '1 SIM',
    costoProveedor: 675_000,
    precioMercado: 849_900,
    coloresSugeridos: ['Negro', 'Rosado'],
    coloresElegidos: [],
    precioVenta: null,
    ...cambios,
  };
}

describe('borrador de tecnología', () => {
  it('arranca con lo sugerido que está en la paleta, y nada si no hay sugerencia', () => {
    expect(coloresIniciales(configuracion(), ['Negro', 'Gris'])).toEqual(['Negro']);
    expect(coloresIniciales(configuracion({ coloresSugeridos: [] }), ['Negro'])).toEqual([]);
  });

  it('lo ya elegido manda sobre lo sugerido', () => {
    expect(
      coloresIniciales(configuracion({ coloresElegidos: ['Gris'] }), ['Negro', 'Gris']),
    ).toEqual(['Gris']);
  });

  it('sin paleta, lo sugerido entra tal cual', () => {
    expect(coloresIniciales(configuracion(), [])).toEqual(['Negro', 'Rosado']);
  });

  it('el precio arranca en el fijado y si no en el de mercado', () => {
    expect(precioInicial(configuracion())).toBe(849_900);
    expect(precioInicial(configuracion({ precioVenta: 829_900 }))).toBe(829_900);
    expect(precioInicial(configuracion({ precioMercado: null }))).toBeNull();
  });

  it('no se aprueba sin nada que vender, con un precio pendiente ni al costo', () => {
    const configuraciones = [
      configuracion({ sku: 'a', costoProveedor: 100 }),
      configuracion({ sku: 'b', costoProveedor: 100 }),
    ];
    expect(problemaDeAprobacion([{ sku: 'a', colores: [], precioVenta: 1 }], configuraciones)).toBe(
      'nadaQueVender',
    );
    expect(
      problemaDeAprobacion(
        [
          { sku: 'a', colores: ['Negro'], precioVenta: 200 },
          { sku: 'b', colores: ['Gris'], precioVenta: null },
        ],
        configuraciones,
      ),
    ).toBe('faltaPrecio');
    expect(
      problemaDeAprobacion([{ sku: 'a', colores: ['Negro'], precioVenta: 100 }], configuraciones),
    ).toBe('precioBajoCosto');
    expect(
      problemaDeAprobacion(
        [
          { sku: 'a', colores: ['Negro'], precioVenta: 200 },
          { sku: 'b', colores: [], precioVenta: null },
        ],
        configuraciones,
      ),
    ).toBeNull();
  });

  it('un precio mal escrito es un error, no un precio vacío', () => {
    expect(precioEscrito('849.900')).toBe(849_900);
    expect(precioEscrito(' ')).toBeNull();
    expect(precioEscrito('849,900')).toBe('ilegible');
    expect(precioEscrito('-5')).toBe('ilegible');
  });

  it('los colores escritos a mano se separan por coma, sin repetir', () => {
    expect(separarColoresEscritos(' Negro, Azul claro,,Negro ')).toEqual(['Negro', 'Azul claro']);
  });

  function conFotos(cantidadDeFotos: number, productoId: string | null = null): BorradorTecnologia {
    return {
      id: 'bt-1',
      proveedorId: 'prov-1',
      idModelo: 'samsung-galaxy-a17-5g',
      titulo: 'Samsung Galaxy A17 5G',
      marcaSugerida: null,
      categoriaSugerida: null,
      descripcion: 'El A17.',
      paleta: [],
      configuraciones: [configuracion()],
      productoId,
      estado: 'EN_REVISION',
      motivoRechazo: null,
      vistoEn: '2026-10-08T05:00:00Z',
      creadoEn: '2026-10-08T15:00:00Z',
      cantidadDeFotos,
      fotos: [],
    };
  }

  it('a un modelo nuevo le faltan las fotos hasta cuatro, y a uno de configuraciones nuevas ninguna', () => {
    expect(fotosQueFaltan(conFotos(1))).toBe(3);
    expect(fotosQueFaltan(conFotos(6))).toBe(0);
    expect(fotosQueFaltan(conFotos(0, 'p-1'))).toBe(0);
  });
});
