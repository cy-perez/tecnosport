import { huellaDePedido } from './huella-pedido';
import { CrearPedidoComando } from './pedido.comandos';

const COMANDO: CrearPedidoComando = {
  correo: 'compra@ejemplo.co',
  nombreComprador: 'Ana Pérez',
  contacto: { nombre: 'Ana Pérez', telefono: '3138816711' },
  lineas: [
    { varianteId: 'variante-1', cantidad: 1 },
    { varianteId: 'variante-2', cantidad: 3, enlaceCompra: 'enlace-1' },
  ],
  tipoEntrega: 'ENVIO_A_DOMICILIO',
  direccion: {
    codigoDaneDepartamento: '05',
    departamento: 'Antioquia',
    codigoDaneCiudad: '05001',
    ciudad: 'Medellín',
    direccion: 'Circular 4 # 70-20',
    indicaciones: null,
    barrio: null,
  },
  metodoPago: 'WOMPI',
  autorizaDatos: true,
  transportadora: 'Coordinadora',
};

describe('huellaDePedido', () => {
  it('es la misma para el mismo comando, aunque el carrito devuelva las líneas en otro orden', () => {
    expect(huellaDePedido({ ...COMANDO, lineas: [...COMANDO.lineas].reverse() })).toBe(
      huellaDePedido(COMANDO),
    );
  });

  it.each<[string, Partial<CrearPedidoComando>]>([
    ['otra cantidad', { lineas: [{ varianteId: 'variante-1', cantidad: 2 }, COMANDO.lineas[1]] }],
    ['una línea menos', { lineas: [COMANDO.lineas[0]] }],
    [
      'otro enlace de compra',
      { lineas: [COMANDO.lineas[0], { ...COMANDO.lineas[1], enlaceCompra: null }] },
    ],
    ['otro medio', { metodoPago: 'CONTRAENTREGA' }],
    ['otra transportadora', { transportadora: 'Servientrega' }],
    ['otra entrega', { tipoEntrega: 'RETIRO_EN_PUNTO', direccion: null, transportadora: null }],
    ['otra dirección', { direccion: { ...COMANDO.direccion!, direccion: 'Calle 10 # 1-1' } }],
    ['otro correo', { correo: 'otra@ejemplo.co' }],
    ['otro comprador', { nombreComprador: 'Carlos Ruiz' }],
    ['otro teléfono', { contacto: { nombre: 'Ana Pérez', telefono: '3000000000' } }],
  ])('cambia con %s', (_caso, cambio) => {
    expect(huellaDePedido({ ...COMANDO, ...cambio })).not.toBe(huellaDePedido(COMANDO));
  });
});
