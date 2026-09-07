import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsBoton } from '../ui/boton/ts-boton';

/**
 * Paginación por página, la de las listas del panel administrativo
 * (`docs/03-api.md` la distingue de la paginación por cursor de la vitrina).
 *
 * `pagina` es 0-based, como el filtro y la API; lo que se muestra es 1-based,
 * como en la URL. La conversión vive aquí y no en cada pantalla.
 */
@Component({
  selector: 'ts-paginador',
  imports: [TranslocoPipe, TsBoton],
  templateUrl: './ts-paginador.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsPaginador {
  readonly pagina = input.required<number>();
  readonly totalPaginas = input.required<number>();

  /** Emite la página destino, 0-based. */
  readonly paginaCambiada = output<number>();

  /**
   * Una lista vacía viene con `totalPaginas: 0` del backend, y "Página 1 de 0"
   * no se le dice a nadie: sin resultados sigue habiendo una página, la que se
   * está viendo.
   */
  protected readonly total = computed(() => Math.max(1, this.totalPaginas()));

  protected readonly hayAnterior = computed(() => this.pagina() > 0);
  protected readonly haySiguiente = computed(() => this.pagina() + 1 < this.total());

  protected irA(pagina: number): void {
    this.paginaCambiada.emit(pagina);
  }
}
