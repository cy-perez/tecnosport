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
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import {
  usarBorrarBorradoresTecnologiaSinAprobar,
  usarContarBorradoresTecnologiaSinAprobar,
} from '../../application/borrar-borradores-tecnologia.mutacion';

/**
 * «Borrar los no aprobados» de la bandeja de tecnología (24/10/2026): la forma de rehacer una tanda
 * desde cero antes de correr otra vez la skill de fichas. Misma interacción que la limpieza de la
 * bandeja de prendas: confirmación en línea con lo que implica, tandas hasta terminar y el foco en
 * el aviso cuando la caja desaparece. Los aprobados se quedan.
 */
@Component({
  selector: 'ts-limpiar-borradores-tecnologia',
  imports: [TranslocoPipe, TsBoton],
  templateUrl: './ts-limpiar-borradores-tecnologia.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsLimpiarBorradoresTecnologia {
  private readonly transloco = inject(TranslocoService);
  private readonly enfocar = usarFoco();
  private readonly cuenta = usarContarBorradoresTecnologiaSinAprobar();
  private readonly borrar = usarBorrarBorradoresTecnologiaSinAprobar();
  private enVuelo = false;

  protected readonly cantidad = computed(() => this.cuenta.data() ?? 0);
  protected readonly borrando = computed(() => this.borrar.isPending());
  protected readonly confirmando = signal(false);
  /** Lo que lleva borrado, para el texto del botón ocupado. */
  protected readonly borradosHastaAhora = signal(0);
  protected readonly aviso = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private readonly caja = viewChild<ElementRef<HTMLElement>>('caja');
  private readonly boton = viewChild('boton', { read: ElementRef });
  private readonly avisoRef = viewChild<ElementRef<HTMLElement>>('avisoRef');

  protected preguntar(): void {
    this.error.set(null);
    this.aviso.set(null);
    this.confirmando.set(true);
    // Sin esto, tabular desde el botón salta a «Sí, borrar todos» sin pasar por la advertencia.
    this.enfocar(() => this.caja()?.nativeElement);
  }

  protected cancelar(): void {
    this.confirmando.set(false);
    this.error.set(null);
    this.enfocar(() => this.boton()?.nativeElement.querySelector('button'));
  }

  protected confirmar(): void {
    // Una marca propia: `isPending` no cambia en el mismo tic del `mutate`.
    if (this.enVuelo) {
      return;
    }
    this.enVuelo = true;
    this.error.set(null);
    this.borradosHastaAhora.set(0);
    this.borrar.mutate(
      { alAvanzar: (borrados) => this.borradosHastaAhora.set(borrados) },
      {
        onSettled: () => (this.enVuelo = false),
        onSuccess: (borrados) => {
          if (borrados === 0) {
            this.error.set(this.transloco.translate('admin.tecnologia.limpiar.ninguno'));
            return;
          }
          this.confirmando.set(false);
          this.aviso.set(this.transloco.translate('admin.tecnologia.limpiar.hecho', { borrados }));
          this.enfocar(() => this.avisoRef()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.error.set(mensajeDeError(error, this.transloco, 'admin.tecnologia.limpiar.error')),
      },
    );
  }
}
