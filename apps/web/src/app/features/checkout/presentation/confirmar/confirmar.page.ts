import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { CheckoutStore } from '../../application/checkout.store';
import { CrearPedidoComando } from '../../domain/pedido.comandos';
import { MetodoPago, Pedido } from '../../domain/pedido.model';
import { esMetodoPagoWompi } from '../../domain/reglas-pedido';
import { urlWebCheckoutWompi } from '../../domain/wompi';

const CLAVE_ETIQUETA: Record<MetodoPago, string> = {
  TARJETA: 'checkout.metodoPago.tarjeta',
  PSE: 'checkout.metodoPago.pse',
  NEQUI: 'checkout.metodoPago.nequi',
  BANCOLOMBIA: 'checkout.metodoPago.bancolombia',
  ADDI: 'checkout.metodoPago.addi',
  TRANSFERENCIA_MANUAL: 'checkout.metodoPago.transferencia_manual',
  CONTRAENTREGA: 'checkout.metodoPago.contraentrega',
};

/**
 * Tercer y último paso antes de que exista el pedido (Fase 3, paso 4c de
 * `docs/09-plan-de-arranque.md`). Sin dirección o sin método de pago
 * guardados, no hay nada que confirmar — vuelve al paso que falta.
 *
 * Después de crear el pedido:
 * - Wompi (`esMetodoPagoWompi`): pide el intento de pago y **redirige el
 *   navegador** al Web Checkout hospedado — el cliente sale del sitio unos
 *   segundos, a propósito (`docs/09-plan-de-arranque.md`).
 * - Transferencia manual: el pedido ya trae `datosTransferencia`, no hace
 *   falta pedir nada más — a la pantalla de transferencia (paso 4e).
 * - Contraentrega: no se cobra nada, el pedido ya quedó confirmado — a la
 *   pantalla de estado (paso 4f).
 */
@Component({
  selector: 'app-confirmar',
  imports: [RouterLink, TranslocoPipe, TsBoton, TsEsqueleto, TsPrecio],
  templateUrl: './confirmar.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmarPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();
  protected readonly carrito = inject(CarritoStore);
  protected readonly checkout = inject(CheckoutStore);

  protected readonly error = signal<string | null>(null);

  protected readonly subtotal = computed(() => {
    const datosCarrito = this.carrito.consulta.data();
    if (!datosCarrito) {
      return 0;
    }
    return datosCarrito.lineas.reduce((suma, linea) => {
      const snapshot = this.carrito.snapshotDeLinea(linea.varianteId);
      return suma + (snapshot ? snapshot.precioValor * linea.cantidad : 0);
    }, 0);
  });

  protected readonly etiquetaMetodoPago = computed(() => {
    const metodo = this.checkout.metodoPago();
    return metodo ? this.traducir()(CLAVE_ETIQUETA[metodo]) : '';
  });

  protected readonly enviando = computed(
    () => this.checkout.creando() || this.checkout.iniciandoPago(),
  );

  constructor() {
    effect(() => {
      const datosCarrito = this.carrito.consulta.data();
      const sinDatosEntrega = this.checkout.datosEntrega() === null;
      const sinMetodoPago = this.checkout.metodoPago() === null;
      const carritoVacio = !!datosCarrito && datosCarrito.lineas.length === 0;

      if (carritoVacio || sinDatosEntrega) {
        void this.router.navigate(['../resumen'], { relativeTo: this.route });
      } else if (sinMetodoPago) {
        void this.router.navigate(['../metodo-pago'], { relativeTo: this.route });
      }
    });
  }

  protected async confirmar(): Promise<void> {
    const datos = this.checkout.datosEntrega();
    const metodoPago = this.checkout.metodoPago();
    const datosCarrito = this.carrito.consulta.data();
    if (!datos || !metodoPago || !datosCarrito || datosCarrito.lineas.length === 0) {
      return;
    }
    this.error.set(null);

    const comando: CrearPedidoComando = {
      correo: datos.correo,
      lineas: datosCarrito.lineas.map((linea) => ({ varianteId: linea.varianteId, cantidad: linea.cantidad })),
      tipoEntrega: datos.tipoEntrega,
      direccion: datos.direccion,
      metodoPago,
      autorizaDatos: datos.autorizaDatos,
    };

    try {
      // Un reintento tras un fallo a mitad de camino (p. ej. creando el
      // intento de pago) no vuelve a crear el pedido: ya existe, `POST
      // /pedidos` no es el paso a repetir. `CheckoutStore.guardarDatosEntrega`
      // y `elegirMetodoPago` limpian `pedido` cada vez que cambia algo que lo
      // volvería inválido, así que si sigue ahí es el mismo intento.
      const pedido = this.checkout.pedido() ?? (await this.checkout.crearPedido(comando));
      await this.continuarSegunMetodoPago(pedido);
      // Aquí y no antes. Las líneas ya son del pedido, así que dejar el carrito lleno invita a
      // comprarlas dos veces; pero limpiarlo apenas se crea el pedido rompería el reintento de
      // arriba, porque la guarda del principio de este método exige un carrito con líneas. Si
      // `continuarSegunMetodoPago` falla —el intento de pago de Wompi, por ejemplo— no se llega
      // hasta acá y el carrito queda intacto para volver a intentarlo.
      this.carrito.limpiar();
    } catch {
      this.error.set(this.transloco.translate('checkout.confirmar.error'));
    }
  }

  private async continuarSegunMetodoPago(pedido: Pedido): Promise<void> {
    if (esMetodoPagoWompi(pedido.metodoPago)) {
      const intento = await this.checkout.crearIntentoPago(pedido.id);
      const idioma = this.transloco.activeLang();
      // referencia, pedidoId y correo van en la propia URL de retorno (no en
      // el estado de la app): Wompi solo le agrega `?id=...` a lo que se le
      // pasó, y la SPA se recarga entera al volver de un dominio externo —
      // `CheckoutStore` no sobrevive ese viaje. `id` lo agrega Wompi mismo;
      // `correo` hace falta para `GET /pedidos/{id}/seguimiento` en la
      // pantalla de estado (docs/03-api.md: "con token del correo").
      const parametrosRetorno = new URLSearchParams({
        referencia: intento.referencia,
        pedidoId: pedido.id,
        correo: pedido.correo,
      });
      const urlRetorno = `${window.location.origin}/${idioma}/checkout/retorno-wompi?${parametrosRetorno.toString()}`;
      window.location.href = urlWebCheckoutWompi(intento, urlRetorno);
      return;
    }

    if (pedido.metodoPago === 'TRANSFERENCIA_MANUAL') {
      // Con parámetros, igual que el retorno de Wompi: un refresh en la
      // pantalla de transferencia (el comprador copiando el número de
      // cuenta con calma) no debería perder los datos de la cuenta.
      void this.router.navigate(['../transferencia'], {
        relativeTo: this.route,
        queryParams: { pedidoId: pedido.id, correo: pedido.correo },
      });
      return;
    }

    void this.router.navigate(['../estado'], { relativeTo: this.route });
  }
}
