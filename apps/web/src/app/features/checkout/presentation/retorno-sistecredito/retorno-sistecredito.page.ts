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
 * `pedidoId` y `correo` **los pone el backend en la propia URL de respuesta**, como segmentos de
 * ruta. Hacen falta: la pantalla de estado los exige para consultar el seguimiento y no tiene
 * forma de pedirlos — sin ellos enseña "no encontramos este pedido", que es lo que veía **todo**
 * comprador que pagara con Sistecrédito antes de este arreglo, justo después de haber pagado.
 *
 * <p>La pasarela no los devuelve —solo concatena los suyos— y tampoco sobreviven en memoria: la
 * SPA se recarga entera al volver de un dominio externo, así que `CheckoutStore` viene vacío.
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
    const parametros = this.route.snapshot.paramMap;
    const pedidoId = parametros.get('pedidoId');
    const correo = parametros.get('correo');

    if (!pedidoId || !correo) {
      this.sinDatos.set(true);
      return;
    }

    // A la pantalla de estado, que es la que consulta la verdad. Lo que trae la pasarela en la
    // URL (`paymentRef`, `transactionId`, `orderId`) no se usa para nada: no decide un pago, y el
    // backend ya guardó el id de la transacción al crear el intento.
    void this.router.navigate(['../../../estado'], {
      relativeTo: this.route,
      queryParams: { pedidoId, correo },
    });
  }
}
