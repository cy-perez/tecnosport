import { ActivatedRoute, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import legalesEn from '../../../../../assets/i18n/scopes/legales/en.json';
import legalesEs from '../../../../../assets/i18n/scopes/legales/es.json';
import { DocumentoLegalPage } from './documento-legal.page';

type Documento = 'privacidad' | 'terminos' | 'cookies';

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
});
