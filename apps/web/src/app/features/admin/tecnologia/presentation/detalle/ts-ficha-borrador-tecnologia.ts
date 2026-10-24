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
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsAreaTexto } from '../../../../../shared/ui/area-texto/ts-area-texto';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsDescripcion } from '../../../../../shared/ui/descripcion/ts-descripcion';
import { usarEditarFichaBorradorTecnologia } from '../../application/editar-borrador-tecnologia.mutacion';
import { BorradorTecnologia } from '../../domain/borrador-tecnologia.model';

/**
 * «Ficha que preparó la skill»: la vista previa tal como la verá el cliente y, mientras el borrador
 * está en revisión, la edición a mano (23/10/2026).
 *
 * La edición se guarda aparte de la de la skill: una lista nueva no la pisa, y el aviso de
 * `fichaNuevaDeLaSkill` dice que trajo otra. «Volver a la de la skill» no borra nada al pulsarlo:
 * pone su texto en el área, y se guarda como cualquier otra edición —guardar un texto igual al de
 * la skill es volver a ella—.
 */
@Component({
  selector: 'ts-ficha-borrador-tecnologia',
  imports: [ReactiveFormsModule, TranslocoPipe, TsAreaTexto, TsBoton, TsDescripcion],
  templateUrl: './ts-ficha-borrador-tecnologia.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsFichaBorradorTecnologia {
  private readonly transloco = inject(TranslocoService);
  private readonly editar = usarEditarFichaBorradorTecnologia();
  private readonly enfocar = usarFoco();

  readonly borrador = input.required<BorradorTecnologia>();
  /** Falso en uno aprobado o rechazado: la ficha se ve, no se edita. */
  readonly editable = input(false);

  protected readonly abierta = signal(false);
  protected readonly editando = signal(false);
  protected readonly texto = new FormControl('', { nonNullable: true });
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly guardando = computed(() => this.editar.isPending());

  private readonly botonEditar = viewChild('botonEditar', { read: ElementRef<HTMLElement> });
  private readonly areaEdicion = viewChild<ElementRef<HTMLElement>>('areaEdicion');
  private readonly avisoRef = viewChild<ElementRef<HTMLElement>>('avisoRef');

  protected alternar(evento: Event): void {
    this.abierta.set((evento.target as HTMLDetailsElement).open);
  }

  protected empezar(): void {
    this.texto.setValue(this.borrador().descripcion);
    this.error.set(null);
    this.aviso.set(null);
    this.editando.set(true);
    this.enfocar(() => this.areaEdicion()?.nativeElement.querySelector('textarea'));
  }

  protected volverALaDeLaSkill(): void {
    this.texto.setValue(this.borrador().descripcionDeLaSkill);
  }

  protected cancelar(): void {
    this.editando.set(false);
    this.error.set(null);
    this.enfocar(() => this.botonEditar()?.nativeElement.querySelector('button'));
  }

  protected guardar(): void {
    if (this.guardando()) {
      return;
    }
    const escrito = this.texto.value.trim();
    if (!escrito) {
      this.texto.markAsTouched();
      this.error.set(this.transloco.translate('admin.tecnologia.ficha.vacia'));
      return;
    }
    const borrador = this.borrador();
    // Igual a la de la skill no es una edición: se vuelve a ella, y el aviso de ficha nueva se apaga.
    const descripcion = escrito === borrador.descripcionDeLaSkill.trim() ? null : escrito;
    this.error.set(null);
    this.editar.mutate(
      { id: borrador.id, descripcion },
      {
        onSuccess: () => {
          this.editando.set(false);
          this.aviso.set(
            descripcion === null
              ? 'admin.tecnologia.ficha.vueltaALaSkill'
              : 'admin.tecnologia.ficha.guardada',
          );
          this.enfocar(() => this.avisoRef()?.nativeElement);
        },
        onError: (error) =>
          this.error.set(
            mensajeDeError(error, this.transloco, 'admin.tecnologia.ficha.errorGuardar'),
          ),
      },
    );
  }
}
