import { DatePipe, NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { CheckoutStore } from '../../application/checkout.store';
import { CriteriosSeguimiento, usarSeguimientoPedido } from '../../application/seguimiento-pedido.consulta';
import { EstadoPedido, Pedido, RetractoPublico } from '../../domain/pedido.model';
import { esMetodoPagoWompi, puedeReintentarPago } from '../../domain/reglas-pedido';
import { urlWebCheckoutWompi } from '../../domain/wompi';

const CLAVE_ETIQUETA_ESTADO: Record<EstadoPedido, string> = {
  PAGO_PENDIENTE: 'checkout.estado.estados.pago_pendiente',
  PAGADO: 'checkout.estado.estados.pagado',
  PAGO_FALLIDO: 'checkout.estado.estados.pago_fallido',
  CONFIRMADO_CONTRAENTREGA: 'checkout.estado.estados.confirmado_contraentrega',
  EN_PREPARACION: 'checkout.estado.estados.en_preparacion',
  DESPACHADO: 'checkout.estado.estados.despachado',
  ENTREGADO: 'checkout.estado.estados.entregado',
  RECHAZADO_EN_ENTREGA: 'checkout.estado.estados.rechazado_en_entrega',
  DEVUELTO: 'checkout.estado.estados.devuelto',
  RECAUDO_PENDIENTE: 'checkout.estado.estados.recaudo_pendiente',
  RECAUDO_CONCILIADO: 'checkout.estado.estados.recaudo_conciliado',
};

/**
 * Último paso del checkout (Fase 3, paso 4f de `docs/09-plan-de-arranque.md`)
 * — adonde llegan contraentrega (directo desde `ConfirmarPage`, sin salir
 * del sitio) y el retorno de Wompi (`RetornoWompiPage`, con `pedidoId` y
 * `correo` en la URL porque la SPA se recargó entera).
 *
 * `CheckoutStore.pedido` manda si ya está poblado (no hizo falta salir del
 * sitio): se muestra directo, sin llamar al servidor. Si no, se consulta
 * `GET /pedidos/{id}/seguimiento` con lo que venga en la URL — construido
 * expresamente para el paso 4f, ver `docs/03-api.md`.
 */
@Component({
  selector: 'app-estado',
  imports: [DatePipe, NgOptimizedImage, TranslocoPipe, TsBoton, TsEsqueleto, TsPrecio],
  templateUrl: './estado.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstadoPage {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();
  protected readonly checkout = inject(CheckoutStore);

  protected readonly error = signal<string | null>(null);

  protected readonly criteriosSeguimiento = computed<CriteriosSeguimiento | null>(() => {
    if (this.checkout.pedido() !== null) {
      return null;
    }
    const parametros = this.route.snapshot.queryParamMap;
    const pedidoId = parametros.get('pedidoId');
    const correo = parametros.get('correo');
    return pedidoId && correo ? { pedidoId, correo } : null;
  });

  protected readonly consulta = usarSeguimientoPedido(() => this.criteriosSeguimiento());

  protected readonly pedido = computed<Pedido | null>(() => this.checkout.pedido() ?? this.consulta.data() ?? null);

  /**
   * Solo del seguimiento, nunca del store: el pedido que `CheckoutStore` guarda es el que acaba de
   * crearse en esta misma visita, y sobre uno recién creado no puede haber ningún retracto. Que
   * este bloque no aparezca ahí no es un olvido.
   */
  protected readonly retractos = computed<readonly RetractoPublico[]>(
    () => this.consulta.data()?.retractos ?? [],
  );

  protected etiquetaEstadoRetracto(estado: RetractoPublico['estado']): string {
    return this.traducir()('checkout.estado.retracto.estados.' + estado.toLowerCase());
  }

  protected readonly etiquetaEstado = computed(() => {
    const pedido = this.pedido();
    return pedido ? this.traducir()(CLAVE_ETIQUETA_ESTADO[pedido.estado]) : '';
  });

  protected readonly puedeReintentar = computed(() => {
    const pedido = this.pedido();
    return pedido !== null && puedeReintentarPago(pedido.estado);
  });

  protected readonly reintentando = signal(false);

  protected async reintentar(): Promise<void> {
    const pedido = this.pedido();
    if (!pedido) {
      return;
    }
    this.error.set(null);
    this.reintentando.set(true);
    try {
      const actualizado = await this.checkout.reintentarPago(pedido.id);
      if (esMetodoPagoWompi(actualizado.metodoPago)) {
        const intento = await this.checkout.crearIntentoPago(actualizado.id);
        const idioma = this.transloco.activeLang();
        const parametrosRetorno = new URLSearchParams({
          referencia: intento.referencia,
          pedidoId: actualizado.id,
          correo: actualizado.correo,
        });
        const urlRetorno = `${window.location.origin}/${idioma}/checkout/retorno-wompi?${parametrosRetorno.toString()}`;
        window.location.href = urlWebCheckoutWompi(intento, urlRetorno);
      }
    } catch {
      this.error.set(this.transloco.translate('checkout.estado.error_reintento'));
    } finally {
      this.reintentando.set(false);
    }
  }
}
