import { aBorradorTecnologia, aElegirPeticion } from './mapeador-borrador-tecnologia';

describe('mapeador del borrador de tecnología', () => {
  /** Los dos sugeridos los calcula el servidor (ADR-0095); el panel solo los lleva. */
  it('lleva el sugerido de contado y el publicado de cada configuración', () => {
    const borrador = aBorradorTecnologia({
      id: 'b-1',
      proveedorId: 'p-1',
      idModelo: 'm',
      titulo: 'M',
      descripcion: '',
      descripcionDeLaSkill: '',
      fichaEditada: false,
      fichaNuevaDeLaSkill: false,
      paleta: [],
      configuraciones: [
        {
          sku: 'a',
          titulo: 'A',
          costoProveedor: 675000,
          precioMercado: 1200000,
          precioContadoSugerido: 1075000,
          precioPublicadoSugerido: 1111000,
          coloresSugeridos: [],
          coloresElegidos: [],
        },
      ],
      estado: 'EN_REVISION',
      vistoEn: '2026-10-08T05:00:00Z',
      creadoEn: '2026-10-08T15:00:00Z',
      cantidadDeFotos: 0,
      fotosQueFaltan: 4,
      fotos: [],
    });

    const [config] = borrador.configuraciones;
    expect(config.precioContadoSugerido).toBe(1075000);
    expect(config.precioPublicadoSugerido).toBe(1111000);
  });

  it('lo opcional que no viene queda en nulo, y las listas tal cual', () => {
    const borrador = aBorradorTecnologia({
      id: 'b-1',
      proveedorId: 'p-1',
      idModelo: 'samsung-galaxy-a17-5g',
      titulo: 'Samsung Galaxy A17 5G',
      descripcion: 'El A17, editado.',
      descripcionDeLaSkill: 'El A17.',
      fichaEditada: true,
      fichaNuevaDeLaSkill: true,
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
      cantidadDeFotos: 1,
      fotosQueFaltan: 3,
      fotos: [
        {
          id: 'f-1',
          url: 'https://firmada/f-1',
          ancho: 1200,
          alto: 1200,
          hash: 'a'.repeat(64),
          altEs: 'Galaxy A17 sobre fondo blanco',
          altEn: 'Galaxy A17 on a white background',
        },
      ],
    });

    expect(borrador.descripcion).toBe('El A17, editado.');
    expect(borrador.descripcionDeLaSkill).toBe('El A17.');
    expect(borrador.fichaEditada).toBe(true);
    expect(borrador.fichaNuevaDeLaSkill).toBe(true);
    expect(borrador.marcaSugerida).toBeNull();
    expect(borrador.productoId).toBeNull();
    const [config] = borrador.configuraciones;
    expect(config.precioMercado).toBeNull();
    expect(config.precioContadoSugerido).toBeNull();
    expect(config.precioPublicadoSugerido).toBeNull();
    expect(config.precioVenta).toBeNull();
    expect(config.ram).toBeNull();
    expect(config.coloresSugeridos).toEqual(['Negro']);
    expect(borrador.cantidadDeFotos).toBe(1);
    expect(borrador.fotosQueFaltan).toBe(3);
    expect(borrador.fotos).toEqual([
      {
        id: 'f-1',
        url: 'https://firmada/f-1',
        ancho: 1200,
        alto: 1200,
        altEs: 'Galaxy A17 sobre fondo blanco',
        altEn: 'Galaxy A17 on a white background',
      },
    ]);
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
