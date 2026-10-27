import { CrearPedidoComando } from './pedido.comandos';

/**
 * Lo que identifica a un pedido ya creado frente al comando de ahora: las líneas con su cantidad y
 * su enlace de compra, la entrega, la transportadora, el medio y los datos de quien compra. Dos
 * comandos con la misma huella crearían el mismo pedido; con otra, reutilizar el ya creado cobraría
 * un carrito que no es el que el comprador está viendo —y después se borraría el de ahora—.
 *
 * <p>Las líneas se ordenan por variante: el orden en que el carrito las devuelve no cambia lo que se
 * compra. El resto va en una lista de posición fija, para que `JSON.stringify` dé siempre la misma
 * cadena sin depender del orden de las llaves de un objeto.
 */
export function huellaDePedido(comando: CrearPedidoComando): string {
  const lineas = comando.lineas
    .map((linea) => [linea.varianteId, linea.cantidad, linea.enlaceCompra ?? null] as const)
    .sort(([una], [otra]) => (una < otra ? -1 : una > otra ? 1 : 0));
  const direccion = comando.direccion;
  return JSON.stringify([
    comando.correo,
    comando.contacto.nombre,
    comando.contacto.telefono,
    lineas,
    comando.tipoEntrega,
    direccion
      ? [
          direccion.codigoDaneDepartamento,
          direccion.codigoDaneCiudad,
          direccion.direccion,
          direccion.indicaciones,
          direccion.barrio,
        ]
      : null,
    comando.transportadora,
    comando.metodoPago,
    comando.autorizaDatos,
  ]);
}
