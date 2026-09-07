import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { hayExistencia, precioDesde, Producto, urlPreferida } from '../../domain/producto.model';
import { TsEtiquetaStock } from '../etiqueta-stock/ts-etiqueta-stock';

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
  imports: [NgOptimizedImage, TranslocoPipe, TsPrecio, TsEtiquetaStock, RouterLink],
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
  protected readonly url = urlPreferida;

  protected readonly precio = computed(() => precioDesde(this.producto()));
  protected readonly disponible = computed(() => hayExistencia(this.producto()));

  protected readonly alt = computed(() => {
    const producto = this.producto();
    const imagen = producto.imagenPrincipal;
    if (!imagen) {
      return producto.nombre;
    }
    const idioma = this.transloco.activeLang();
    return (idioma === 'en' ? imagen.altEn : imagen.altEs) || producto.nombre;
  });
}
