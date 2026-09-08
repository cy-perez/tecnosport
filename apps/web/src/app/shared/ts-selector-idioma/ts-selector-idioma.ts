import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { urlEnOtroIdioma } from '../../core/idioma/idioma.servicio';
import { OpcionSelect, TsSelect } from '../ui/select/ts-select';

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
 *
 * Sin `FormControl`: este componente vive en el encabezado, o sea en todas las
 * pantallas, y usar un formulario para un `<select>` de dos opciones metía
 * `@angular/forms` (38,6 kB) en el paquete inicial de todo el sitio. El valor
 * se ata a la señal del idioma activo y la elección se atiende con `cambio`,
 * que es lo que el formulario hacía por debajo.
 */
@Component({
  selector: 'ts-selector-idioma',
  imports: [TranslocoPipe, TsSelect],
  templateUrl: './ts-selector-idioma.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // `w-fit` y no el ancho disponible: `ts-select` le da a su `<select>`
  // un `inline-size: 100%`, que aquí se resuelve contra el contenido. En
  // el encabezado el control no debe estirarse.
  host: { class: 'block w-fit' },
})
export class TsSelectorIdioma {
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);

  /** El código en mayúscula se lee igual en los dos idiomas: no se traduce. */
  protected readonly opciones: readonly OpcionSelect[] = IDIOMAS.map((idioma) => ({
    valor: idioma,
    etiqueta: idioma.toUpperCase(),
  }));

  // El idioma activo no siempre cambia desde aquí: un enlace con otro prefijo
  // o el botón de atrás del navegador también lo mueven. El select sigue a la
  // URL, no al revés — de ahí que el valor se lea de la señal y no se guarde.
  protected readonly idiomaActivo = computed(() => this.transloco.activeLang());

  protected navegarA(idioma: string): void {
    void this.router.navigateByUrl(urlEnOtroIdioma(this.router.url, idioma));
  }
}
