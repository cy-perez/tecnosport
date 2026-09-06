import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { TsBoton } from '../../../../shared/ts-boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsEtiquetaStock } from '../../../../shared/ts-etiqueta-stock/ts-etiqueta-stock';
import { TsGaleria } from '../../../../shared/ts-galeria/ts-galeria';
import { Miga, TsMigas } from '../../../../shared/ts-migas/ts-migas';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { TsSelectorVariante } from '../../../../shared/ts-selector-variante/ts-selector-variante';
import { TsVisor360 } from '../../../../shared/ts-visor-360/ts-visor-360';
import { usarFichaProducto } from '../../application/buscar-ficha-producto.consulta';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { Imagen } from '../../domain/producto.model';
import { ejesDeAtributos, Seleccion, seleccionDeVariante, variantePorDefecto, varianteSeleccionada } from '../../domain/seleccion-variante';

@Component({
  selector: 'app-ficha',
  imports: [
    TranslocoPipe,
    TsGaleria,
    TsSelectorVariante,
    TsPrecio,
    TsEtiquetaStock,
    TsEsqueleto,
    TsBoton,
    TsMigas,
    TsVisor360,
  ],
  templateUrl: './ficha.page.html',
  styleUrl: './ficha.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FichaPage {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();
  protected readonly carrito = inject(CarritoStore);

  private readonly slug = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });

  protected readonly consulta = usarFichaProducto(() => this.slug().get('slug') ?? '');

  protected readonly producto = computed(() => this.consulta.data());

  // `usarTraductor`, no `transloco.translate()` directo: dentro de un
  // `computed` ese no es reactivo y las etiquetas se quedarían en el idioma
  // con el que se creó el componente (apps/web/CLAUDE.md).
  protected readonly migas = computed<Miga[]>(() => {
    const traducir = this.traducir();
    const idioma = this.transloco.activeLang();
    return [
      { etiqueta: traducir('migas.portada'), enlace: ['/', idioma] },
      { etiqueta: traducir('migas.catalogo'), enlace: ['/', idioma, 'productos'] },
      { etiqueta: this.producto()?.nombre ?? '' },
    ];
  });

  protected readonly imagenesGaleria = computed<Imagen[]>(() => {
    const producto = this.producto();
    if (!producto) {
      return [];
    }
    return [producto.imagenPrincipal, ...producto.galeria].filter((imagen): imagen is Imagen => imagen !== null);
  });

  /**
   * El visor recibe URL y nada más. Se ordena por `orden` aquí y no se confía en el orden en que
   * llegue el arreglo: el giro depende de esa secuencia, y un fotograma fuera de sitio se ve como
   * un salto (`docs/10-captura-360.md`).
   *
   * WebP con el original de respaldo, por la regla de imágenes de `apps/web/CLAUDE.md`.
   */
  protected readonly fotogramas360 = computed<string[]>(() => {
    const rotacion = this.producto()?.rotacion;
    if (!rotacion) {
      return [];
    }
    return [...rotacion.imagenes].sort((uno, otro) => uno.orden - otro.orden).map((imagen) => imagen.urlWebp || imagen.url);
  });

  protected readonly ejes = computed(() => {
    const producto = this.producto();
    return producto ? ejesDeAtributos(producto) : [];
  });

  protected readonly seleccion = signal<Seleccion>({});

  protected readonly varianteActiva = computed(() => {
    const producto = this.producto();
    return producto ? varianteSeleccionada(producto, this.seleccion()) : null;
  });

  constructor() {
    // Reinicia la selección a la variante por defecto cada vez que carga un producto distinto
    // (primer render, o al navegar de una ficha a otra).
    effect(() => {
      const producto = this.producto();
      if (!producto) {
        return;
      }
      const variante = variantePorDefecto(producto);
      this.seleccion.set(variante ? seleccionDeVariante(variante) : {});
    });
  }

  protected cambiarSeleccion(seleccion: Seleccion): void {
    this.seleccion.set(seleccion);
  }

  protected agregarAlCarrito(): void {
    const producto = this.producto();
    const variante = this.varianteActiva();
    if (!producto || !variante) {
      return;
    }
    const idioma = this.transloco.activeLang();
    const imagen = producto.imagenPrincipal;
    void this.carrito.agregarAlCarrito(variante.id, 1, {
      varianteId: variante.id,
      nombreProducto: producto.nombre,
      slugProducto: producto.slug,
      sku: variante.sku,
      imagenUrl: imagen?.url ?? null,
      imagenAlt: (imagen ? (idioma === 'en' ? imagen.altEn : imagen.altEs) : '') || producto.nombre,
      precioValor: variante.precio.valor,
      precioMoneda: variante.precio.moneda,
    });
  }
}
