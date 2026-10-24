import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarIdiomaActivo } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { usarEliminarBorradorTecnologia } from '../../application/borrar-borradores-tecnologia.mutacion';
import { BorradorTecnologia } from '../../domain/borrador-tecnologia.model';

/**
 * La clave del estado de navegación con que la bandeja anuncia el borrador que se acaba de borrar:
 * la página de origen ya no existe para decirlo.
 */
export const ESTADO_BORRADOR_ELIMINADO = 'borradorTecnologiaEliminado';

/**
 * «Eliminar el borrador» desde su revisión (24/10/2026). Solo en revisión o rechazado: un aprobado
 * es la constancia del producto publicado, y quien lo llama no lo monta. Al terminar vuelve a la
 * bandeja, que dice cuál se borró.
 */
@Component({
  selector: 'ts-eliminar-borrador-tecnologia',
  imports: [TranslocoPipe, TsBoton],
  templateUrl: './ts-eliminar-borrador-tecnologia.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsEliminarBorradorTecnologia {
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly idioma = usarIdiomaActivo();
  private readonly enfocar = usarFoco();
  private readonly eliminar = usarEliminarBorradorTecnologia();

  readonly borrador = input.required<BorradorTecnologia>();

  protected readonly confirmando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly eliminando = computed(() => this.eliminar.isPending());

  private readonly caja = viewChild<ElementRef<HTMLElement>>('caja');
  private readonly boton = viewChild('boton', { read: ElementRef });

  protected preguntar(): void {
    this.error.set(null);
    this.confirmando.set(true);
    this.enfocar(() => this.caja()?.nativeElement);
  }

  protected cancelar(): void {
    this.confirmando.set(false);
    this.error.set(null);
    this.enfocar(() => this.boton()?.nativeElement.querySelector('button'));
  }

  protected confirmar(): void {
    if (this.eliminando()) {
      return;
    }
    const borrador = this.borrador();
    this.error.set(null);
    this.eliminar.mutate(borrador.id, {
      onSuccess: () =>
        void this.router.navigate(['/', this.idioma(), 'admin', 'tecnologia'], {
          state: { [ESTADO_BORRADOR_ELIMINADO]: borrador.titulo },
        }),
      onError: (error: unknown) =>
        this.error.set(mensajeDeError(error, this.transloco, 'admin.tecnologia.eliminar.error')),
    });
  }
}
