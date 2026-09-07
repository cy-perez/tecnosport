import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsBoton } from '../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../shared/ui/campo/ts-campo';
import { SesionStore } from '../../../core/autenticacion/sesion.store';

/**
 * Solo para `ADMIN` — el login de `CLIENTE` es `features/cuenta/`, todavía
 * sin construir. Un correo/clave válidos pero de un `CLIENTE` cierran la
 * sesión que acaban de abrir en vez de dejarla colgada: esta pantalla no
 * es el lugar para esa cuenta.
 */
@Component({
  selector: 'app-iniciar-sesion-admin',
  imports: [TsPaginaFormulario, ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo],
  templateUrl: './iniciar-sesion-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IniciarSesionAdminPage {
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  protected readonly sesionStore = inject(SesionStore);

  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  protected readonly form = new FormGroup({
    correo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    clave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly estadoFormulario = toSignal(this.form.statusChanges, {
    initialValue: this.form.status,
  });
  protected readonly formularioInvalido = computed(() => this.estadoFormulario() === 'INVALID');

  /**
   * A dónde ir después de iniciar sesión: lo que puso `adminGuard` al mandar
   * aquí, o el panel si se llegó por la puerta principal.
   *
   * Solo se acepta una ruta **relativa de este sitio**, dentro de `/admin`. Un
   * `destino` es un parámetro de la URL, o sea entrada del usuario: sin este
   * filtro, un enlace preparado con `?destino=https://otro-sitio` convertiría
   * este formulario en un salto a un sitio ajeno, con la credencial recién
   * escrita y la confianza de venir del dominio correcto.
   */
  private destinoTrasIniciar(idioma: string): string {
    const panel = `/${idioma}/admin/panel`;
    const destino = this.ruta.snapshot.queryParamMap.get('destino');
    if (destino === null) {
      return panel;
    }
    const esRutaDeEsteSitio = destino.startsWith('/') && !destino.startsWith('//');
    return esRutaDeEsteSitio && destino.includes('/admin/') ? destino : panel;
  }

  protected async enviar(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.enviando.set(true);

    try {
      const sesion = await this.sesionStore.iniciarSesion(
        this.form.controls.correo.value,
        this.form.controls.clave.value,
      );
      if (sesion.rol !== 'ADMIN') {
        await this.sesionStore.cerrarSesion();
        this.error.set(this.transloco.translate('admin.iniciarSesion.no_es_admin'));
        return;
      }
      const idioma = this.transloco.activeLang();
      void this.router.navigateByUrl(this.destinoTrasIniciar(idioma));
    } catch {
      this.error.set(this.transloco.translate('admin.iniciarSesion.error'));
    } finally {
      this.enviando.set(false);
    }
  }
}
