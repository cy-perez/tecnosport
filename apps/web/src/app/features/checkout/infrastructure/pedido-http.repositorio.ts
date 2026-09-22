import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';
import { CrearPedidoComando, MetodosDePagoDisponiblesComando } from '../domain/pedido.comandos';
import { Direccion, MetodoPago, Pedido, Seguimiento } from '../domain/pedido.model';
import { RepositorioPedidos } from '../domain/repositorio-pedidos.puerto';
import { aPedido, aSeguimiento } from './mapeador-pedido';

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
    barrio: direccion.barrio ?? undefined,
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
        nombre: comando.contacto.nombre,
        telefono: comando.contacto.telefono,
        lineas: comando.lineas.map((linea) => ({
          varianteId: linea.varianteId,
          cantidad: linea.cantidad,
        })),
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
        lineas: comando.lineas.map((linea) => ({
          varianteId: linea.varianteId,
          cantidad: linea.cantidad,
        })),
        tipoEntrega: comando.tipoEntrega,
        direccion: aDireccionRequest(comando.direccion),
      },
    });
    // Sin afirmación de tipo: el endpoint publica el enum en el OpenAPI y el cliente generado
    // devuelve la unión (`docs/09`, deuda 25). Era `as MetodoPago[]` sobre un `string[]`.
    return desempaquetar(respuesta, 'no se pudieron consultar los métodos de pago disponibles');
  }

  async reintentarPago(pedidoId: string): Promise<Pedido> {
    const respuesta = await this.cliente.POST('/api/v1/pedidos/{id}/reintentar-pago', {
      params: { path: { id: pedidoId } },
    });
    return aPedido(desempaquetar(respuesta, 'no se pudo reintentar el pago'));
  }

  async consultarSeguimiento(pedidoId: string, correo: string): Promise<Seguimiento | null> {
    const respuesta = await this.cliente.GET('/api/v1/pedidos/{id}/seguimiento', {
      params: { path: { id: pedidoId }, query: { correo } },
    });
    if (respuesta.response.status === 404) {
      return null;
    }
    return aSeguimiento(desempaquetar(respuesta, 'no se pudo consultar el estado del pedido'));
  }
}
