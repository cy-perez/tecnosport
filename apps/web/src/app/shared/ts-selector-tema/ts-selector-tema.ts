import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
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
 *
 * Sin `FormControl`: el selector vive en el encabezado, o sea en todas las
 * pantallas, y usar un formulario para un `<select>` de tres opciones metía
 * `@angular/forms` (38,6 kB) en el paquete inicial de todo el sitio.
 */
@Component({
  selector: 'ts-selector-tema',
  imports: [TranslocoPipe, TsSelect],
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

  protected readonly elegido = signal<Tema>('sistema');

  constructor() {
    afterNextRender(() => {
      // Solo refleja lo que el servidor ya aplicó.
      this.elegido.set(this.tema.sincronizarConCookie());
    });
  }

  // El valor llega como `string` porque eso es lo que emite un `<select>`; se estrecha contra la
  // lista real de temas en vez de castear, que es lo que hacía `FormControl<Tema>` por debajo.
  protected elegir(valor: string): void {
    const tema = this.tema.opciones.find((opcion) => opcion === valor);
    if (tema) {
      this.elegido.set(tema);
      this.tema.elegir(tema);
    }
  }
}
