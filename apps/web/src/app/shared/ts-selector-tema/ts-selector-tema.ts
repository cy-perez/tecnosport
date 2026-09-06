import { afterNextRender, ChangeDetectionStrategy, Component, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Translation, TranslocoPipe, translateObjectSignal } from '@jsverse/transloco';
import { OpcionSelect, TsSelect } from '../ts-select/ts-select';

const TEMAS = ['claro', 'oscuro', 'sistema'] as const;

export type Tema = (typeof TEMAS)[number];

/** Un año: la preferencia de tema no caduca sola (docs/04-ui-marca.md). */
const UN_ANIO_EN_SEGUNDOS = 31536000;

function esTema(valor: unknown): valor is Tema {
  return typeof valor === 'string' && (TEMAS as readonly string[]).includes(valor);
}

function leerCookie(nombre: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((fragmento) => fragmento.startsWith(`${nombre}=`))
    ?.split('=')[1];
}

// `Translation` indexa a `any`: se estrecha a string en vez de confiar.
function etiquetaDe(diccionario: Translation, clave: string): string {
  const valor = diccionario[clave];
  return typeof valor === 'string' ? valor : '';
}

/**
 * Tres opciones: claro, oscuro y seguir al sistema (docs/04-ui-marca.md). La
 * elección se persiste en una cookie que `server.ts` lee para resolver claro y
 * oscuro explícitos durante el SSR; "sistema" no se puede resolver en el
 * servidor y lo termina el script en línea de `index.html` antes del primer
 * pintado, para que no haya destello.
 *
 * Por eso el control arranca en "sistema" y solo se corrige después de
 * renderizar: la cookie vive en `document`, que en el servidor no existe.
 */
@Component({
  selector: 'ts-selector-tema',
  imports: [ReactiveFormsModule, TranslocoPipe, TsSelect],
  templateUrl: './ts-selector-tema.html',
  styleUrl: './ts-selector-tema.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsSelectorTema {
  // `translateObjectSignal`, no `transloco.translate()` dentro del computed:
  // ese no lee ninguna señal, así que la lista se quedaría en el idioma con el
  // que se creó el componente (apps/web/CLAUDE.md).
  private readonly etiquetas = translateObjectSignal('tema');

  protected readonly opciones = computed<readonly OpcionSelect[]>(() => {
    const etiquetas = this.etiquetas();
    return TEMAS.map((tema) => ({ valor: tema, etiqueta: etiquetaDe(etiquetas, tema) }));
  });

  protected readonly control = new FormControl<Tema>('sistema', { nonNullable: true });

  constructor() {
    afterNextRender(() => {
      const cookie = leerCookie('ts-tema');
      if (esTema(cookie)) {
        // Solo refleja lo que el servidor ya aplicó: escribir la cookie otra
        // vez y volver a fijar `data-tema` no cambiaría nada.
        this.control.setValue(cookie, { emitEvent: false });
      }
    });

    this.control.valueChanges.pipe(takeUntilDestroyed()).subscribe((tema) => this.aplicar(tema));
  }

  private aplicar(tema: Tema): void {
    document.cookie = `ts-tema=${tema}; path=/; max-age=${UN_ANIO_EN_SEGUNDOS}; samesite=lax`;
    const visual =
      tema === 'sistema'
        ? window.matchMedia('(prefers-color-scheme: dark)').matches
          ? 'oscuro'
          : 'claro'
        : tema;
    document.documentElement.setAttribute('data-tema', visual);
  }
}
