import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { Pie } from './pie';

async function renderPie() {
  return render(Pie, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
  });
}

describe('Pie', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute('data-movimiento');
  });

  it('marcar "Reducir movimiento" lo guarda y lo aplica al documento', async () => {
    await renderPie();
    const control = screen.getByRole('checkbox', { name: 'Reducir movimiento' }) as HTMLInputElement;
    expect(control.checked).toBe(false);

    fireEvent.click(control);

    expect(control.checked).toBe(true);
    expect(document.documentElement.getAttribute('data-movimiento')).toBe('reducido');
    expect(window.localStorage.getItem('ts-movimiento-reducido')).toBe('true');
  });

  it('desmarcarlo lo quita del documento y del almacenamiento', async () => {
    await renderPie();
    const control = screen.getByRole('checkbox', { name: 'Reducir movimiento' }) as HTMLInputElement;
    fireEvent.click(control);

    fireEvent.click(control);

    expect(control.checked).toBe(false);
    expect(document.documentElement.getAttribute('data-movimiento')).toBe('normal');
    expect(window.localStorage.getItem('ts-movimiento-reducido')).toBe('false');
  });

  it('muestra el nombre comercial y el NIT, sin sigla societaria', async () => {
    await renderPie();

    expect(screen.getByText('Tecno Sport')).toBeTruthy();
    expect(screen.getByText('NIT 1054994043-1')).toBeTruthy();
  });

  it('enlaza el teléfono a tel: y a wa.me', async () => {
    await renderPie();

    expect(screen.getByRole('link', { name: 'Llamar' }).getAttribute('href')).toBe('tel:+573104209655');
    expect(screen.getByRole('link', { name: 'WhatsApp' }).getAttribute('href')).toBe('https://wa.me/573104209655');
  });

  it('enlaza el correo con mailto:', async () => {
    await renderPie();

    expect(screen.getByRole('link', { name: 'contact@tecnosport.co' }).getAttribute('href')).toBe(
      'mailto:contact@tecnosport.co',
    );
  });

  it('muestra el año actual en el copyright', async () => {
    await renderPie();

    const anio = new Date().getFullYear();
    expect(screen.getByText(`© ${anio} Tecno Sport`)).toBeTruthy();
  });
});
