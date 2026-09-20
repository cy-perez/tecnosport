import { Pedido } from './pedido.model';
import {
  datosTransferenciaDelPedido,
  esMetodoPagoSistecredito,
  esMetodoPagoWompi,
  puedeReintentarPago,
  requiereDireccion,
} from './reglas-pedido';

function pedidoDePrueba(overrides: Partial<Pedido>): Pedido {
  return {
    id: 'id-1',
    numeroPedido: 'TS-2026-000123',
    usuarioId: null,
    correo: 'compra@ejemplo.co',
    lineas: [],
    tipoEntrega: 'ENVIO_A_DOMICILIO',
    direccion: null,
    metodoPago: 'TARJETA',
    estado: 'PAGO_PENDIENTE',
    subtotal: { valor: 100_000, moneda: 'COP' },
    costoEnvio: { valor: 0, moneda: 'COP' },
    total: { valor: 100_000, moneda: 'COP' },
    creadoEn: '2026-09-04T00:00:00Z',
    contacto: null,
    datosTransferencia: null,
    ...overrides,
  };
}

describe('requiereDireccion', () => {
  it('el envío a domicilio exige dirección', () => {
    expect(requiereDireccion('ENVIO_A_DOMICILIO')).toBe(true);
  });

  it('el retiro en punto no la lleva', () => {
    expect(requiereDireccion('RETIRO_EN_PUNTO')).toBe(false);
  });
});

describe('esMetodoPagoWompi', () => {
  it.each(['TARJETA', 'PSE', 'NEQUI', 'BANCOLOMBIA', 'ADDI'] as const)(
    '%s va por Wompi',
    (metodo) => {
      expect(esMetodoPagoWompi(metodo)).toBe(true);
    },
  );

  it.each(['TRANSFERENCIA_MANUAL', 'CONTRAENTREGA'] as const)('%s no va por Wompi', (metodo) => {
    expect(esMetodoPagoWompi(metodo)).toBe(false);
  });

  /**
   * La que importa: si alguien mete SISTECREDITO en esta lista, el pedido se va a construir una
   * URL de Wompi con una firma que Sistecrédito no genera, y el comprador acaba en una pantalla
   * de error de otro proveedor.
   */
  it('Sistecrédito NO va por Wompi: lo cobra otra pasarela con otro flujo', () => {
    expect(esMetodoPagoWompi('SISTECREDITO')).toBe(false);
  });
});

describe('esMetodoPagoSistecredito', () => {
  it('reconoce su propio método', () => {
    expect(esMetodoPagoSistecredito('SISTECREDITO')).toBe(true);
  });

  it.each(['TARJETA', 'ADDI', 'TRANSFERENCIA_MANUAL', 'CONTRAENTREGA'] as const)(
    '%s no lo cobra Sistecrédito',
    (metodo) => {
      expect(esMetodoPagoSistecredito(metodo)).toBe(false);
    },
  );
});

describe('puedeReintentarPago', () => {
  it('solo un pedido en PAGO_FALLIDO admite reintento', () => {
    expect(puedeReintentarPago('PAGO_FALLIDO')).toBe(true);
  });

  it.each(['PAGO_PENDIENTE', 'PAGADO', 'CONFIRMADO_CONTRAENTREGA'] as const)(
    '%s no admite reintento',
    (estado) => {
      expect(puedeReintentarPago(estado)).toBe(false);
    },
  );
});

describe('datosTransferenciaDelPedido', () => {
  it('los expone cuando el método de pago es transferencia manual', () => {
    const datos = {
      banco: 'Bancolombia',
      tipoCuenta: 'Ahorros',
      numeroCuenta: '000-000000-00',
      titular: 'Tecno Sport',
      referencia: 'TS-2026-000123',
    };
    const pedido = pedidoDePrueba({
      metodoPago: 'TRANSFERENCIA_MANUAL',
      datosTransferencia: datos,
    });

    expect(datosTransferenciaDelPedido(pedido)).toEqual(datos);
  });

  it('devuelve null para cualquier otro método de pago, incluso si el campo llegara poblado', () => {
    const pedido = pedidoDePrueba({
      metodoPago: 'TARJETA',
      contacto: null,
      datosTransferencia: {
        banco: 'Bancolombia',
        tipoCuenta: 'Ahorros',
        numeroCuenta: '000-000000-00',
        titular: 'Tecno Sport',
        referencia: 'TS-2026-000123',
      },
    });

    expect(datosTransferenciaDelPedido(pedido)).toBeNull();
  });
});
