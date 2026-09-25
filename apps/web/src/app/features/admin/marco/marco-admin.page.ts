import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PestanasAdmin } from './pestanas-admin';

/**
 * El marco de las pantallas del panel: la barra de secciones y debajo la pantalla.
 *
 * <p>Existe por una sola razón, y es la que convierte un menú en unas pestañas: la barra tiene que
 * seguir ahí después de pulsar una. Hasta el 25 de septiembre de 2026 los enlaces a las secciones
 * vivían dentro de `panel-admin.page.html`, así que desaparecían en cuanto llevaban a alguna parte
 * y volver exigía pasar otra vez por el panel.
 *
 * <p>Envuelve a las pantallas **con sesión** y no a la rama entera: `iniciar-sesion` es hermana
 * suya en `admin.routes.ts`, no hija, porque una barra de secciones sobre un formulario de entrada
 * ofrece diez destinos que el guardián va a rechazar uno por uno. Por lo mismo `adminGuard` subió
 * a este nodo: estaba declarado nueve veces, una por pantalla, y era la misma condición.
 */
@Component({
  selector: 'app-marco-admin',
  imports: [RouterOutlet, PestanasAdmin],
  templateUrl: './marco-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarcoAdminPage {}
