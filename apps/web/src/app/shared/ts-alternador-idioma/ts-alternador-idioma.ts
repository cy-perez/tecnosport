import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../core/i18n/traductor';
import { urlEnOtroIdioma } from '../../core/idioma/idioma.servicio';

const IDIOMAS = ['es', 'en'] as const;

type Idioma = (typeof IDIOMAS)[number];

/** El nombre accesible de cada segmento. El código de dos letras no lo da. */
const CLAVE_DE_ACCION: Record<Idioma, string> = {
  es: 'idioma.ver_en_espanol',
  en: 'idioma.ver_en_ingles',
};

/**
 * Cambiar de idioma es navegar, no guardar una preferencia: el idioma vive en
 * el primer segmento de la URL (`docs/05-i18n.md`), así que el control lleva a
 * la misma página con el otro prefijo — nunca a la portada.
 *
 * Fue un `<select>` sobre `ts-select` y ahora es un control segmentado, con la
 * forma del de la plantilla de referencia: una pista con una pastilla encima
 * marcando el activo. Comparte con `ts-alternador-tema`, que va justo al lado,
 * el alto de 44 px y la escala de radios; no el borde, que esta forma no lleva.
 * Con dos idiomas y nada más, mostrar los dos cuesta 44 px de ancho y ahorra la
 * ambigüedad de un código suelto — "EN" tanto puede leerse como "estás en
 * inglés" como "ve al inglés".
 *
 * **El idioma activo no es interactivo**, y eso es el patrón de `ts-migas`: un
 * `<span>` con `aria-current`, no un control que no lleva a ninguna parte. Así
 * el grupo tiene un solo punto de tabulación, el que de verdad hace algo.
 *
 * Sigue sin `FormControl` por lo mismo que antes: este componente vive en el
 * encabezado, o sea en todas las pantallas, y `@angular/forms` son 38,6 kB en
 * el paquete inicial de todo el sitio.
 */
@Component({
  selector: 'ts-alternador-idioma',
  templateUrl: './ts-alternador-idioma.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // `inline-flex` para que el elemento propio no herede el `display: inline`,
  // que le sumaría el interlineado al grupo de 44 px. Ya no hace falta el
  // `block w-fit` de antes: existía para domar el `inline-size: 100%` que
  // `ts-select` le pone a su `<select>`, y aquí no hay select.
  host: { class: 'inline-flex' },
})
export class TsAlternadorIdioma {
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);

  // `usarTraductor()` y no el pipe `| transloco`: en el entorno de pruebas el
  // pipe no reacciona a un cambio de idioma, así que no hay forma de fijar el
  // nombre accesible con una prueba. Mismo criterio que `ts-alternador-tema`.
  private readonly traducir = usarTraductor();

  protected readonly idiomas = IDIOMAS;

  // El idioma activo no siempre cambia desde aquí: un enlace con otro prefijo
  // o el botón de atrás del navegador también lo mueven. El control sigue a la
  // URL, no al revés — de ahí que se lea de la señal y no se guarde.
  protected readonly idiomaActivo = this.transloco.activeLang;

  /** El nombre del grupo entero, que el código de dos letras no explica. */
  protected readonly etiquetaDelGrupo = computed(() => this.traducir()('idioma.etiqueta'));

  protected accion(idioma: Idioma): string {
    return this.traducir()(CLAVE_DE_ACCION[idioma]);
  }

  protected navegarA(idioma: Idioma): void {
    void this.router.navigateByUrl(urlEnOtroIdioma(this.router.url, idioma));
  }
}
