import { METODOS_DE_PAGO_AL_DIA, aDireccion, aPedido } from './mapeador-pedido';

describe('aPedido', () => {
  it('mapea el DTO completo al modelo de dominio', () => {
    const pedido = aPedido({
      id: 'pedido-1',
      numeroPedido: 'TS-2026-000001',
      usuarioId: 'usuario-1',
      correo: 'compra@ejemplo.co',
      contacto: { nombre: 'Ana Pérez', telefono: '3138816711' },
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
        barrio: 'Boston',
      },
      metodoPago: 'TRANSFERENCIA_MANUAL',
      estado: 'PAGO_PENDIENTE',
      subtotal: { valor: 160_000, moneda: 'COP' },
      costoEnvio: { valor: 0, moneda: 'COP' },
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
      contacto: { nombre: 'Ana Pérez', telefono: '3138816711' },
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
        barrio: 'Boston',
      },
      metodoPago: 'TRANSFERENCIA_MANUAL',
      estado: 'PAGO_PENDIENTE',
      subtotal: { valor: 160_000, moneda: 'COP' },
      costoEnvio: { valor: 0, moneda: 'COP' },
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
      contacto: { nombre: 'Ana Pérez', telefono: '3138816711' },
      lineas: [],
      tipoEntrega: 'RETIRO_EN_PUNTO',
      metodoPago: 'CONTRAENTREGA',
      estado: 'CONFIRMADO_CONTRAENTREGA',
      subtotal: { valor: 0, moneda: 'COP' },
      costoEnvio: { valor: 0, moneda: 'COP' },
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
      contacto: null,
      lineas: [],
      tipoEntrega: 'ENVIO_A_DOMICILIO',
      direccion: null,
      metodoPago: 'TARJETA',
      estado: 'PAGO_PENDIENTE',
      subtotal: { valor: 0, moneda: 'COP' },
      costoEnvio: { valor: 0, moneda: 'COP' },
      total: { valor: 0, moneda: 'COP' },
      creadoEn: '',
      datosTransferencia: null,
    });
  });

  /**
   * Sistecrédito llegó al enum del backend y el panel se quedó sin él durante días, tapado por un
   * `as MetodoPago` (`docs/09`, deuda 25). Lo que impide que vuelva a pasar es el compilador —
   * `METODOS_DE_PAGO_AL_DIA` no compila si las dos uniones se separan—, y esta prueba vigila el
   * otro lado: que el mapeador pase el valor tal cual y no lo reemplace por el de por omisión.
   */
  it('un método que llegó después al enum pasa tal cual, sin caer en el de por omisión', () => {
    const pedido = aPedido({ metodoPago: 'SISTECREDITO' });

    expect(pedido.metodoPago).toBe('SISTECREDITO');
    expect(METODOS_DE_PAGO_AL_DIA).toBe(true);
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
      barrio: null,
    });
  });
});
