import { afterNextRender, ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Translation, TranslocoPipe, translateObjectSignal } from '@jsverse/transloco';
import { ServicioTema, Tema } from '../../core/tema/tema.servicio';
import { OpcionSelect, TsSelect } from '../ui/select/ts-select';

// `Translation` indexa a `any`: se estrecha a string en vez de confiar.
function etiquetaDe(diccionario: Translation, clave: string): string {
  const valor = diccionario[clave];
  return typeof valor === 'string' ? valor : '';
}

/**
 * Solo la interfaz del selector. La cookie, la resolución de "sistema" y la
 * escritura de `data-tema` son de `ServicioTema` (`core/tema/`): un componente
 * de `shared/` no decide la política de persistencia del sitio.
 *
 * El control arranca en "sistema" y solo se corrige después de renderizar,
 * porque la cookie vive en `document`, que en el servidor no existe.
 */
@Component({
  selector: 'ts-selector-tema',
  imports: [ReactiveFormsModule, TranslocoPipe, TsSelect],
  templateUrl: './ts-selector-tema.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // `w-fit` y no el ancho disponible: `ts-select` le da a su `<select>`
  // un `inline-size: 100%`, que aquí se resuelve contra el contenido. En
  // el encabezado el control no debe estirarse.
  host: { class: 'block w-fit' },
})
export class TsSelectorTema {
  private readonly tema = inject(ServicioTema);

  // `translateObjectSignal`, no `transloco.translate()` dentro del computed:
  // ese no lee ninguna señal, así que la lista se quedaría en el idioma con el
  // que se creó el componente (apps/web/CLAUDE.md).
  private readonly etiquetas = translateObjectSignal('tema');

  protected readonly opciones = computed<readonly OpcionSelect[]>(() => {
    const etiquetas = this.etiquetas();
    return this.tema.opciones.map((tema) => ({
      valor: tema,
      etiqueta: etiquetaDe(etiquetas, tema),
    }));
  });

  protected readonly control = new FormControl<Tema>('sistema', { nonNullable: true });

  constructor() {
    afterNextRender(() => {
      // Solo refleja lo que el servidor ya aplicó.
      this.control.setValue(this.tema.sincronizarConCookie(), { emitEvent: false });
    });

    this.control.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((tema) => this.tema.elegir(tema));
  }
}
