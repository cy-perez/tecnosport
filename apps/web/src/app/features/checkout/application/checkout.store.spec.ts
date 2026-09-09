import { Component, inject } from '@angular/core';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { render } from '@testing-library/angular';
import { IntentoDePago } from '../domain/intento-pago.model';
import { CrearPedidoComando } from '../domain/pedido.comandos';
import { MetodoPago, Pedido, Seguimiento } from '../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../domain/repositorio-pedidos.puerto';
import { CheckoutStore } from './checkout.store';

function pedidoDePrueba(overrides: Partial<Pedido> = {}): Pedido {
  return {
    id: 'pedido-1',
    numeroPedido: 'TS-2026-000001',
    usuarioId: null,
    correo: 'compra@ejemplo.co',
    lineas: [],
    tipoEntrega: 'ENVIO_A_DOMICILIO',
    direccion: null,
    metodoPago: 'TARJETA',
    estado: 'PAGO_PENDIENTE',
    total: { valor: 150_000, moneda: 'COP' },
    creadoEn: '2026-01-01T00:00:00Z',
    datosTransferencia: null,
    ...overrides,
  };
}

class RepositorioPedidosFalso implements RepositorioPedidos {
  llamadasCrear = 0;
  llamadasReintentar = 0;
  private pedido = pedidoDePrueba();

  async crear(): Promise<Pedido> {
    this.llamadasCrear++;
    return this.pedido;
  }

  async metodosDePagoDisponibles(): Promise<MetodoPago[]> {
    return ['TARJETA', 'CONTRAENTREGA'];
  }

  async reintentarPago(): Promise<Pedido> {
    this.llamadasReintentar++;
    this.pedido = { ...this.pedido, estado: 'PAGO_PENDIENTE' };
    return this.pedido;
  }

  async consultarSeguimiento(): Promise<Seguimiento | null> {
    throw new Error('no usado en esta prueba');
  }
}

class RepositorioPagosFalso implements RepositorioPagos {
  llamadasCrearIntento = 0;
  llamadasRegistrarIdTransaccion: { referencia: string; idTransaccionWompi: string }[] = [];

  async crearIntento(): Promise<IntentoDePago> {
    this.llamadasCrearIntento++;
    return {
      referencia: 'TS-2026-000001-1',
      monto: { valor: 150_000, moneda: 'COP' },
      firmaIntegridad: 'firma',
      llavePublica: 'pub_test_xyz',
      ambiente: 'sandbox',
    };
  }

  async registrarIdTransaccion(referencia: string, idTransaccionWompi: string): Promise<void> {
    this.llamadasRegistrarIdTransaccion.push({ referencia, idTransaccionWompi });
  }
}

function comandoDePrueba(): CrearPedidoComando {
  return {
    correo: 'compra@ejemplo.co',
    lineas: [{ varianteId: 'variante-1', cantidad: 1 }],
    tipoEntrega: 'RETIRO_EN_PUNTO',
    direccion: null,
    metodoPago: 'TARJETA',
    autorizaDatos: true,
  };
}

@Component({ selector: 'app-anfitrion-de-prueba', template: '' })
class AnfitrionDePrueba {
  readonly store = inject(CheckoutStore);
}

async function renderConRepositorio(repositorio: RepositorioPedidos, pagos: RepositorioPagos = new RepositorioPagosFalso()) {
  const { fixture } = await render(AnfitrionDePrueba, {
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_PEDIDOS, useValue: repositorio },
      { provide: REPOSITORIO_PAGOS, useValue: pagos },
    ],
  });
  return { store: fixture.componentInstance.store };
}

describe('CheckoutStore', () => {
  it('crearPedido llama al repositorio y deja el resultado en la señal pedido', async () => {
    const repositorio = new RepositorioPedidosFalso();
    const { store } = await renderConRepositorio(repositorio);

    const resultado = await store.crearPedido(comandoDePrueba());

    expect(repositorio.llamadasCrear).toBe(1);
    expect(store.pedido()).toEqual(resultado);
    expect(store.pedido()?.numeroPedido).toBe('TS-2026-000001');
  });

  it('reintentarPago llama al repositorio y actualiza la señal pedido', async () => {
    const repositorio = new RepositorioPedidosFalso();
    const { store } = await renderConRepositorio(repositorio);
    await store.crearPedido(comandoDePrueba());

    const resultado = await store.reintentarPago('pedido-1');

    expect(repositorio.llamadasReintentar).toBe(1);
    expect(store.pedido()?.estado).toBe('PAGO_PENDIENTE');
    expect(resultado.estado).toBe('PAGO_PENDIENTE');
  });

  it('pedido arranca en null antes de crear nada', async () => {
    const { store } = await renderConRepositorio(new RepositorioPedidosFalso());

    expect(store.pedido()).toBeNull();
  });

  it('elegirMetodoPago deja el valor en la señal metodoPago', async () => {
    const { store } = await renderConRepositorio(new RepositorioPedidosFalso());

    store.elegirMetodoPago('CONTRAENTREGA');

    expect(store.metodoPago()).toBe('CONTRAENTREGA');
  });

  it('guardarDatosEntrega descarta el método de pago y el pedido ya creados', async () => {
    const { store } = await renderConRepositorio(new RepositorioPedidosFalso());
    store.elegirMetodoPago('CONTRAENTREGA');
    await store.crearPedido(comandoDePrueba());

    store.guardarDatosEntrega({
      correo: 'compra@ejemplo.co',
      tipoEntrega: 'RETIRO_EN_PUNTO',
      direccion: null,
      autorizaDatos: true,
    });

    expect(store.metodoPago()).toBeNull();
    expect(store.pedido()).toBeNull();
  });

  it('elegirMetodoPago descarta el pedido ya creado con el método anterior', async () => {
    const { store } = await renderConRepositorio(new RepositorioPedidosFalso());
    store.elegirMetodoPago('TARJETA');
    await store.crearPedido(comandoDePrueba());

    store.elegirMetodoPago('CONTRAENTREGA');

    expect(store.pedido()).toBeNull();
  });

  it('crearIntentoPago llama al repositorio de pagos y devuelve el intento', async () => {
    const pagos = new RepositorioPagosFalso();
    const { store } = await renderConRepositorio(new RepositorioPedidosFalso(), pagos);

    const intento = await store.crearIntentoPago('pedido-1');

    expect(pagos.llamadasCrearIntento).toBe(1);
    expect(intento.referencia).toBe('TS-2026-000001-1');
  });

  it('registrarIdTransaccionWompi llama al repositorio de pagos con la referencia y el id', async () => {
    const pagos = new RepositorioPagosFalso();
    const { store } = await renderConRepositorio(new RepositorioPedidosFalso(), pagos);

    await store.registrarIdTransaccionWompi('TS-2026-000001-1', '01-1531231271-19365');

    expect(pagos.llamadasRegistrarIdTransaccion).toEqual([
      { referencia: 'TS-2026-000001-1', idTransaccionWompi: '01-1531231271-19365' },
    ]);
  });
});
