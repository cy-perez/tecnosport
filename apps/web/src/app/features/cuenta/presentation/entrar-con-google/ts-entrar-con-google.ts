import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  input,
  output,
  PLATFORM_ID,
  signal,
  viewChild,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarConfiguracionGoogle } from '../../application/configuracion-google.consulta';
import { BOTON_GOOGLE } from '../../domain/boton-google.puerto';

/**
 * "Iniciar sesión con Google" o "Registrarse con Google" (ADR-0074), con el botón oficial. No hace
 * nada con la credencial: la entrega a la pantalla, que es la que sabe si hace falta la
 * autorización de datos y a dónde ir después.
 *
 * Si el ambiente no tiene cliente de Google, o el script no carga, no se pinta nada: el formulario
 * de siempre sigue ahí, y un botón que no funciona es peor que ninguno.
 */
@Component({
  selector: 'ts-entrar-con-google',
  imports: [TranslocoPipe],
  template: `
    @if (configuracion.data()?.habilitado && !fallo()) {
      <div class="flex flex-col gap-12">
        <p class="m-0 flex items-center gap-12 text-sm text-ts-texto-suave" aria-hidden="true">
          <span class="h-[var(--trazo-fino)] flex-1 bg-ts-borde"></span>
          {{ 'cuenta.google.separador' | transloco }}
          <span class="h-[var(--trazo-fino)] flex-1 bg-ts-borde"></span>
        </p>
        <!-- scheme-light: el iframe de Google hereda el color-scheme: dark del sitio y su documento
             es claro, y cuando los dos no coinciden Chrome le pinta al iframe un fondo opaco: era el
             marco blanco alrededor del botón en tema oscuro. El color del botón no cambia: lo sigue
             decidiendo la opción oscuro que se le pasa a Google. -->
        <div #contenedor class="min-h-tactil w-full scheme-light"></div>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsEntrarConGoogle {
  private readonly botonGoogle = inject(BOTON_GOOGLE);
  private readonly transloco = inject(TranslocoService);
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  readonly modo = input.required<'iniciar' | 'registrar'>();
  readonly credencial = output<string>();

  protected readonly configuracion = usarConfiguracionGoogle();
  protected readonly fallo = signal(false);
  private readonly contenedor = viewChild<ElementRef<HTMLElement>>('contenedor');

  constructor() {
    effect(() => {
      const configuracion = this.configuracion.data();
      const contenedor = this.contenedor()?.nativeElement;
      if (!this.esNavegador || !configuracion?.habilitado || !contenedor) {
        return;
      }
      this.botonGoogle
        .pintar(contenedor, {
          configuracion,
          modo: this.modo(),
          idioma: this.transloco.getActiveLang(),
          oscuro: contenedor.ownerDocument.documentElement.getAttribute('data-tema') === 'oscuro',
          alObtenerCredencial: (credencial) => this.credencial.emit(credencial),
        })
        .catch(() => this.fallo.set(true));
    });
  }
}
