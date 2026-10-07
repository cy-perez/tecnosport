import { NgOptimizedImage } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  output,
} from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { descriptoresDe, Imagen, parametrosDe } from '../../domain/producto.model';
import { TAMANOS_GALERIA, TAMANOS_MINIATURA } from '../../../../core/imagenes/tamanos-de-imagen';

const MINIATURA_BASE =
  'anillo-foco relative size-64 cursor-pointer overflow-hidden bg-transparent p-0';
const MINIATURA = `${MINIATURA_BASE} border border-ts-borde`;
const MINIATURA_ACTIVA = `${MINIATURA_BASE} border-2 border-ts-primario`;

@Component({
  selector: 'ts-galeria',
  imports: [NgOptimizedImage],
  templateUrl: './ts-galeria.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsGaleria {
  /**
   * Las dos variantes completas, no una base más un `[class.x]`. `border` y
   * `border-2` tocan la misma propiedad: dejarlas juntas en el atributo hace
   * que gane el orden del CSS compilado y no la condición. Y son constantes,
   * así que no pasa por `cn()` en cada ciclo de detección.
   */
  protected claseMiniatura(activa: boolean): string {
    return activa ? MINIATURA_ACTIVA : MINIATURA;
  }

  private readonly transloco = inject(TranslocoService);

  readonly imagenes = input.required<readonly Imagen[]>();

  /**
   * El nombre del producto, que es a lo que cae el `alt` cuando la imagen no trae uno.
   *
   * Lo tenían `ts-tarjeta-producto` y la ficha desde siempre; esta galería caía a cadena vacía, y
   * eso no es "sin texto alternativo": en el `<img>` grande declara la foto **decorativa**, y en el
   * `<button>` de la miniatura deja el control **sin nombre accesible ninguno** —Angular solo quita
   * el atributo con `null`, no con `''`, y el `<img>` de dentro ya es `alt=""`—. Con lector de
   * pantalla la tira se anunciaba "botón, botón, botón". Un catálogo cargado por proveedor sin
   * `alt` es el caso normal, no el raro: `mapeador-productos.ts` normaliza el nulo del backend a
   * `''`.
   */
  readonly nombreProducto = input.required<string>();

  /**
   * Quién es la candidata a LCP lo sabe la pantalla, no el componente — mismo criterio que
   * `ts-tarjeta-producto`. En una ficha con visor 360 la prioritaria es el fotograma frontal del
   * visor, y esta deja de serlo: priorizar las dos es no priorizar ninguna.
   *
   * **Por omisión, `false`.** El valor por defecto tiene que ser el que no hace daño: una pantalla
   * nueva que se olvide de decidir se lleva una imagen sin priorizar, no una segunda candidata a
   * LCP compitiendo con la de verdad (`NG02955`, que este proyecto ya pagó dos veces).
   *
   * `priority` es una de las entradas que NgOptimizedImage congela tras inicializar, así que quien
   * la use tiene que pasar un valor fijo por instancia, no una expresión que cambie.
   */
  readonly prioritaria = input(false);

  /**
   * En qué foto se para la galería cuando la pantalla la mueve: la ficha la usa para saltar a la
   * foto del color elegido. Por omisión, la primera.
   */
  readonly indiceInicial = input(0);

  /**
   * Vuelve a la foto que diga la pantalla cuando cambian las fotos o cambia esa indicación —al
   * elegir otro color en la ficha—: con un `signal` suelto, el índice de la cuarta foto se quedaba
   * apuntando a nada en una lista de dos y la galería desaparecía entera. La fuente son las URL y
   * no el arreglo: una revalidación que devuelve las mismas fotos no tiene por qué mover a quien
   * está mirando.
   *
   * <b>El índice entra en la fuente, y hace falta.</b> Desde que la galería enseña todas las
   * fotos siempre, elegir un color ya no cambia la lista —solo cuál es la activa—, así que con
   * las URL por única fuente el salto al color elegido no ocurriría nunca. Y sigue siendo un
   * `linkedSignal` y no un `effect`: entre dos cambios, quien toca una miniatura manda.
   */
  protected readonly indiceActivo = linkedSignal({
    source: () => ({
      urls: this.imagenes()
        .map((imagen) => imagen.url)
        .join('|'),
      inicial: this.indiceInicial(),
    }),
    computation: (fuente: { urls: string; inicial: number }) => fuente.inicial,
  });

  protected readonly activa = computed<Imagen | null>(
    () => this.imagenes()[this.indiceActivo()] ?? this.imagenes()[0] ?? null,
  );

  /**
   * La foto que tocó quien mira, para que la pantalla decida qué significa. En la ficha, una foto
   * de un color elige ese color: sin este aviso la galería enseñaba el azul cielo mientras el
   * carrito recibía el beige que seguía elegido. La galería no sabe de colores; solo cuenta qué se
   * tocó.
   */
  readonly fotoElegida = output<Imagen>();

  protected elegir(indice: number): void {
    this.indiceActivo.set(indice);
    const imagen = this.imagenes()[indice];
    if (imagen) {
      this.fotoElegida.emit(imagen);
    }
  }

  protected readonly descriptores = descriptoresDe;
  protected readonly TAMANOS = TAMANOS_GALERIA;
  protected readonly TAMANOS_MINIATURA = TAMANOS_MINIATURA;

  protected readonly parametros = parametrosDe;

  protected alt(imagen: Imagen): string {
    const idioma = this.transloco.activeLang();
    return (idioma === 'en' ? imagen.altEn : imagen.altEs) || this.nombreProducto();
  }
}
