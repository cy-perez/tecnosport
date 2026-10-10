import { aPaquetesDePedido } from './mapeador-paquetes-pedido';

describe('mapeador de los paquetes de un pedido', () => {
  it('mapea cada paquete en orden y el indicador de recaudo', () => {
    const resultado = aPaquetesDePedido({
      conRecaudo: true,
      paquetes: [
        {
          pesoKg: 2,
          largoCm: 40,
          anchoCm: 30,
          altoCm: 10,
          valorDeclarado: { valor: 131_000, moneda: 'COP' },
          contenido: 'Prendas de vestir',
        },
      ],
    });

    expect(resultado).toEqual({
      conRecaudo: true,
      paquetes: [
        {
          pesoKg: 2,
          largoCm: 40,
          anchoCm: 30,
          altoCm: 10,
          valorDeclarado: { valor: 131_000, moneda: 'COP' },
          contenido: 'Prendas de vestir',
        },
      ],
    });
  });
});
