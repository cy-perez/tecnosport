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

  /** `Idempotency-Key` obligatoria, mismo motivo que `PedidoHttpRepositorio.crear`
   * (`docs/03-api.md`): cada llamada crea un intento de pago nuevo. */
  async crearIntento(pedidoId: string): Promise<IntentoDePago> {
    const respuesta = await this.cliente.POST('/api/v1/pagos/intentos', {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
      body: { pedidoId },
    });
    return aIntentoDePago(desempaquetar(respuesta, 'no se pudo iniciar el pago con Wompi'));
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
      headers: { 'Idempotency-Key': crypto.randomUUID() },
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
    return aIntentoSistecredito(
      desempaquetar(respuesta, 'no se pudo iniciar el pago con Sistecrédito'),
    );
  }

  /** Sin `Idempotency-Key`: no crea nada, solo sobrescribe un campo — repetir
   * la misma llamada con el mismo id es inofensivo (`ConfiguracionIdempotencia.java`
   * solo ata el filtro a `/pedidos` y `/pagos/intentos`, no a esta ruta). */
  async registrarIdTransaccion(referencia: string, idTransaccionWompi: string): Promise<void> {
    const respuesta = await this.cliente.PATCH('/api/v1/pagos/intentos/{referencia}', {
      params: { path: { referencia } },
      body: { idTransaccionWompi },
    });
    exigirExito(respuesta, 'no se pudo registrar el id de transacción de Wompi');
  }
}
