import { TsMuestraColor } from '../../../../shared/ui/muestra-color/ts-muestra-color';
import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import {
  descriptoresDe,
  hayExistencia,
  parametrosDe,
  precioDesde,
  Producto,
} from '../../domain/producto.model';
import { TAMANOS_TARJETA } from '../../../../core/imagenes/tamanos-de-imagen';
import { TsEtiquetaStock } from '../etiqueta-stock/ts-etiqueta-stock';
import { usarPaletaDeColores } from '../../application/listar-paleta-colores.consulta';
import { usarIdiomaActivo } from '../../../../core/i18n/traductor';
import {
  coloresDe,
  imagenDelColor,
  nombreDeColor,
  tallaUnicaDe,
} from '../../domain/seleccion-variante';

/** Cuántas muestras caben en una tarjeta sin envolver en dos filas; el resto se cuenta. */
const MUESTRAS_VISIBLES = 5;

const MUESTRA_BASE = 'block size-[var(--control-muestra-tarjeta)] overflow-hidden rounded-completo';
const MUESTRA = `${MUESTRA_BASE} border border-ts-borde-control`;
// La elegida conserva su borde fino y gana un anillo **exterior y separado** (`outline` con
// `--foco-separacion`), del color primario. Era un `border-2` del mismo color: sobre una muestra
// oscura —negro, azul marino— el borde grafito se fundía con el color en tema claro y no se sabía
// cuál estaba elegida. Con la separación el anillo se pinta sobre la superficie de la tarjeta, que
// es contra lo que está medido `primario` (`npm run contrastes`), sea cual sea el color de la
// muestra. Y el borde no cambia de ancho, así que elegir no mueve nada.
const MUESTRA_ACTIVA = `${MUESTRA} outline-foco outline-offset-foco outline-ts-primario`;

/**
 * Vive en `features/catalogo/presentation` y no en `shared/`, que es de donde
 * vino. Nunca fue compartida —sus únicos consumidores son la portada y la
 * rejilla— y además importa `catalogo/domain`: un componente de `shared/`
 * dependiendo del dominio de una funcionalidad es la flecha al revés, y ESLint
 * no lo veía porque sus reglas de límites solo cubren `features/**`. Aquí la
 * flecha `presentation → domain` es la correcta y sí está verificada.
 */
@Component({
  selector: 'ts-tarjeta-producto',
  imports: [NgOptimizedImage, TranslocoPipe, TsPrecio, TsEtiquetaStock, RouterLink, TsMuestraColor],
  templateUrl: './ts-tarjeta-producto.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsTarjetaProducto {
  private readonly transloco = inject(TranslocoService);

  readonly producto = input.required<Producto>();

  /**
   * Solo la primera tarjeta de una pantalla, y solo si es la candidata a LCP.
   * En la portada lo es: el hero es un bloque de CSS, no un <img>, así que la
   * imagen más grande del primer viewport es esta, y sin `priority` Angular
   * avisaba (NG02955). Marcarlas todas sería peor que ninguna: priorizar todo
   * es no priorizar nada.
   */
  readonly prioritaria = input(false);

  // Absoluto, no relativo: la tarjeta se usa en la rejilla (/{lang}/productos)
  // y en la portada (/{lang}), y un enlace relativo al slug apuntaría a un
  // lugar distinto en cada una.
  protected readonly enlace = computed(() => [
    '/',
    this.transloco.activeLang(),
    'productos',
    this.producto().slug,
  ]);

  /** La misma regla que la galería y el visor 360: WebP con el original de respaldo. */
  protected readonly descriptores = descriptoresDe;
  protected readonly TAMANOS = TAMANOS_TARJETA;

  protected readonly parametros = parametrosDe;

  private readonly paleta = usarPaletaDeColores();
  private readonly idioma = usarIdiomaActivo();

  /** El color que el visitante eligió en esta tarjeta; nulo, la foto principal. */
  protected readonly colorElegido = signal<string | null>(null);

  protected readonly colores = computed(() => coloresDe(this.producto()));
  protected readonly coloresVisibles = computed(() => this.colores().slice(0, MUESTRAS_VISIBLES));
  protected readonly coloresOcultos = computed(() =>
    Math.max(0, this.colores().length - MUESTRAS_VISIBLES),
  );

  protected readonly imagenMostrada = computed(() => {
    const imagen = imagenDelColor(this.producto(), this.colorElegido());
    return imagen ? [imagen] : [];
  });

  protected readonly tallaUnica = computed(() => tallaUnicaDe(this.producto()));

  protected readonly precio = computed(() => precioDesde(this.producto()));
  protected readonly disponible = computed(() => hayExistencia(this.producto()));

  /** «Negro» en la vitrina en español, «Black» en la de inglés. */
  protected nombreDeColor(valor: string): string {
    return nombreDeColor(valor, this.paleta.data() ?? [], this.idioma());
  }

  protected elegirColor(color: string): void {
    this.colorElegido.set(color);
  }

  /** Completas y no compuestas: `border` y `border-2` compiten por la misma propiedad. */
  protected claseMuestra(activa: boolean): string {
    return activa ? MUESTRA_ACTIVA : MUESTRA;
  }
}
