import { AlmacenCorreoDePedido } from '../domain/almacen-correo-de-pedido.puerto';
import { correoDelPedido } from './correo-del-pedido';

class AlmacenEnMemoria implements AlmacenCorreoDePedido {
  readonly guardados = new Map<string, string>();
  recordar(pedidoId: string, correo: string): void {
    this.guardados.set(pedidoId, correo);
  }
  correoDe(pedidoId: string): string | null {
    return this.guardados.get(pedidoId) ?? null;
  }
}

describe('correoDelPedido', () => {
  it('lee el correo del fragmento y lo recuerda para un refresco', () => {
    const almacen = new AlmacenEnMemoria();

    expect(correoDelPedido('p1', 'correo=ana%2Bts%40correo.co', almacen)).toBe('ana+ts@correo.co');
    expect(almacen.correoDe('p1')).toBe('ana+ts@correo.co');
  });

  it('sin fragmento usa lo que el navegador recordó antes de ir a la pasarela', () => {
    const almacen = new AlmacenEnMemoria();
    almacen.recordar('p1', 'ana@correo.co');

    expect(correoDelPedido('p1', null, almacen)).toBe('ana@correo.co');
  });

  it('sin ninguno de los dos no inventa nada', () => {
    expect(correoDelPedido('p1', null, new AlmacenEnMemoria())).toBeNull();
  });
});
