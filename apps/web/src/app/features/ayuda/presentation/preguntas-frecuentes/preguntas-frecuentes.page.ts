import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { usarTraductorDeObjetos } from '../../../../core/i18n/traductor';
import { origenPublico } from '../../../../core/seo/origen-publico';
import { usarDatosEstructurados } from '../../../../core/seo/usar-metadatos';

/** Una pregunta con su respuesta en uno o varios párrafos. */
interface Pregunta {
  readonly pregunta: string;
  readonly respuesta: readonly string[];
}

/** Las preguntas agrupadas por tema, que es como se buscan. */
interface GrupoDePreguntas {
  readonly titulo: string;
  readonly preguntas: readonly Pregunta[];
}

/**
 * Las preguntas frecuentes.
 *
 * <p><b>Ninguna respuesta inventa nada.</b> Las catorce salen de los términos y condiciones ya
 * publicados —plazo de entrega, medios de pago, contraentrega, retracto, garantía, reversión del
 * pago, plazos de atención— y lo dicen en voz alta con un enlace al documento. Esta página resume
 * para que se lea; el documento es el que obliga. Si alguna vez dicen cosas distintas, el error
 * está aquí.
 *
 * <p>El texto vive entero en el scope `ayuda` de Transloco (regla dura #4), como una lista de
 * grupos que esta plantilla recorre: así se edita sin tocar el componente, y la versión en inglés
 * no se puede quedar corta sin que se note. Se lee con `usarTraductorDeObjetos` y no con el pipe
 * porque lo que hace falta es la estructura entera, no una cadena — mismo criterio que
 * `DocumentoLegalPage`.
 *
 * <p>Cada pregunta es un `<details>` nativo y no un acordeón propio, y es una decisión, no pereza:
 * el elemento ya trae el estado abierto/cerrado, el anuncio al lector de pantalla y la operación
 * con teclado, funciona sin una línea de JavaScript —así que sirve igual antes de hidratar, que es
 * justo cuando alguien llega desde un buscador— y el navegador lo abre solo cuando se busca texto
 * dentro de la página. Un acordeón propio tendría que reproducir las cuatro cosas.
 *
 * <p>Con `FAQPage` de schema.org: es de los pocos datos estructurados que un buscador usa para algo
 * concreto, y se arma de las mismas claves que pinta la plantilla, así que no hay una segunda copia
 * del texto que pueda quedarse vieja.
 */
@Component({
  selector: 'app-preguntas-frecuentes',
  imports: [RouterLink, TranslocoDirective],
  templateUrl: './preguntas-frecuentes.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PreguntasFrecuentesPage {
  private readonly transloco = inject(TranslocoService);
  private readonly traducirObjeto = usarTraductorDeObjetos();

  protected readonly idioma = this.transloco.activeLang;

  protected readonly grupos = computed(
    () => this.traducirObjeto()<GrupoDePreguntas[]>('ayuda.preguntas.grupos') ?? [],
  );

  /**
   * `FAQPage`, con las mismas preguntas que se ven. Los párrafos de una respuesta se unen con un
   * espacio: el bloque pide texto, no una lista, y un salto de línea dentro de un JSON-LD no
   * aporta nada a quien lo lee.
   */
  private readonly datosEstructurados = computed<object[]>(() => {
    const grupos = this.grupos();
    if (grupos.length === 0) {
      return [];
    }
    const preguntas = grupos.flatMap((grupo) => grupo.preguntas);
    return [
      {
        '@context': 'https://schema.org',
        '@type': 'FAQPage',
        '@id': `${origenPublico()}/${this.idioma()}/ayuda/preguntas-frecuentes`,
        mainEntity: preguntas.map((pregunta) => ({
          '@type': 'Question',
          name: pregunta.pregunta,
          acceptedAnswer: { '@type': 'Answer', text: pregunta.respuesta.join(' ') },
        })),
      },
    ];
  });

  constructor() {
    usarDatosEstructurados(() => this.datosEstructurados());
  }
}
