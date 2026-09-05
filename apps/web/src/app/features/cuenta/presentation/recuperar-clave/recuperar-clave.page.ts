import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { REPOSITORIO_CUENTA } from '../../domain/repositorio-cuenta.puerto';
import { TsBoton } from '../../../../shared/ts-boton/ts-boton';
import { TsCampo } from '../../../../shared/ts-campo/ts-campo';

/**
 * Un solo campo (correo). El mensaje de éxito es siempre el mismo, exista o no una cuenta con ese
 * correo (docs/08-seguridad-legal.md, OWASP) — el propio {@code RepositorioCuenta.solicitarRecuperacion}
 * nunca lanza por esa distinción, así que esta página no tiene nada que ocultar a propósito: solo
 * refleja lo que el backend ya decide.
 */
@Component({
  selector: 'app-recuperar-clave',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo],
  templateUrl: './recuperar-clave.page.html',
  styleUrl: './recuperar-clave.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecuperarClavePage {
  private readonly repositorio = inject(REPOSITORIO_CUENTA);
  protected readonly transloco = inject(TranslocoService);

  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);

  protected readonly form = new FormGroup({
    correo: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });

  private readonly estadoFormulario = toSignal(this.form.statusChanges, { initialValue: this.form.status });
  protected readonly formularioInvalido = computed(() => this.estadoFormulario() === 'INVALID');

  protected async enviar(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.enviando.set(true);

    try {
      await this.repositorio.solicitarRecuperacion(this.form.controls.correo.value);
      this.enviado.set(true);
    } catch {
      this.error.set(this.transloco.translate('cuenta.recuperarClave.error_generico'));
    } finally {
      this.enviando.set(false);
    }
  }
}
