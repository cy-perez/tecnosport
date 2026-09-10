import { ActivatedRoute, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import { of } from 'rxjs';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import legalesEn from '../../../../../assets/i18n/scopes/legales/en.json';
import legalesEs from '../../../../../assets/i18n/scopes/legales/es.json';
import { DocumentoLegalPage } from './documento-legal.page';

type Documento = 'privacidad' | 'terminos' | 'cookies';

interface SeccionComparable {
  readonly titulo: string;
  readonly parrafos?: readonly string[];
  readonly lista?: readonly string[];
  readonly cierre?: readonly string[];
}

/**
 * La forma de una sección: su número y cuántos bloques trae de cada tipo. El título **no** entra
 * —está traducido, y compararlo sería comparar el inglés con el castellano—, pero el número que lo
 * encabeza sí: es el que se cita en una reclamación, y tiene que ser el mismo en los dos idiomas.
 */
function forma(seccion: SeccionComparable): string {
  const numero = seccion.titulo.split('.')[0];
  return [
    numero,
    seccion.parrafos?.length ?? 0,
    seccion.lista?.length ?? 0,
    seccion.cierre?.length ?? 0,
  ].join('/');
}

async function renderDocumento(documento: Documento, lang: 'es' | 'en' = 'es') {
  return render(DocumentoLegalPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: {
          es,
          en,
          'legales/es': legalesEs,
          'legales/en': legalesEn,
        } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: lang },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { data: of({ documento }) } },
    ],
  });
}

describe('DocumentoLegalPage', () => {
  it.each([
    ['privacidad' as Documento, 'Política de tratamiento de datos personales'],
    ['terminos' as Documento, 'Términos y condiciones'],
    ['cookies' as Documento, 'Política de cookies'],
  ])('pinta el título de %s', async (documento, titulo) => {
    await renderDocumento(documento);

    expect(screen.getByRole('heading', { level: 1, name: titulo })).toBeTruthy();
  });

  // El texto vive en el JSON como lista de secciones: si el componente dejara de recorrerla, la
  // página saldría con título y sin documento, que es exactamente el fallo que nadie nota.
  it('pinta todas las secciones del documento, no solo el encabezado', async () => {
    await renderDocumento('privacidad');

    const secciones = screen.getAllByRole('heading', { level: 2 });

    // 16 secciones de la política, más el encabezado de "otros documentos".
    expect(secciones.length).toBe(17);
  });

  it('incluye el responsable y su NIT, que la ley exige identificar', async () => {
    await renderDocumento('privacidad');

    // getAllByText y no getByText: el nombre y el NIT aparecen tanto en el aviso del encabezado
    // como en la sección 1, y esa repetición es deliberada — el deber de identificación se cumple
    // esté donde esté mirando quien lee.
    expect(screen.getAllByText(/Cristhian Yurday Pérez Hoyos/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/1054994043-9/).length).toBeGreaterThan(0);
  });

  it('los términos explican el retracto con su plazo legal', async () => {
    await renderDocumento('terminos');

    expect(screen.getByText(/cinco \(5\) días hábiles siguientes a la entrega/)).toBeTruthy();
    // El plazo de reintegro sale en varias secciones (disponibilidad, retracto): es el mismo plazo
    // legal y tiene que decir lo mismo en todas.
    expect(screen.getAllByText(/quince \(15\) días calendario/).length).toBeGreaterThan(0);
  });

  it('enlaza los otros dos documentos', async () => {
    await renderDocumento('cookies');

    expect(screen.getByRole('link', { name: 'Política de tratamiento de datos' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Términos y condiciones' })).toBeTruthy();
  });

  it('sirve el documento en inglés', async () => {
    await renderDocumento('cookies', 'en');

    expect(screen.getByRole('heading', { level: 1, name: 'Cookie policy' })).toBeTruthy();
  });

  // La nota de idioma existía en el JSON en inglés desde que se escribieron los documentos y
  // **ninguna plantilla la mostraba**: una clave traducida no es una clave visible, y nada lo
  // avisaba. No es un detalle de forma. La Ley 1480 de 2011 exige castellano en la información al
  // consumidor (art. 23) y en el contrato (art. 37.1, con las condiciones que no cumplan
  // declaradas ineficaces), así que la versión que rige es siempre la española; sin esta nota,
  // quien compra navegando en inglés —el checkout y el registro enlazan la versión de su propio
  // idioma— acepta un documento cuyo original nunca vio. Por eso se comprueba en los dos idiomas
  // y en los tres documentos, no solo que la clave exista.
  it.each([
    ['privacidad' as Documento, 'es' as const, /prevalece el texto en castellano/],
    ['terminos' as Documento, 'es' as const, /prevalece el texto en castellano/],
    ['cookies' as Documento, 'es' as const, /prevalece el texto en castellano/],
    ['privacidad' as Documento, 'en' as const, /the Spanish text prevails/],
    ['terminos' as Documento, 'en' as const, /the Spanish text prevails/],
    ['cookies' as Documento, 'en' as const, /the Spanish text prevails/],
  ])('muestra la nota de idioma en %s (%s)', async (documento, lang, texto) => {
    await renderDocumento(documento, lang);

    expect(screen.getByText(texto)).toBeTruthy();
  });

  /**
   * **Son dos documentos y los dos se leen.** Una promesa que el texto hace en un idioma y no en
   * el otro no es una errata de traducción: es un documento legal distinto para quien navega en
   * inglés, y no hay nota de cortesía que lo salve — la nota dice cuál rige, no rellena lo que
   * falta.
   *
   * Esta prueba nació de un fallo real: al reescribir la sección de envío se cambió un párrafo en
   * castellano ("el plazo aplicable") y el inglés se quedó con el anterior ("the agreed term"),
   * que ya no era cierto porque no hay plazo pactado. Nadie lo habría visto: las dos páginas
   * renderizan, las dos pasan accesibilidad, y ninguna prueba comparaba los textos entre sí.
   *
   * Compara la **estructura**, que es lo que se puede comparar sin saber inglés: mismos títulos de
   * sección en el mismo orden, y misma cantidad de párrafos, viñetas y cierres en cada una. Un
   * párrafo que se añade en un idioma y no en el otro rompe aquí.
   */
  it.each([['privacidad' as Documento], ['terminos' as Documento], ['cookies' as Documento]])(
    'el documento de %s tiene la misma estructura en los dos idiomas',
    (documento) => {
      const seccionesEs = legalesEs[documento].secciones as readonly SeccionComparable[];
      const seccionesEn = legalesEn[documento].secciones as readonly SeccionComparable[];

      expect(seccionesEn.map(forma)).toEqual(seccionesEs.map(forma));
    },
  );

  // Son las páginas que más texto largo tienen del sitio, y las que alguien va a leer con lector
  // de pantalla justo cuando tiene un problema con una compra.
  it.each([['privacidad' as Documento], ['terminos' as Documento], ['cookies' as Documento]])(
    'la página de %s no tiene violaciones de WCAG 2.2 AA',
    async (documento) => {
      const { container } = await renderDocumento(documento);

      await esperarSinViolaciones(container);
    },
  );
});
