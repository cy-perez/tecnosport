import { provideRouter } from '@angular/router';
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
    providers: [provideRouter([])],
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

  /**
   * El caso que el orden anterior rompía: con el almacenamiento bloqueado —Safari en privado,
   * políticas de empresa— se guardaba antes de aplicar, así que la excepción dejaba la casilla
   * marcada y el documento sin `data-movimiento`. La preferencia de accesibilidad no se aplicaba, y
   * eso es peor que no recordarla.
   */
  it('con el almacenamiento bloqueado, la preferencia se aplica igual', async () => {
    // El espia va en Storage.prototype y no en window.localStorage: sobre la instancia, jsdom no
    // lo intercepta y la prueba pasaba con el defecto puesto — comprobado revirtiendo el arreglo.
    const guardar = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('QuotaExceededError');
    });
    await renderPie();
    const control = screen.getByRole('checkbox', { name: 'Reducir movimiento' }) as HTMLInputElement;

    fireEvent.click(control);

    expect(control.checked).toBe(true);
    expect(document.documentElement.getAttribute('data-movimiento')).toBe('reducido');
    guardar.mockRestore();
  });

  it('muestra el nombre comercial y el NIT, sin sigla societaria', async () => {
    await renderPie();

    expect(screen.getByText('Tecno Sport')).toBeTruthy();
    expect(screen.getByText('NIT 1054994043-9')).toBeTruthy();
  });

  // El número sale del JSON y no repetido aquí, igual que la dirección: es un dato de negocio
  // que ya cambió una vez —el 310 pasó a ser 313— y lo que hay que verificar es el cableado de
  // `tel:` y de `wa.me`, no el valor.
  it('enlaza el teléfono a tel: y a wa.me', async () => {
    await renderPie();

    expect(screen.getByRole('link', { name: 'Llamar' }).getAttribute('href')).toBe(
      `tel:${es.pie.telefono_e164}`,
    );
    expect(screen.getByRole('link', { name: 'WhatsApp' }).getAttribute('href')).toBe(
      `https://wa.me/${es.pie.whatsapp_numero}`,
    );
  });

  // La dirección sale del JSON, no repetida aquí: es un dato de negocio que ya
  // cambió una vez (contact@ -> contacto@) y lo que hay que verificar es el
  // cableado del mailto:, no el valor.
  it('enlaza el correo con mailto:', async () => {
    await renderPie();

    const correo = es.pie.correo;

    expect(screen.getByRole('link', { name: correo }).getAttribute('href')).toBe(`mailto:${correo}`);
  });

  // Los perfiles se enlazan de verdad, con su URL: un pie que dice "Facebook" sin enlace es lo
  // que había, y no llevaba a ninguna parte.
  it('enlaza los perfiles de redes sociales', async () => {
    await renderPie();

    expect(screen.getByRole('link', { name: 'Facebook' }).getAttribute('href')).toBe(
      es.pie.facebook_url,
    );
    expect(screen.getByRole('link', { name: 'Instagram' }).getAttribute('href')).toBe(
      es.pie.instagram_url,
    );
  });

  // El horario se publica porque es exigible: lo anunciado obliga. Y es el de los canales, no
  // el de un punto de venta — la prueba lo fija para que nadie lo mueva sin querer al retocar el
  // bloque de contacto.
  it('publica el horario de atención de los canales', async () => {
    await renderPie();

    expect(screen.getByText(es.pie.horario)).toBeTruthy();
  });

  it('muestra el año actual en el copyright', async () => {
    await renderPie();

    const anio = new Date().getFullYear();
    expect(screen.getByText(`© ${anio} Tecno Sport`)).toBeTruthy();
  });

  it('el pie lleva a la portada, al catálogo y al carrito', async () => {
    await renderPie();

    expect(screen.getByRole('link', { name: 'Portada' }).getAttribute('href')).toBe('/es');
    expect(screen.getByRole('link', { name: 'Catálogo' }).getAttribute('href')).toBe('/es/productos');
    expect(screen.getByRole('link', { name: 'Carrito' }).getAttribute('href')).toBe('/es/carrito');
  });

  // Es la única entrada al panel desde la vitrina, y tiene que estar sin sesión:
  // el enlace del encabezado solo aparece cuando `esAdmin()` ya es verdadero, así
  // que no sirve para llegar a iniciar sesión. Sin este, el panel —y con él la
  // pantalla de captura 360— solo se alcanzaba tecleando la ruta.
  it('el pie lleva al panel administrativo aunque no haya sesión', async () => {
    await renderPie();

    expect(screen.getByRole('link', { name: 'Panel administrativo' }).getAttribute('href')).toBe('/es/admin');
  });

  // La ley pide la política de datos publicada y enlazada en el pie
  // (docs/08-seguridad-legal.md). Una página legal a la que solo se llega tecleando la ruta no
  // está publicada, está escondida — la misma regla de cierre de fase que ya costó una corrección.
  it.each([
    ['Términos y condiciones', '/es/legales/terminos'],
    ['Política de datos', '/es/legales/privacidad'],
    ['Cookies', '/es/legales/cookies'],
  ])('enlaza %s en el pie', async (etiqueta, destino) => {
    await renderPie();

    const enlace = screen.getByRole('link', { name: etiqueta });

    expect(enlace.getAttribute('href')).toBe(destino);
  });
});
