import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { CrearPedidoComando, MetodosDePagoDisponiblesComando } from '../domain/pedido.comandos';
import { Direccion, MetodoPago, Pedido } from '../domain/pedido.model';
import { RepositorioPedidos } from '../domain/repositorio-pedidos.puerto';
import { aPedido } from './mapeador-pedido';

function aDireccionRequest(direccion: Direccion | null) {
  if (!direccion) {
    return undefined;
  }
  return {
    codigoDaneDepartamento: direccion.codigoDaneDepartamento,
    departamento: direccion.departamento,
    codigoDaneCiudad: direccion.codigoDaneCiudad,
    ciudad: direccion.ciudad,
    direccion: direccion.direccion,
    indicaciones: direccion.indicaciones ?? undefined,
  };
}

@Injectable()
export class PedidoHttpRepositorio implements RepositorioPedidos {
  private readonly cliente = crearClienteContratos(baseUrl());

  /**
   * `Idempotency-Key` obligatoria (`docs/03-api.md`): crea un pedido nuevo en
   * cada llamada, así que sin ella un reintento de red duplicaría el pedido
   * y su reserva de inventario. Un UUID por intento del usuario, no por
   * reintento HTTP — se genera una vez aquí, por cada vez que este método se
   * invoca (una invocación = un intento real de confirmar).
   */
  async crear(comando: CrearPedidoComando): Promise<Pedido> {
    const { data, error } = await this.cliente.POST('/api/v1/pedidos', {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
      body: {
        correo: comando.correo,
        lineas: comando.lineas.map((linea) => ({ varianteId: linea.varianteId, cantidad: linea.cantidad })),
        tipoEntrega: comando.tipoEntrega,
        direccion: aDireccionRequest(comando.direccion),
        metodoPago: comando.metodoPago,
      },
    });
    if (error) {
      throw new Error('No se pudo crear el pedido.');
    }
    return aPedido(data);
  }

  async metodosDePagoDisponibles(comando: MetodosDePagoDisponiblesComando): Promise<MetodoPago[]> {
    const { data, error } = await this.cliente.POST('/api/v1/pedidos/metodos-de-pago-disponibles', {
      body: {
        correo: comando.correo,
        lineas: comando.lineas.map((linea) => ({ varianteId: linea.varianteId, cantidad: linea.cantidad })),
        tipoEntrega: comando.tipoEntrega,
        direccion: aDireccionRequest(comando.direccion),
      },
    });
    if (error) {
      throw new Error('No se pudieron consultar los métodos de pago disponibles.');
    }
    return (data ?? []) as MetodoPago[];
  }

  async reintentarPago(pedidoId: string): Promise<Pedido> {
    const { data, error } = await this.cliente.POST('/api/v1/pedidos/{id}/reintentar-pago', {
      params: { path: { id: pedidoId } },
    });
    if (error) {
      throw new Error('No se pudo reintentar el pago.');
    }
    return aPedido(data);
  }

  async consultarSeguimiento(pedidoId: string, correo: string): Promise<Pedido | null> {
    const { data, error, response } = await this.cliente.GET('/api/v1/pedidos/{id}/seguimiento', {
      params: { path: { id: pedidoId }, query: { correo } },
    });
    if (response.status === 404) {
      return null;
    }
    if (error) {
      throw new Error('No se pudo consultar el estado del pedido.');
    }
    return aPedido(data);
  }
}
