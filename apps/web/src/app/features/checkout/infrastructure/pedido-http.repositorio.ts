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
   * La llave de la compra en curso, que **sobrevive a un intento fallido**.
   *
   * La premisa de antes era "una invocación = un intento real de confirmar", y por eso se generaba
   * un UUID nuevo dentro de `crear`. Esa premisa es falsa justo en el caso que la llave existe para
   * cubrir: la petición sale, la red se corta antes de la respuesta —el móvil en el ascensor, el
   * caso canónico—, el servidor ya creó el pedido y reservó el inventario, y quien compra ve un
   * error y vuelve a pulsar. Esa segunda pulsación es **el mismo intento**, no uno nuevo, y con
   * llave nueva creaba un segundo pedido con una segunda reserva sobre las mismas unidades. En
   * contraentrega esa reserva además no vence: `CrearPedido.vigenciaReserva` devuelve `null`.
   *
   * Se limpia al completarse de verdad, que es cuando el intento siguiente sí es otro.
   */
  private llaveDeCreacion: string | null = null;

  /**
   * `Idempotency-Key` obligatoria (`docs/03-api.md`): crea un pedido nuevo en
   * cada llamada, así que sin ella un reintento de red duplicaría el pedido
   * y su reserva de inventario.
   */
  async crear(comando: CrearPedidoComando): Promise<Pedido> {
    this.llaveDeCreacion ??= crypto.randomUUID();
    const respuesta = await this.cliente.POST('/api/v1/pedidos', {
      headers: { 'Idempotency-Key': this.llaveDeCreacion },
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
    const pedido = aPedido(desempaquetar(respuesta, 'no se pudo crear el pedido'));
    // Solo aquí, y no en un `finally`: si `desempaquetar` lanza, el intento no se completó y la
    // llave tiene que seguir siendo la misma para que el reintento sea idempotente de verdad.
    this.llaveDeCreacion = null;
    return pedido;
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

  async reintentarPago(pedidoId: string, correo: string): Promise<Pedido> {
    const respuesta = await this.cliente.POST('/api/v1/pedidos/{id}/reintentar-pago', {
      params: { path: { id: pedidoId } },
      body: { correo },
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

  /**
   * `POST` y no `GET`, que es lo que desentona en esta clase: el correo va en el cuerpo para que no
   * acabe en los registros de acceso ni en el historial del navegador. El hermano de arriba lo
   * lleva en la URL porque a él se llega desde el enlace de un correo.
   *
   * El 404 se traduce a `null` como en el hermano. Y el 429 **no**: el límite de intentos por IP de
   * este endpoint es más estrecho que el de los demás —el número es adivinable— y confundirlo con
   * "no encontramos tu pedido" le diría a quien consulta de buena fe que su número está mal. Cae
   * por `desempaquetar`, que es quien traduce el código del servidor al mensaje de la pantalla.
   */
  async consultarSeguimientoPorNumero(
    numeroPedido: string,
    correo: string,
  ): Promise<Seguimiento | null> {
    const respuesta = await this.cliente.POST('/api/v1/pedidos/seguimiento', {
      body: { numeroPedido, correo },
    });
    if (respuesta.response.status === 404) {
      return null;
    }
    return aSeguimiento(desempaquetar(respuesta, 'no se pudo consultar el estado del pedido'));
  }
}
