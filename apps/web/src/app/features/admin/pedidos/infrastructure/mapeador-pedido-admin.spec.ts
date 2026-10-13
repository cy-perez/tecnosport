import type { components } from '@tecnosport/contratos';
import { aPedidoAdmin, aPedidosPaginadosAdmin } from './mapeador-pedido-admin';

type PedidosPaginadosDto = components['schemas']['PedidosPaginadosRespuesta'];
type PedidoDto = components['schemas']['PedidoRespuesta'];

function pedidoDto(variantes: string[]): PedidoDto {
  return {
    id: 'p1',
    estado: 'PAGADO',
    total: { valor: 100_000, moneda: 'COP' },
    dineroRecibido: { valor: 100_000, moneda: 'COP' },
    yaDevuelto: { valor: 0, moneda: 'COP' },
    lineas: variantes.map((varianteId, i) => ({
      id: `l${i}`,
      varianteId,
      sku: `PRV-${i}`,
      nombre: 'Polo estilo Puma - Ferrari',
      cantidad: 1,
      precioUnitario: { valor: 50_000, moneda: 'COP' },
    })),
  } as PedidoDto;
}

describe('aPedidosPaginadosAdmin', () => {
  /** Qué pedirle al proveedor llega aparte de las líneas, por variante (ADR-0085). */
  it('le pone a cada línea lo que se le pide al proveedor por su variante', () => {
    const dto: PedidosPaginadosDto = {
      items: [pedidoDto(['v-s', 'v-xxxl', 'v-sin-ajuste'])],
      tallasAlProveedor: {
        'v-s': { talla: 'M' },
        'v-xxxl': {},
        'v-de-otro-pedido': { talla: 'XL' },
      },
    };

    const lineas = aPedidosPaginadosAdmin(dto).items[0].lineas;

    expect(lineas.map((l) => l.alProveedor)).toEqual([
      { conocida: true, talla: 'M' },
      { conocida: false },
      null,
    ]);
  });

  it('un pedido suelto, como el que devuelve una acción, no lo trae', () => {
    expect(aPedidoAdmin(pedidoDto(['v-s'])).lineas[0].alProveedor).toBeNull();
  });
});
