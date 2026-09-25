import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import ayudaEn from '../../../../../assets/i18n/scopes/ayuda/en.json';
import ayudaEs from '../../../../../assets/i18n/scopes/ayuda/es.json';
import { PreguntasFrecuentesPage } from './preguntas-frecuentes.page';

async function renderPreguntas() {
  const resultado = await render(PreguntasFrecuentesPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'ayuda/es': ayudaEs, 'ayuda/en': ayudaEn } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [provideRouter([])],
  });
  await resultado.fixture.whenStable();
  return resultado;
}

/** Las preguntas del JSON, aplanadas: es contra esto que se compara lo pintado. */
const PREGUNTAS = ayudaEs.preguntas.grupos.flatMap((grupo) => grupo.preguntas);

describe('PreguntasFrecuentesPage', () => {
  it('pinta cada grupo con su título y cada pregunta con su respuesta', async () => {
    const { container } = await renderPreguntas();

    for (const grupo of ayudaEs.preguntas.grupos) {
      expect(screen.getByRole('heading', { name: grupo.titulo })).toBeTruthy();
    }
    expect(container.querySelectorAll('details')).toHaveLength(PREGUNTAS.length);
    for (const pregunta of PREGUNTAS) {
      expect(screen.getByText(pregunta.pregunta)).toBeTruthy();
      for (const parrafo of pregunta.respuesta) {
        expect(screen.getByText(parrafo)).toBeTruthy();
      }
    }
  });

  /**
   * El aviso y su enlace son lo que separa esta página de un documento legal: aquí se resume para
   * que se lea, y lo que obliga es el documento. Sin el enlace, quien necesita el texto vinculante
   * no tiene desde dónde llegar a él.
   */
  it('avisa que lo que obliga son los términos, y enlaza al documento', async () => {
    await renderPreguntas();

    expect(screen.getByText(ayudaEs.preguntas.nota)).toBeTruthy();
    expect(
      screen.getByRole('link', { name: ayudaEs.preguntas.ver_terminos }).getAttribute('href'),
    ).toBe('/es/legales/terminos');
  });

  /**
   * `<details>` nativo y no un acordeón propio: trae el estado, el anuncio al lector de pantalla y
   * la operación con teclado, y funciona sin JavaScript. Lo que esta prueba fija es que siga
   * siendo `<details>`/`<summary>` — el día que alguien lo "mejore" con divs y un `(click)`, esas
   * cuatro cosas se pierden en silencio.
   */
  it('cada pregunta es un details con su summary, y nace cerrada', async () => {
    const { container } = await renderPreguntas();

    const detalles = [...container.querySelectorAll('details')];
    expect(detalles.every((detalle) => detalle.querySelector('summary') !== null)).toBe(true);
    expect(detalles.every((detalle) => !detalle.open)).toBe(true);
  });

  /**
   * `FAQPage` sale de las mismas claves que pinta la plantilla, así que no hay una segunda copia
   * del texto que pueda quedarse vieja. Se comprueba el recuento y una pregunta concreta: si
   * alguien añade una al JSON y el bloque no crece, es que se desconectaron.
   */
  it('publica las mismas preguntas como datos estructurados', async () => {
    await renderPreguntas();

    const bloques = [...document.querySelectorAll('script[type="application/ld+json"]')].map(
      (script) => JSON.parse(script.textContent ?? '{}'),
    );
    const faq = bloques.find((bloque) => bloque['@type'] === 'FAQPage');
    expect(faq).toBeTruthy();
    expect(faq.mainEntity).toHaveLength(PREGUNTAS.length);
    expect(faq.mainEntity[0]).toEqual({
      '@type': 'Question',
      name: PREGUNTAS[0].pregunta,
      acceptedAnswer: { '@type': 'Answer', text: PREGUNTAS[0].respuesta.join(' ') },
    });
  });

  /**
   * Las dos versiones se editan a mano y en momentos distintos, así que lo que se rompe de verdad
   * es que una crezca y la otra no. No se comparan textos —son traducciones— sino la forma.
   */
  it('la versión en inglés tiene los mismos grupos y las mismas preguntas', async () => {
    expect(ayudaEn.preguntas.grupos).toHaveLength(ayudaEs.preguntas.grupos.length);
    ayudaEs.preguntas.grupos.forEach((grupo, i) => {
      expect(ayudaEn.preguntas.grupos[i].preguntas).toHaveLength(grupo.preguntas.length);
      grupo.preguntas.forEach((pregunta, j) => {
        expect(ayudaEn.preguntas.grupos[i].preguntas[j].respuesta).toHaveLength(
          pregunta.respuesta.length,
        );
      });
    });
  });
});
