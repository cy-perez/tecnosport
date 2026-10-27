import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { formatearPrecio } from '../../../../../shared/ts-precio/formato-precio';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { usarMigasAdmin } from '../../../migas-admin';
import {
  usarPreciosPorRecalcular,
  usarRecalcularPrecios,
} from '../../application/recalcular-precios.consulta';
import { PrecioPorRecalcular } from '../../domain/producto-admin.model';

/**
 * Los precios publicados que ya no corresponden a la tarifa de Wompi, y el botón para recalcularlos
 * (ADR-0095). Un cambio de tarifa no mueve ningún precio anunciado por su cuenta: esta pantalla
 * enseña cuáles se moverían y a cuánto, y solo los cambia cuando alguien lo confirma.
 *
 * <p>Cuelga de productos, junto a existencias y medidas, porque es otra lista del catálogo por
 * variante. La confirmación es la misma caja en línea que la limpieza de no publicados: la acción
 * toca muchas variantes de una vez, y se pregunta antes.
 */
@Component({
  selector: 'app-precios-admin',
  imports: [TranslocoPipe, TsBoton, TsEsqueleto, TsMigas],
  templateUrl: './precios-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PreciosAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'admin.productos.precios.titulo' },
  ]);

  private readonly transloco = inject(TranslocoService);

  protected readonly consulta = usarPreciosPorRecalcular();
  private readonly recalcular = usarRecalcularPrecios();

  protected readonly variantes = computed<readonly PrecioPorRecalcular[]>(
    () => this.consulta.data() ?? [],
  );
  protected readonly total = computed(() => this.variantes().length);

  protected readonly confirmando = signal(false);
  protected readonly recalculando = computed(() => this.recalcular.isPending());
  /** Una marca propia y no `recalculando()`: `isPending` no cambia en el mismo tic del `mutate`. */
  private enVuelo = false;
  protected readonly error = signal<string | null>(null);
  /**
   * El texto de la región viva: «Se recalcularon 12 precios publicados», y cuántas se omitieron si
   * alguna cambió entre la revisión y la confirmación.
   */
  protected readonly aviso = signal<string | null>(null);

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly caja = viewChild<ElementRef<HTMLElement>>('caja');
  private readonly boton = viewChild('boton', { read: ElementRef });
  private readonly avisoRef = viewChild<ElementRef<HTMLElement>>('avisoRef');

  protected precio(valor: number): string {
    return formatearPrecio(valor, 'COP', this.transloco.activeLang());
  }

  protected preguntar(): void {
    this.error.set(null);
    this.aviso.set(null);
    this.confirmando.set(true);
    // Sin esto, tabular desde el botón salta directo a «Sí, recalcular» sin pasar por lo que
    // implica.
    this.enfocarDespuesDePintar(() => this.caja()?.nativeElement);
  }

  protected cancelar(): void {
    this.confirmando.set(false);
    this.error.set(null);
    this.enfocarDespuesDePintar(() => this.boton()?.nativeElement.querySelector('button'));
  }

  /** La caja desaparece con el botón dentro, y la lista con ella: el foco va al resultado. */
  protected confirmar(): void {
    if (this.enVuelo) {
      return;
    }
    this.enVuelo = true;
    this.error.set(null);
    // La lista que la persona está viendo: el servidor graba esas filas y no otras.
    this.recalcular.mutate(this.variantes(), {
      onSettled: () => (this.enVuelo = false),
      onSuccess: ({ recalculadas, omitidas }) => {
        this.confirmando.set(false);
        this.aviso.set(
          omitidas > 0
            ? this.transloco.translate('admin.productos.precios.hechoConOmitidas', {
                recalculadas,
                omitidas,
              })
            : this.transloco.translate('admin.productos.precios.hecho', { recalculadas }),
        );
        this.enfocarDespuesDePintar(() => this.avisoRef()?.nativeElement);
      },
      onError: (error: unknown) =>
        this.error.set(mensajeDeError(error, this.transloco, 'admin.productos.precios.error')),
    });
  }
}
