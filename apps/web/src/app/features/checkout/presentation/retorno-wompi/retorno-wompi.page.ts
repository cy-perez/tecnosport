import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { CheckoutStore } from '../../application/checkout.store';

/**
 * Adónde vuelve el navegador tras el Web Checkout de Wompi (paso 4d de
 * `docs/09-plan-de-arranque.md`). La SPA se recarga entera al volver de un
 * dominio externo — `CheckoutStore` no sobrevive ese viaje, así que todo lo
 * que hace falta viaja en la URL: `id` lo agrega Wompi mismo
 * (`docs.wompi.co`, verificado — el formato es
 * `?id=01-1531231271-19365`); `referencia`, `pedidoId` y `correo` los agregó
 * `ConfirmarPage` a la URL de retorno que le pasó a Wompi.
 *
 * **Lo que se ve aquí no decide nada** (`docs/11-pagos-y-envios.md`: "el
 * parámetro de retorno solo sirve para mostrar una pantalla"). Registrar el
 * id es best-effort — si falla, la conciliación programada es la red de
 * seguridad — y de todos modos se sigue a la pantalla de estado, que es la
 * que consulta la verdad.
 */
@Component({
  selector: 'app-retorno-wompi',
  imports: [RouterLink, TranslocoPipe, TsEsqueleto],
  templateUrl: './retorno-wompi.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RetornoWompiPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly checkout = inject(CheckoutStore);

  protected readonly sinDatos = signal(false);

  constructor() {
    const parametros = this.route.snapshot.queryParamMap;
    const idTransaccionWompi = parametros.get('id');
    const referencia = parametros.get('referencia');
    const pedidoId = parametros.get('pedidoId');
    const correo = parametros.get('correo');

    if (!idTransaccionWompi || !referencia || !pedidoId || !correo) {
      this.sinDatos.set(true);
      return;
    }

    void this.procesarRetorno(referencia, idTransaccionWompi, pedidoId, correo);
  }

  private async procesarRetorno(
    referencia: string,
    idTransaccionWompi: string,
    pedidoId: string,
    correo: string,
  ): Promise<void> {
    try {
      await this.checkout.registrarIdTransaccionWompi(referencia, idTransaccionWompi);
    } catch {
      // best-effort a propósito, ver el javadoc de la clase.
    }
    void this.router.navigate(['../estado'], { relativeTo: this.route, queryParams: { pedidoId, correo } });
  }
}
