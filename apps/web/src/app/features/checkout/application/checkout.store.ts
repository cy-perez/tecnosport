import { computed, inject, Injectable, signal } from '@angular/core';
import { injectMutation } from '@tanstack/angular-query-experimental';
import { IntentoDePago } from '../domain/intento-pago.model';
import { DocumentoComprador, IntentoSistecredito } from '../domain/intento-sistecredito.model';
import { huellaDePedido } from '../domain/huella-pedido';
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
   * La huella del comando con el que se creó `pedido` (`huellaDePedido`), o `null` si ese pedido ya
   * no se puede reutilizar: llegó por un reintento desde la pantalla de estado, o la compra terminó.
   *
   * <p>Confirmar reutilizaba `pedido` con solo que existiera, confiando en que todo cambio que lo
   * invalidara pasaba por `guardarDatosEntrega` o `elegirMetodoPago`. El carrito no pasa por aquí:
   * si el intento de pago fallaba y el comprador cambiaba el carrito y volvía con «Atrás», se
   * cobraba el pedido viejo con su total viejo y después se borraba el carrito nuevo.
   */
  private readonly huellaDelPedido = signal<string | null>(null);

  /** El pedido ya creado, solo si se creó con exactamente este comando. */
  pedidoReutilizable(comando: CrearPedidoComando): Pedido | null {
    const pedido = this.pedido();
    return pedido !== null && this.huellaDelPedido() === huellaDePedido(comando) ? pedido : null;
  }

  /**
   * La compra terminó —contraentrega o pago en el punto confirmados, transferencia con sus datos en
   * pantalla—: se suelta todo lo que serviría para volver a cobrar. El pedido se queda para que la
   * pantalla de estado o la de transferencia lo enseñen sin pedirlo otra vez al servidor —y sin
   * depender de que el navegador haya podido recordar el correo—, pero ya sin huella: ningún
   * comando lo vuelve a reutilizar. Los datos de entrega se van con él, así que la próxima compra
   * empieza por el resumen.
   */
  terminarCompra(): void {
    this.huellaDelPedido.set(null);
    this.metodoPago.set(null);
    this.datosEntrega.set(null);
    this.documentoComprador.set(null);
    this.medioYaNoDisponible.set(false);
  }

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

  /**
   * La transportadora que se eligió en su propia página (ADR-0073), sobre los datos de entrega que
   * el resumen ya guardó. Descarta el método de pago y el pedido por lo mismo que
   * `guardarDatosEntrega`: la contraentrega depende de si la elegida recauda, y un pedido creado
   * con otra ya no sirve. Sin datos de entrega no hay a qué ponérsela, y no hace nada.
   */
  elegirTransportadora(transportadora: string): void {
    const datos = this.datosEntrega();
    if (!datos) {
      return;
    }
    this.guardarDatosEntrega({ ...datos, transportadora });
  }

  /**
   * Mismo criterio que `datosEntrega`: en memoria, poblado por la página de selección de método de
   * pago para que la de creación del pedido lo lea. **Solo el medio, sin su precio** (ADR-0095).
   * Guardaba también el subtotal y el descuento que el servidor dio al elegir, y confirmar los
   * enseñaba: una copia que nada invalidaba si después cambiaba el carrito, un precio o la tarifa,
   * mientras el pedido se cobra con el carrito vivo. Confirmar vuelve a pedir el precio de cada
   * medio y toma este por su nombre.
   */
  readonly metodoPago = signal<MetodoPago | null>(null);

  /**
   * Confirmar descubrió que el medio elegido ya no se ofrece para este carrito y esta entrega, y
   * devolvió al comprador a elegir otro: la página de métodos de pago lo dice.
   */
  readonly medioYaNoDisponible = signal(false);

  /** Lo llama confirmar cuando la consulta fresca ya no trae el medio elegido. */
  descartarMetodoNoDisponible(): void {
    this.metodoPago.set(null);
    this.pedido.set(null);
    this.medioYaNoDisponible.set(true);
  }

  elegirMetodoPago(metodo: MetodoPago): void {
    this.metodoPago.set(metodo);
    this.medioYaNoDisponible.set(false);
    // Mismo motivo que en guardarDatosEntrega: el método de pago queda
    // congelado en el pedido ya creado, así que cambiarlo exige uno nuevo.
    this.pedido.set(null);
    if (metodo !== 'SISTECREDITO') {
      // Elegir otro método suelta el documento: es un dato personal que solo tiene sentido
      // mientras Sistecrédito sea el método elegido, y dejarlo en memoria después sería
      // guardarlo sin motivo (`adr/0048`).
      this.documentoComprador.set(null);
    }
  }

  /**
   * Quién pide el crédito, solo para Sistecrédito. **En memoria y nunca en `localStorage`**, a
   * diferencia del id del carrito: es un número de documento, y el backend tampoco lo guarda. Se
   * pierde en un refresh a propósito — volver a teclearlo es barato, y es lo que evita que quede
   * por ahí un dato personal que ya no hace falta.
   */
  readonly documentoComprador = signal<DocumentoComprador | null>(null);

  anotarDocumentoComprador(documento: DocumentoComprador | null): void {
    this.documentoComprador.set(documento);
  }

  private readonly mutacionCrear = injectMutation(() => ({
    mutationFn: (comando: CrearPedidoComando) => this.repositorio.crear(comando),
  }));

  private readonly mutacionReintentar = injectMutation(() => ({
    mutationFn: ({ pedidoId, correo }: { pedidoId: string; correo: string }) =>
      this.repositorio.reintentarPago(pedidoId, correo),
  }));

  private readonly mutacionCrearIntento = injectMutation(() => ({
    mutationFn: (pedidoId: string) => this.repositorioPagos.crearIntento(pedidoId),
  }));

  private readonly mutacionCrearIntentoSistecredito = injectMutation(() => ({
    mutationFn: (variables: { pedidoId: string; documento: DocumentoComprador; idioma: string }) =>
      this.repositorioPagos.crearIntentoSistecredito(
        variables.pedidoId,
        variables.documento,
        variables.idioma,
      ),
  }));

  private readonly mutacionRegistrarIdTransaccion = injectMutation(() => ({
    mutationFn: (variables: { referencia: string; idTransaccionWompi: string }) =>
      this.repositorioPagos.registrarIdTransaccion(
        variables.referencia,
        variables.idTransaccionWompi,
      ),
  }));

  readonly creando = computed(() => this.mutacionCrear.isPending());
  readonly reintentando = computed(() => this.mutacionReintentar.isPending());
  readonly iniciandoPago = computed(
    () =>
      this.mutacionCrearIntento.isPending() || this.mutacionCrearIntentoSistecredito.isPending(),
  );

  async crearPedido(comando: CrearPedidoComando): Promise<Pedido> {
    const pedido = await this.mutacionCrear.mutateAsync(comando);
    this.pedido.set(pedido);
    this.huellaDelPedido.set(huellaDePedido(comando));
    return pedido;
  }

  async reintentarPago(pedidoId: string, correo: string): Promise<Pedido> {
    const pedido = await this.mutacionReintentar.mutateAsync({ pedidoId, correo });
    this.pedido.set(pedido);
    // De la pantalla de estado, sin comando: no hay con qué compararlo, y confirmar no lo reutiliza.
    this.huellaDelPedido.set(null);
    return pedido;
  }

  /** Solo para métodos que van por Wompi (`esMetodoPagoWompi`) — transferencia
   * manual y contraentrega no piden intento, `Pedido.datosTransferencia` o el
   * estado `CONFIRMADO_CONTRAENTREGA` ya traen todo lo necesario. */
  crearIntentoPago(pedidoId: string): Promise<IntentoDePago> {
    return this.mutacionCrearIntento.mutateAsync(pedidoId);
  }

  /** Solo para `SISTECREDITO`. Devuelve la URL a la que hay que mandar al comprador, ya hecha. */
  crearIntentoSistecredito(
    pedidoId: string,
    documento: DocumentoComprador,
    idioma: string,
  ): Promise<IntentoSistecredito> {
    return this.mutacionCrearIntentoSistecredito.mutateAsync({ pedidoId, documento, idioma });
  }

  /** Llamada desde la pantalla de retorno de Wompi. Sin señal que actualizar
   * aquí: la SPA se recarga entera al volver de un dominio externo, así que
   * `pedido`/`datosEntrega`/`metodoPago` ya están en `null` de todos modos. */
  registrarIdTransaccionWompi(referencia: string, idTransaccionWompi: string): Promise<void> {
    return this.mutacionRegistrarIdTransaccion.mutateAsync({ referencia, idTransaccionWompi });
  }
}
