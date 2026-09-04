import { computed, inject, Injectable, signal } from '@angular/core';
import { injectMutation } from '@tanstack/angular-query-experimental';
import { IntentoDePago } from '../domain/intento-pago.model';
import { CrearPedidoComando, DatosEntrega } from '../domain/pedido.comandos';
import { MetodoPago, Pedido } from '../domain/pedido.model';
import { REPOSITORIO_PAGOS } from '../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS } from '../domain/repositorio-pedidos.puerto';

/**
 * Único en toda la app (`providedIn: 'root'`), mismo motivo que `CarritoStore`
 * (`apps/web/CLAUDE.md`): el pedido creado en la página de resumen lo
 * necesitan también la pantalla de retorno de Wompi, la de transferencia
 * manual y la de estado del pedido — páginas ruteadas sin relación
 * padre-hijo entre sí.
 *
 * Sin `injectQuery` aquí, a diferencia de `CarritoStore`: no existe un
 * `GET /pedidos/{id}` liso para releer, solo `GET /pedidos/{id}/seguimiento`
 * (con correo como token, sin sesión, `docs/03-api.md`) — y esa consulta
 * necesita `pedidoId` y `correo`, que casi nunca hacen falta si el pedido ya
 * está en esta señal. Por eso `estado.page.ts` la usa aparte
 * (`seguimiento-pedido.consulta.ts`) solo cuando `pedido` sigue en `null` —
 * la SPA se recargó entera al volver de un dominio externo (Wompi) y
 * `CheckoutStore` no sobrevivió el viaje. El pedido vive en una señal
 * simple, poblada por la mutación que lo creó o lo reintentó.
 */
@Injectable({ providedIn: 'root' })
export class CheckoutStore {
  private readonly repositorio = inject(REPOSITORIO_PEDIDOS);
  private readonly repositorioPagos = inject(REPOSITORIO_PAGOS);

  readonly pedido = signal<Pedido | null>(null);

  /**
   * Lo que la página de dirección/resumen recoge, para que la de selección
   * de método de pago lo lea sin obligar a repetir el formulario. Vive en
   * memoria, no en `localStorage`: a diferencia del id del carrito, no hace
   * falta que sobreviva un refresh — un refresh a mitad de checkout ya
   * manda de vuelta a esta página en la Fase 3 (sin cuenta ni sesión
   * todavía, no hay dónde más recuperarlo).
   */
  readonly datosEntrega = signal<DatosEntrega | null>(null);

  guardarDatosEntrega(datos: DatosEntrega): void {
    this.datosEntrega.set(datos);
    // Un cambio de dirección/tipo de entrega puede volver inválido el método
    // ya elegido (p. ej. contraentrega deja de estar disponible en la nueva
    // ciudad) — se descarta y la página de método de pago vuelve a pedirlo.
    // Si ya existía un pedido creado con los datos anteriores, tampoco sigue
    // sirviendo: un pedido es inmutable una vez creado, así que un cambio de
    // dirección exige uno nuevo, no reutilizar el viejo.
    this.metodoPago.set(null);
    this.pedido.set(null);
  }

  /** Mismo criterio que `datosEntrega`: en memoria, poblado por la página de
   * selección de método de pago para que la de creación del pedido lo lea. */
  readonly metodoPago = signal<MetodoPago | null>(null);

  elegirMetodoPago(metodo: MetodoPago): void {
    this.metodoPago.set(metodo);
    // Mismo motivo que en guardarDatosEntrega: el método de pago queda
    // congelado en el pedido ya creado, así que cambiarlo exige uno nuevo.
    this.pedido.set(null);
  }

  private readonly mutacionCrear = injectMutation(() => ({
    mutationFn: (comando: CrearPedidoComando) => this.repositorio.crear(comando),
  }));

  private readonly mutacionReintentar = injectMutation(() => ({
    mutationFn: (pedidoId: string) => this.repositorio.reintentarPago(pedidoId),
  }));

  private readonly mutacionCrearIntento = injectMutation(() => ({
    mutationFn: (pedidoId: string) => this.repositorioPagos.crearIntento(pedidoId),
  }));

  private readonly mutacionRegistrarIdTransaccion = injectMutation(() => ({
    mutationFn: (variables: { referencia: string; idTransaccionWompi: string }) =>
      this.repositorioPagos.registrarIdTransaccion(variables.referencia, variables.idTransaccionWompi),
  }));

  readonly creando = computed(() => this.mutacionCrear.isPending());
  readonly reintentando = computed(() => this.mutacionReintentar.isPending());
  readonly iniciandoPago = computed(() => this.mutacionCrearIntento.isPending());

  async crearPedido(comando: CrearPedidoComando): Promise<Pedido> {
    const pedido = await this.mutacionCrear.mutateAsync(comando);
    this.pedido.set(pedido);
    return pedido;
  }

  async reintentarPago(pedidoId: string): Promise<Pedido> {
    const pedido = await this.mutacionReintentar.mutateAsync(pedidoId);
    this.pedido.set(pedido);
    return pedido;
  }

  /** Solo para métodos que van por Wompi (`esMetodoPagoWompi`) — transferencia
   * manual y contraentrega no piden intento, `Pedido.datosTransferencia` o el
   * estado `CONFIRMADO_CONTRAENTREGA` ya traen todo lo necesario. */
  crearIntentoPago(pedidoId: string): Promise<IntentoDePago> {
    return this.mutacionCrearIntento.mutateAsync(pedidoId);
  }

  /** Llamada desde la pantalla de retorno de Wompi. Sin señal que actualizar
   * aquí: la SPA se recarga entera al volver de un dominio externo, así que
   * `pedido`/`datosEntrega`/`metodoPago` ya están en `null` de todos modos. */
  registrarIdTransaccionWompi(referencia: string, idTransaccionWompi: string): Promise<void> {
    return this.mutacionRegistrarIdTransaccion.mutateAsync({ referencia, idTransaccionWompi });
  }
}
