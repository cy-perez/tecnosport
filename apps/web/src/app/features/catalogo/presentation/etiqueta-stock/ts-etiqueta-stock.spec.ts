import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { TsEtiquetaStock } from './ts-etiqueta-stock';

async function renderEtiqueta(disponible: boolean) {
  return render(TsEtiquetaStock, {
    inputs: { disponible },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'catalogo/es': esCatalogo } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
  });
}

describe('TsEtiquetaStock', () => {
  it('anuncia la disponibilidad con texto, no solo con color', async () => {
    await renderEtiqueta(true);

    expect(screen.getByText('Disponible')).toBeTruthy();
  });

  it('anuncia el agotado con texto', async () => {
    await renderEtiqueta(false);

    expect(screen.getByText('Agotado')).toBeTruthy();
  });

  // El color es la única diferencia visual entre los dos estados, así que se
  // comprueba: si una variante deja de aplicar el suyo, nada más lo atrapa.
  it('disponible va en el color de éxito', async () => {
    const { container } = await renderEtiqueta(true);

    expect(container.querySelector('span')?.className).toContain('text-ts-exito');
  });

  it('agotado va en gris y no en rojo: es un estado, no un error', async () => {
    const { container } = await renderEtiqueta(false);
    const clases = container.querySelector('span')?.className ?? '';

    expect(clases).toContain('text-ts-texto-suave');
    expect(clases).not.toContain('text-ts-error');
  });
});
