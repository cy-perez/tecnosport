import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Encabezado } from './layout/encabezado/encabezado';
import { MenuLateral } from './layout/menu-lateral/menu-lateral';
import { MenuLateralStore } from './layout/menu-lateral/menu-lateral.store';
import { Pie } from './layout/pie/pie';

@Component({
  imports: [RouterOutlet, TranslocoPipe, Encabezado, MenuLateral, Pie],
  selector: 'app-root',
  templateUrl: './app.html',
  // Tres filas: encabezado, contenido y pie. El contenido se estira (`1fr`)
  // para que el pie quede siempre al fondo — con `display: block` y
  // `min-height`, el pie se apilaba justo debajo del contenido y dejaba el
  // espacio sobrante *debajo* de él, que es lo que se veía como "el pie está
  // más arriba de donde debería".
  //
  // Va en `host` y no en una plantilla porque son los estilos del propio
  // `app-root`: es el reemplazo del `:host` del SCSS que este componente
  // tenía. `relative` es el ancla del enlace de salto, que es `absolute`.
  //
  // El relleno de la izquierda es lo que le hace sitio al menú lateral, que es `fixed`: un `fixed`
  // no reserva espacio por su cuenta —está fuera del flujo—, y sin esto el logo del encabezado
  // quedaría debajo del riel. Solo desde el primer punto de quiebre porque en móvil el menú no
  // existe: ahí el árbol vive en el panel desplegable del encabezado.
  //
  // **El ancho es el riel, salvo con el menú fijado**, y esa es la diferencia entre desplegar y
  // fijar: desplegar pinta el panel encima del contenido —el gesto tiene que ser barato y no mover
  // nada bajo el cursor—, y fijar le hace sitio de verdad. Un panel fijado que siguiera encima
  // taparía justo lo que se está mirando, que es lo contrario de para lo que se fija.
  //
  // Va por variable y no por dos clases alternas porque el nombre de una utilidad con variante
  // (`desde-movil:ps-menu-lateral`) no se puede ligar con `[class.…]` sin pelearse con los dos
  // puntos. Con la variable, la clase es estática y lo que cambia es su valor.
  host: {
    class:
      'relative grid min-h-screen grid-rows-[auto_1fr_auto] bg-ts-fondo font-texto text-ts-texto transition-[padding] ease-[var(--curva-suave)] desde-movil:ps-[var(--ancho-menu-actual)]',
    '[style.--ancho-menu-actual]':
      "menu.fijado() ? 'var(--ancho-menu-lateral)' : 'var(--ancho-menu-riel)'",
    '[style.transitionDuration]': "menu.sinMovimiento() ? '0ms' : 'var(--mov-panel)'",
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly menu = inject(MenuLateralStore);
}
