import { aDireccion, aPedido } from './mapeador-pedido';

describe('aPedido', () => {
  it('mapea el DTO completo al modelo de dominio', () => {
    const pedido = aPedido({
      id: 'pedido-1',
      numeroPedido: 'TS-2026-000001',
      usuarioId: 'usuario-1',
      correo: 'compra@ejemplo.co',
      lineas: [
        {
          id: 'linea-1',
          varianteId: 'variante-1',
          sku: 'SKU-1',
          nombre: 'Camiseta running',
          cantidad: 2,
          precioUnitario: { valor: 80_000, moneda: 'COP' },
          tasaIva: 0.19,
          imagenUrl: 'https://cdn.tecnosport.co/camiseta.webp',
        },
      ],
      tipoEntrega: 'ENVIO_A_DOMICILIO',
      direccion: {
        codigoDaneDepartamento: '05',
        departamento: 'Antioquia',
        codigoDaneCiudad: '05001',
        ciudad: 'Medellín',
        direccion: 'Cra. 26C #38B-31',
        indicaciones: 'Portería principal',
      },
      metodoPago: 'TRANSFERENCIA_MANUAL',
      estado: 'PAGO_PENDIENTE',
      total: { valor: 160_000, moneda: 'COP' },
      creadoEn: '2026-09-04T12:00:00Z',
      datosTransferencia: {
        banco: 'Bancolombia',
        tipoCuenta: 'Ahorros',
        numeroCuenta: '000-000000-00',
        titular: 'Tecno Sport',
        referencia: 'TS-2026-000001',
      },
    });

    expect(pedido).toEqual({
      id: 'pedido-1',
      numeroPedido: 'TS-2026-000001',
      usuarioId: 'usuario-1',
      correo: 'compra@ejemplo.co',
      lineas: [
        {
          id: 'linea-1',
          varianteId: 'variante-1',
          sku: 'SKU-1',
          nombre: 'Camiseta running',
          cantidad: 2,
          precioUnitario: { valor: 80_000, moneda: 'COP' },
          tasaIva: 0.19,
          imagenUrl: 'https://cdn.tecnosport.co/camiseta.webp',
        },
      ],
      tipoEntrega: 'ENVIO_A_DOMICILIO',
      direccion: {
        codigoDaneDepartamento: '05',
        departamento: 'Antioquia',
        codigoDaneCiudad: '05001',
        ciudad: 'Medellín',
        direccion: 'Cra. 26C #38B-31',
        indicaciones: 'Portería principal',
      },
      metodoPago: 'TRANSFERENCIA_MANUAL',
      estado: 'PAGO_PENDIENTE',
      total: { valor: 160_000, moneda: 'COP' },
      creadoEn: '2026-09-04T12:00:00Z',
      datosTransferencia: {
        banco: 'Bancolombia',
        tipoCuenta: 'Ahorros',
        numeroCuenta: '000-000000-00',
        titular: 'Tecno Sport',
        referencia: 'TS-2026-000001',
      },
    });
  });

  it('sin dirección ni datos de transferencia, quedan en null', () => {
    const pedido = aPedido({
      id: 'pedido-2',
      numeroPedido: 'TS-2026-000002',
      correo: 'compra@ejemplo.co',
      lineas: [],
      tipoEntrega: 'RETIRO_EN_PUNTO',
      metodoPago: 'CONTRAENTREGA',
      estado: 'CONFIRMADO_CONTRAENTREGA',
      total: { valor: 0, moneda: 'COP' },
      creadoEn: '2026-09-04T12:00:00Z',
    });

    expect(pedido.usuarioId).toBeNull();
    expect(pedido.direccion).toBeNull();
    expect(pedido.datosTransferencia).toBeNull();
  });

  it('campos ausentes se rellenan con valores por defecto, nunca undefined', () => {
    const pedido = aPedido({});

    expect(pedido).toEqual({
      id: '',
      numeroPedido: '',
      usuarioId: null,
      correo: '',
      lineas: [],
      tipoEntrega: 'ENVIO_A_DOMICILIO',
      direccion: null,
      metodoPago: 'TARJETA',
      estado: 'PAGO_PENDIENTE',
      total: { valor: 0, moneda: 'COP' },
      creadoEn: '',
      datosTransferencia: null,
    });
  });
});

describe('aDireccion', () => {
  it('campos ausentes se rellenan con valores por defecto', () => {
    expect(aDireccion({})).toEqual({
      codigoDaneDepartamento: '',
      departamento: '',
      codigoDaneCiudad: '',
      ciudad: '',
      direccion: '',
      indicaciones: null,
    });
  });
});
