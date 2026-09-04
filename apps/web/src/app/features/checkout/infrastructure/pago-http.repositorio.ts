import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { IntentoDePago } from '../domain/intento-pago.model';
import { RepositorioPagos } from '../domain/repositorio-pagos.puerto';
import { aIntentoDePago } from './mapeador-pago';

@Injectable()
export class PagoHttpRepositorio implements RepositorioPagos {
  private readonly cliente = crearClienteContratos(baseUrl());

  /** `Idempotency-Key` obligatoria, mismo motivo que `PedidoHttpRepositorio.crear`
   * (`docs/03-api.md`): cada llamada crea un intento de pago nuevo. */
  async crearIntento(pedidoId: string): Promise<IntentoDePago> {
    const { data, error } = await this.cliente.POST('/api/v1/pagos/intentos', {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
      body: { pedidoId },
    });
    if (error) {
      throw new Error('No se pudo iniciar el pago con Wompi.');
    }
    return aIntentoDePago(data);
  }

  /** Sin `Idempotency-Key`: no crea nada, solo sobrescribe un campo — repetir
   * la misma llamada con el mismo id es inofensivo (`ConfiguracionIdempotencia.java`
   * solo ata el filtro a `/pedidos` y `/pagos/intentos`, no a esta ruta). */
  async registrarIdTransaccion(referencia: string, idTransaccionWompi: string): Promise<void> {
    const { error } = await this.cliente.PATCH('/api/v1/pagos/intentos/{referencia}', {
      params: { path: { referencia } },
      body: { idTransaccionWompi },
    });
    if (error) {
      throw new Error('No se pudo registrar el id de transacción de Wompi.');
    }
  }
}
