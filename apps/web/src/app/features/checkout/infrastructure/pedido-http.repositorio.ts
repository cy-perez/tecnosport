import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';
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
    const respuesta = await this.cliente.POST('/api/v1/pedidos', {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
      body: {
        correo: comando.correo,
        lineas: comando.lineas.map((linea) => ({ varianteId: linea.varianteId, cantidad: linea.cantidad })),
        tipoEntrega: comando.tipoEntrega,
        direccion: aDireccionRequest(comando.direccion),
        metodoPago: comando.metodoPago,
        autorizaDatos: comando.autorizaDatos,
      },
    });
    return aPedido(desempaquetar(respuesta, 'no se pudo crear el pedido'));
  }

  async metodosDePagoDisponibles(comando: MetodosDePagoDisponiblesComando): Promise<MetodoPago[]> {
    const respuesta = await this.cliente.POST('/api/v1/pedidos/metodos-de-pago-disponibles', {
      body: {
        correo: comando.correo,
        lineas: comando.lineas.map((linea) => ({ varianteId: linea.varianteId, cantidad: linea.cantidad })),
        tipoEntrega: comando.tipoEntrega,
        direccion: aDireccionRequest(comando.direccion),
      },
    });
    return desempaquetar(respuesta, 'no se pudieron consultar los métodos de pago disponibles') as MetodoPago[];
  }

  async reintentarPago(pedidoId: string): Promise<Pedido> {
    const respuesta = await this.cliente.POST('/api/v1/pedidos/{id}/reintentar-pago', {
      params: { path: { id: pedidoId } },
    });
    return aPedido(desempaquetar(respuesta, 'no se pudo reintentar el pago'));
  }

  async consultarSeguimiento(pedidoId: string, correo: string): Promise<Pedido | null> {
    const respuesta = await this.cliente.GET('/api/v1/pedidos/{id}/seguimiento', {
      params: { path: { id: pedidoId }, query: { correo } },
    });
    if (respuesta.response.status === 404) {
      return null;
    }
    return aPedido(desempaquetar(respuesta, 'no se pudo consultar el estado del pedido'));
  }
}
