import { ESTADOS_PEDIDO, EstadoPedido } from './pedido-admin.model';
import { filtroDesdeQueryParams, queryParamsDesdeFiltro } from './query-params-filtro';

/**
 * Un `Record` con todos los estados del tipo: si se agrega uno a `EstadoPedido` y no a esta tabla,
 * no compila. Y la prueba de abajo exige que `ESTADOS_PEDIDO` tenga los mismos. Así, una lista
 * incompleta —como la que dejó de leer `CANCELADO` de la URL— no pasa en silencio.
 */
const TODOS: Record<EstadoPedido, true> = {
  PAGO_PENDIENTE: true,
  PAGADO: true,
  PAGO_FALLIDO: true,
  CONFIRMADO_CONTRAENTREGA: true,
  EN_PREPARACION: true,
  DESPACHADO: true,
  ENTREGADO: true,
  RECHAZADO_EN_ENTREGA: true,
  DEVUELTO: true,
  RECAUDO_PENDIENTE: true,
  RECAUDO_CONCILIADO: true,
  CANCELADO: true,
};

describe('filtro de pedidos en la URL', () => {
  it('la lista de estados tiene todos los del tipo, sin repetir', () => {
    expect([...ESTADOS_PEDIDO].sort()).toEqual(Object.keys(TODOS).sort());
  });

  it.each(ESTADOS_PEDIDO)('%s va a la URL y vuelve igual', (estado) => {
    const filtro = filtroDesdeQueryParams({ estado });

    expect(filtro.estado).toBe(estado);
    expect(queryParamsDesdeFiltro(filtro)).toEqual({ estado });
  });

  it('un estado que no existe se ignora', () => {
    expect(filtroDesdeQueryParams({ estado: 'BORRADO' }).estado).toBeNull();
  });
});
