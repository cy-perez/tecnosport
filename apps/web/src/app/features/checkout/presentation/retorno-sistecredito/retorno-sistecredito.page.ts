import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';

/**
 * Adónde vuelve el navegador tras la experiencia de pago de Sistecrédito (`adr/0048`).
 *
 * La pasarela concatena a la URL de respuesta tres parámetros propios (guía `G-ALI-12`):
 * `paymentRef` (su `_id`, el mismo que devolvió al crear), `transactionId` (el identificador del
 * medio de pago) y `orderId` (nuestra referencia, la que mandamos como `invoice`).
 *
 * **Aquí no se registra nada y no se decide nada**, a diferencia del retorno de Wompi. Allá hacía
 * falta reportar el id de transacción porque llega por primera vez en esta URL; aquí el backend ya
 * lo guardó al crear el intento, así que esta pantalla solo traduce esos parámetros en un viaje a
 * la pantalla de estado. Lo que el navegador trae no aprueba un pago: eso lo deciden la
 * notificación contrastada y la conciliación.
 *
 * `pedidoId` y `correo` no los devuelve la pasarela —solo agrega los suyos— y tampoco sobreviven
 * en memoria: la SPA se recarga entera al volver de un dominio externo, así que `CheckoutStore`
 * viene vacío. Por eso se sigue a la pantalla de estado con lo que haya; esa pantalla sabe pedir
 * lo que le falte en vez de dejar al comprador sin saber qué pasó con su compra.
 */
@Component({
  selector: 'app-retorno-sistecredito',
  imports: [RouterLink, TranslocoPipe, TsEsqueleto],
  templateUrl: './retorno-sistecredito.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RetornoSistecreditoPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly sinDatos = signal(false);

  constructor() {
    const parametros = this.route.snapshot.queryParamMap;
    const referencia = parametros.get('orderId');
    const pedidoId = parametros.get('pedidoId');
    const correo = parametros.get('correo');

    if (!referencia && !pedidoId) {
      this.sinDatos.set(true);
      return;
    }

    // Con pedidoId y correo, a la pantalla de estado, que consulta la verdad. Sin ellos —el caso
    // normal, porque la pasarela solo devuelve lo suyo— se va igual: esa pantalla sabe pedir los
    // datos que le faltan en vez de dejar al comprador sin saber qué pasó con su compra.
    void this.router.navigate(['../estado'], {
      relativeTo: this.route,
      queryParams: pedidoId && correo ? { pedidoId, correo } : {},
    });
  }
}
