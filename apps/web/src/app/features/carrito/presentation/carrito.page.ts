import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { usarTraductor } from '../../../core/i18n/traductor';
import { usarFoco } from '../../../shared/foco/foco';
import { TsBoton } from '../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../shared/ts-esqueleto/ts-esqueleto';
import { TsPrecio } from '../../../shared/ts-precio/ts-precio';
import { CarritoStore } from '../application/carrito.store';
import { LineaCarritoComponent } from './linea-carrito/linea-carrito';

@Component({
  selector: 'app-carrito',
  imports: [TranslocoPipe, TsBoton, TsEsqueleto, TsPrecio, LineaCarritoComponent],
  templateUrl: './carrito.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CarritoPage {
  protected readonly store = inject(CarritoStore);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');
  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly traducir = usarTraductor();

  /**
   * Lo último que pasó con una línea, para la región viva. Cambiar la cantidad o quitar una línea
   * no anunciaba nada —ni la cantidad nueva, ni el total, ni que había fallado—, y un fallo se
   * tragaba con `void`.
   */
  protected readonly anuncioLinea = signal<
    | { readonly tipo: 'cantidad'; readonly nombre: string; readonly cantidad: number }
    | { readonly tipo: 'eliminada'; readonly nombre: string }
    | { readonly tipo: 'error' }
    | null
  >(null);

  /**
   * La foto se resuelve aquí y no en la plantilla. Llamar a `snapshotDeLinea` desde el `@for`
   * parecía inocente y era una lectura de `localStorage` por línea **en cada ciclo de detección**;
   * un `computed` solo se recalcula cuando el carrito cambia de verdad.
   */
  protected readonly lineas = computed(() => {
    const carrito = this.store.consulta.data();
    if (!carrito) {
      return [];
    }
    return carrito.lineas.map((linea) => ({
      linea,
      snapshot: this.store.snapshotDeLinea(linea.varianteId),
      precio: this.store.precioDeLinea(linea.id),
    }));
  });

  /** El del servidor (`CarritoStore.subtotal`), no la suma de los precios guardados al agregar. */
  protected readonly total = computed(() => this.store.subtotal());

  protected cambiarCantidad(lineaId: string, cantidad: number, nombre?: string): void {
    this.store.actualizarCantidad(lineaId, cantidad).then(
      () => this.anuncioLinea.set({ tipo: 'cantidad', nombre: this.nombreDe(nombre), cantidad }),
      () => this.anuncioLinea.set({ tipo: 'error' }),
    );
  }

  /**
   * Al quitar una línea su botón desaparece con ella, y el navegador manda el foco a `<body>`. Se
   * lleva a la línea que ocupa ahora su lugar —la siguiente— o, si era la última, a la anterior; y
   * si el carrito quedó vacío, al título. Si falla, el botón sigue ahí y el foco no se toca.
   */
  protected eliminarLinea(lineaId: string, indice: number, nombre?: string): void {
    this.store.eliminarLinea(lineaId).then(
      () => {
        this.anuncioLinea.set({ tipo: 'eliminada', nombre: this.nombreDe(nombre) });
        this.enfocarDespuesDePintar(() => {
          const lineas = this.anfitrion.nativeElement.querySelectorAll<HTMLElement>('[data-linea]');
          return lineas[indice] ?? lineas[indice - 1] ?? this.titulo()?.nativeElement;
        });
      },
      () => this.anuncioLinea.set({ tipo: 'error' }),
    );
  }

  /** El nombre guardado de la línea, o el genérico si no hay foto guardada (otro dispositivo). */
  private nombreDe(nombre: string | undefined): string {
    return nombre ?? this.traducir()('carrito.producto_sin_foto');
  }
}
