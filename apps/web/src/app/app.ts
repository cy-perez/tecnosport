import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Encabezado } from './layout/encabezado/encabezado';
import { Pie } from './layout/pie/pie';

@Component({
  imports: [RouterOutlet, TranslocoPipe, Encabezado, Pie],
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
  host: {
    class:
      'relative grid min-h-screen grid-rows-[auto_1fr_auto] bg-ts-fondo font-texto text-ts-texto',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
