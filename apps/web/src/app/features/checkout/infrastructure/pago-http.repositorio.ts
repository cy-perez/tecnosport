import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar, exigirExito } from '../../../core/http/respuesta-http';
import { IntentoDePago } from '../domain/intento-pago.model';
import { DocumentoComprador, IntentoSistecredito } from '../domain/intento-sistecredito.model';
import { RepositorioPagos } from '../domain/repositorio-pagos.puerto';
import { aIntentoDePago, aIntentoSistecredito } from './mapeador-pago';

@Injectable()
export class PagoHttpRepositorio implements RepositorioPagos {
  private readonly cliente = crearClienteContratos(baseUrl());

  /**
   * Una llave por pedido, no por llamada, y por el mismo motivo que en `PedidoHttpRepositorio`: si
   * la red se corta antes de la respuesta, la siguiente pulsación es **el mismo intento**. Con
   * llave nueva se abría un segundo intento de pago —y con Sistecrédito, una segunda solicitud de
   * crédito a nombre de una persona, que es lo que `ConfiguracionIdempotencia` dice que esta llave
   * evita—. Lo que hasta hoy salvaba ese caso era el `unique` de `pago.referencia`, no la llave.
   *
   * El pedido es el identificador natural del intento: dos intentos de pago del mismo pedido son
   * el mismo acto mientras uno no haya terminado. Se olvida al completarse.
   */
  private readonly llavesPorPedido = new Map<string, string>();

  private llaveDe(pedidoId: string): string {
    const existente = this.llavesPorPedido.get(pedidoId);
    if (existente) {
      return existente;
    }
    const nueva = crypto.randomUUID();
    this.llavesPorPedido.set(pedidoId, nueva);
    return nueva;
  }

  /** `Idempotency-Key` obligatoria, mismo motivo que `PedidoHttpRepositorio.crear`
   * (`docs/03-api.md`): cada llamada crea un intento de pago nuevo. */
  async crearIntento(pedidoId: string): Promise<IntentoDePago> {
    const respuesta = await this.cliente.POST('/api/v1/pagos/intentos', {
      headers: { 'Idempotency-Key': this.llaveDe(pedidoId) },
      body: { pedidoId },
    });
    const intento = aIntentoDePago(
      desempaquetar(respuesta, 'no se pudo iniciar el pago con Wompi'),
    );
    this.llavesPorPedido.delete(pedidoId);
    return intento;
  }

  /**
   * Con `Idempotency-Key` por lo mismo que el de Wompi: cada llamada crea un intento de pago
   * nuevo, y aquí además una transacción del lado de Sistecrédito — repetirla por un reintento
   * del navegador dejaría dos créditos abiertos para la misma compra.
   */
  async crearIntentoSistecredito(
    pedidoId: string,
    documento: DocumentoComprador,
    idioma: string,
  ): Promise<IntentoSistecredito> {
    const respuesta = await this.cliente.POST('/api/v1/pagos/sistecredito/intentos', {
      headers: { 'Idempotency-Key': this.llaveDe(pedidoId) },
      body: {
        pedidoId,
        tipoDocumento: documento.tipoDocumento,
        documento: documento.documento,
        // A qué versión del sitio vuelve el comprador. Va el código de idioma y no la URL: el
        // backend compone la dirección, y dejar que el navegador la dictara sería una
        // redirección abierta con nuestro dominio de por medio.
        idioma,
      },
    });
    const intento = aIntentoSistecredito(
      desempaquetar(respuesta, 'no se pudo iniciar el pago con Sistecrédito'),
    );
    this.llavesPorPedido.delete(pedidoId);
    return intento;
  }

  /** Sin `Idempotency-Key`: no crea nada, solo sobrescribe un campo — repetir
   * la misma llamada con el mismo id es inofensivo, y `ConfiguracionIdempotencia.java`
   * no ata el filtro a esta ruta. */
  async registrarIdTransaccion(referencia: string, idTransaccionWompi: string): Promise<void> {
    const respuesta = await this.cliente.PATCH('/api/v1/pagos/intentos/{referencia}', {
      params: { path: { referencia } },
      body: { idTransaccionWompi },
    });
    exigirExito(respuesta, 'no se pudo registrar el id de transacción de Wompi');
  }
}
