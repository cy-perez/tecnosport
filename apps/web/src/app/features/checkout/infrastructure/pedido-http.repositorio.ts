import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar, exigirExito } from '../../../core/http/respuesta-http';
import { CrearPedidoComando, MetodosDePagoDisponiblesComando } from '../domain/pedido.comandos';
import { Direccion, MetodoDePagoConPrecio, Pedido, Seguimiento } from '../domain/pedido.model';
import { RepositorioPedidos } from '../domain/repositorio-pedidos.puerto';
import { aMetodoDePagoConPrecio, aPedido, aSeguimiento } from './mapeador-pedido';
import { nuevaLlaveDeIdempotencia } from '../../../core/http/llave-de-idempotencia';

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
    this.llaveDeCreacion ??= nuevaLlaveDeIdempotencia();
    const respuesta = await this.cliente.POST('/api/v1/pedidos', {
      headers: { 'Idempotency-Key': this.llaveDeCreacion },
      body: {
        correo: comando.correo,
        nombreComprador: comando.nombreComprador,
        nombre: comando.contacto.nombre,
        telefono: comando.contacto.telefono,
        lineas: comando.lineas.map((linea) => ({
          varianteId: linea.varianteId,
          cantidad: linea.cantidad,
          enlaceCompra: linea.enlaceCompra ?? undefined,
        })),
        tipoEntrega: comando.tipoEntrega,
        direccion: aDireccionRequest(comando.direccion),
        metodoPago: comando.metodoPago,
        autorizaDatos: comando.autorizaDatos,
        transportadora: comando.transportadora ?? undefined,
      },
    });
    const pedido = aPedido(desempaquetar(respuesta, 'no se pudo crear el pedido'));
    // Solo aquí, y no en un `finally`: si `desempaquetar` lanza, el intento no se completó y la
    // llave tiene que seguir siendo la misma para que el reintento sea idempotente de verdad.
    this.llaveDeCreacion = null;
    return pedido;
  }

  /**
   * `metodos-de-pago-con-precio` y no `metodos-de-pago-disponibles`, que el servidor conserva: es
   * la misma decisión de cuáles se ofrecen, más lo que costaría la mercancía con cada uno
   * (ADR-0095). Desde que existe, el frontend no usa la otra.
   */
  async metodosDePagoConPrecio(
    comando: MetodosDePagoDisponiblesComando,
  ): Promise<MetodoDePagoConPrecio[]> {
    const respuesta = await this.cliente.POST('/api/v1/pedidos/metodos-de-pago-con-precio', {
      body: {
        correo: comando.correo,
        lineas: comando.lineas.map((linea) => ({
          varianteId: linea.varianteId,
          cantidad: linea.cantidad,
        })),
        tipoEntrega: comando.tipoEntrega,
        direccion: aDireccionRequest(comando.direccion),
        transportadora: comando.transportadora ?? undefined,
        telefono: comando.telefono ?? undefined,
      },
    });
    // Sin afirmación de tipo: el endpoint publica el enum en el OpenAPI y el cliente generado
    // devuelve la unión (`docs/09`, deuda 25).
    return desempaquetar(
      respuesta,
      'no se pudieron consultar los métodos de pago disponibles',
    ).metodos.map(aMetodoDePagoConPrecio);
  }

  async reintentarPago(pedidoId: string, correo: string): Promise<Pedido> {
    const respuesta = await this.cliente.POST('/api/v1/pedidos/{id}/reintentar-pago', {
      params: { path: { id: pedidoId } },
      body: { correo },
    });
    return aPedido(desempaquetar(respuesta, 'no se pudo reintentar el pago'));
  }

  async consultarSeguimiento(pedidoId: string, correo: string): Promise<Seguimiento | null> {
    // POST y con el correo en el cuerpo: en la consulta quedaba en los registros del servidor.
    const respuesta = await this.cliente.POST('/api/v1/pedidos/{id}/seguimiento', {
      params: { path: { id: pedidoId } },
      body: { correo },
    });
    if (respuesta.response.status === 404) {
      return null;
    }
    return aSeguimiento(desempaquetar(respuesta, 'no se pudo consultar el estado del pedido'));
  }

  /**
   * 202 sin cuerpo en todos los casos, acierte o no (ADR-0088). El 429 **no** se traga: el límite
   * por IP de este endpoint es el más estrecho de todos, y decirle "te enviamos el enlace" a quien
   * chocó con él le haría esperar un correo que no va a llegar. Sale por `exigirExito`, que es quien
   * le pone el código del servidor para que la pantalla lo distinga.
   */
  async solicitarEnlaceDeSeguimiento(numeroPedido: string, correo: string): Promise<void> {
    const respuesta = await this.cliente.POST('/api/v1/pedidos/seguimiento', {
      body: { numeroPedido, correo },
    });
    exigirExito(respuesta, 'no se pudo pedir el enlace de seguimiento');
  }
}
