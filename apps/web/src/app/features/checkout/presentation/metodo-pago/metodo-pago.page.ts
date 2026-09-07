import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { OpcionMetodoPago, TsSelectorMetodoPago } from '../selector-metodo-pago/ts-selector-metodo-pago';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { usarMetodosDePagoDisponibles } from '../../application/metodos-de-pago-disponibles.consulta';
import { CheckoutStore } from '../../application/checkout.store';
import { MetodosDePagoDisponiblesComando } from '../../domain/pedido.comandos';
import { MetodoPago } from '../../domain/pedido.model';

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
 * Segundo paso del checkout (Fase 3, paso 4b de `docs/09-plan-de-arranque.md`).
 * Sin dirección/correo guardados de la página de resumen (`CheckoutStore.datosEntrega`)
 * no hay nada que consultar — se vuelve ahí. `POST
 * /pedidos/metodos-de-pago-disponibles` es la misma consulta que el
 * servidor vuelve a aplicar al crear el pedido (`MetodosDePagoDisponibles`
 * en el backend): lo que se ve aquí no es una promesa, es lo que de verdad
 * va a aceptar el siguiente paso.
 */
@Component({
  selector: 'app-metodo-pago',
  imports: [RouterLink, TranslocoPipe, TsBoton, TsEsqueleto, TsSelectorMetodoPago],
  templateUrl: './metodo-pago.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetodoPagoPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();
  protected readonly carrito = inject(CarritoStore);
  protected readonly checkout = inject(CheckoutStore);

  protected readonly comando = computed<MetodosDePagoDisponiblesComando | null>(() => {
    const datos = this.checkout.datosEntrega();
    const datosCarrito = this.carrito.consulta.data();
    if (!datos || !datosCarrito || datosCarrito.lineas.length === 0) {
      return null;
    }
    return {
      correo: datos.correo,
      lineas: datosCarrito.lineas.map((linea) => ({ varianteId: linea.varianteId, cantidad: linea.cantidad })),
      tipoEntrega: datos.tipoEntrega,
      direccion: datos.direccion,
    };
  });

  protected readonly consulta = usarMetodosDePagoDisponibles(() => this.comando());

  protected readonly opciones = computed<OpcionMetodoPago[]>(() =>
    (this.consulta.data() ?? []).map((metodo) => ({
      valor: metodo,
      etiqueta: this.traducir()(CLAVE_ETIQUETA[metodo]),
    })),
  );

  constructor() {
    // Sin datos de entrega, o con el carrito vacío (se vació en otra
    // pestaña, por ejemplo): no hay nada que consultar. De vuelta al
    // resumen, que sabe mostrar cada uno de esos dos casos.
    effect(() => {
      const datosCarrito = this.carrito.consulta.data();
      const sinDatosEntrega = this.checkout.datosEntrega() === null;
      const carritoVacio = !!datosCarrito && datosCarrito.lineas.length === 0;
      if (sinDatosEntrega || carritoVacio) {
        void this.router.navigate(['../resumen'], { relativeTo: this.route });
      }
    });
  }

  protected elegir(metodo: MetodoPago): void {
    this.checkout.elegirMetodoPago(metodo);
  }

  protected continuar(): void {
    if (!this.checkout.metodoPago()) {
      return;
    }
    // Creación del pedido y redirección a Wompi es el paso 4c, todavía sin construir.
    void this.router.navigate(['../confirmar'], { relativeTo: this.route });
  }
}
