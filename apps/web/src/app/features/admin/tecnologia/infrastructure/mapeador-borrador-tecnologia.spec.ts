import { aBorradorTecnologia, aElegirPeticion } from './mapeador-borrador-tecnologia';

describe('mapeador del borrador de tecnología', () => {
  it('lo opcional que no viene queda en nulo, y las listas tal cual', () => {
    const borrador = aBorradorTecnologia({
      id: 'b-1',
      proveedorId: 'p-1',
      idModelo: 'samsung-galaxy-a17-5g',
      titulo: 'Samsung Galaxy A17 5G',
      descripcion: 'El A17.',
      paleta: ['Negro', 'Gris'],
      configuraciones: [
        {
          sku: 'a17-1-sim',
          titulo: 'Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM',
          costoProveedor: 675000,
          coloresSugeridos: ['Negro'],
          coloresElegidos: [],
        },
      ],
      estado: 'EN_REVISION',
      vistoEn: '2026-10-08T05:00:00Z',
      creadoEn: '2026-10-08T15:00:00Z',
    });

    expect(borrador.marcaSugerida).toBeNull();
    expect(borrador.productoId).toBeNull();
    const [config] = borrador.configuraciones;
    expect(config.precioMercado).toBeNull();
    expect(config.precioVenta).toBeNull();
    expect(config.ram).toBeNull();
    expect(config.coloresSugeridos).toEqual(['Negro']);
  });

  it('una elección sin precio no manda el campo', () => {
    expect(
      aElegirPeticion([
        { sku: 'a', colores: ['Negro'], precioVenta: 829900 },
        { sku: 'b', colores: [], precioVenta: null },
      ]),
    ).toEqual({
      configuraciones: [
        { sku: 'a', colores: ['Negro'], precioVenta: 829900 },
        { sku: 'b', colores: [] },
      ],
    });
  });
});
