import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { urlEnOtroIdioma } from '../../core/idioma/idioma.servicio';
import { OpcionSelect, TsSelect } from '../ts-select/ts-select';

const IDIOMAS = ['es', 'en'] as const;

/**
 * Cambiar de idioma es navegar, no guardar una preferencia: el idioma vive en
 * el primer segmento de la URL (`docs/05-i18n.md`), así que el selector lleva a
 * la misma página con el otro prefijo — nunca a la portada.
 *
 * Se apoya en `ts-select` en vez de un `<select>` propio: ahí ya está resuelto
 * el `<label>` real, el anillo de foco, el objetivo táctil de 44 px y el
 * `min-inline-size: 0` que evita que el texto de la opción más larga ensanche
 * la columna.
 */
@Component({
  selector: 'ts-selector-idioma',
  imports: [ReactiveFormsModule, TranslocoPipe, TsSelect],
  templateUrl: './ts-selector-idioma.html',
  styleUrl: './ts-selector-idioma.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsSelectorIdioma {
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);

  /** El código en mayúscula se lee igual en los dos idiomas: no se traduce. */
  protected readonly opciones: readonly OpcionSelect[] = IDIOMAS.map((idioma) => ({
    valor: idioma,
    etiqueta: idioma.toUpperCase(),
  }));

  protected readonly control = new FormControl(this.transloco.getActiveLang(), { nonNullable: true });

  constructor() {
    // El idioma activo no siempre cambia desde aquí: un enlace con otro prefijo
    // o el botón de atrás del navegador también lo mueven. El select sigue a la
    // URL, no al revés.
    effect(() => {
      const activo = this.transloco.activeLang();
      if (this.control.value !== activo) {
        this.control.setValue(activo, { emitEvent: false });
      }
    });

    this.control.valueChanges.pipe(takeUntilDestroyed()).subscribe((idioma) => {
      void this.router.navigateByUrl(urlEnOtroIdioma(this.router.url, idioma));
    });
  }
}
