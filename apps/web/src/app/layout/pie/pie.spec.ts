import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
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
  it('muestra el nombre comercial y el NIT, sin sigla societaria', async () => {
    await renderPie();

    expect(screen.getByText('Tecno Sport')).toBeTruthy();
    expect(screen.getByText('NIT 1054994043-9')).toBeTruthy();
  });

  /**
   * El número se enseña, no se esconde detrás de un verbo. Hasta el 25 de septiembre de 2026 aquí
   * había un enlace "Llamar" con el `tel:` detrás: quien quería apuntar el número no lo veía. El
   * `tel:` sigue existiendo, en la página de contacto, que es a la que se llega para llamar.
   *
   * Sale del JSON y no repetido aquí, igual que la dirección: es un dato de negocio que ya cambió
   * una vez —el 310 pasó a ser 313— y lo que se verifica es que el pie publique **el mismo**.
   */
  it('publica el número de teléfono como texto, no como un enlace "Llamar"', async () => {
    await renderPie();

    expect(screen.getByText(es.pie.telefono)).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Llamar' })).toBeNull();
    expect(
      screen
        .queryAllByRole('link')
        .some((enlace) => (enlace.getAttribute('href') ?? '').startsWith('tel:')),
    ).toBe(false);
  });

  /**
   * WhatsApp sube del bloque de contacto a la columna de redes, con Facebook e Instagram, y es el
   * único de los tres sin `rel="me"`: `me` declara identidad y un enlace de chat no la declara.
   */
  it('enlaza las tres redes, y solo los dos perfiles declaran identidad', async () => {
    await renderPie();

    const whatsapp = screen.getByRole('link', { name: 'WhatsApp' });
    const facebook = screen.getByRole('link', { name: 'Facebook' });
    const instagram = screen.getByRole('link', { name: 'Instagram' });

    expect(whatsapp.getAttribute('href')).toBe(`https://wa.me/${es.pie.whatsapp_numero}`);
    expect(facebook.getAttribute('href')).toBe(es.pie.facebook_url);
    expect(instagram.getAttribute('href')).toBe(es.pie.instagram_url);

    expect(whatsapp.getAttribute('rel')).toBeNull();
    expect(facebook.getAttribute('rel')).toBe('me');
    expect(instagram.getAttribute('rel')).toBe('me');
  });

  // La dirección sale del JSON, no repetida aquí: es un dato de negocio que ya
  // cambió una vez (contact@ -> contacto@) y lo que hay que verificar es el
  // cableado del mailto:, no el valor.
  it('enlaza el correo con mailto:', async () => {
    await renderPie();

    const correo = es.pie.correo;

    expect(screen.getByRole('link', { name: correo }).getAttribute('href')).toBe(
      `mailto:${correo}`,
    );
  });

  // El horario se publica porque es exigible: lo anunciado obliga. Y es el de los canales, no
  // el de un punto de venta — la prueba lo fija para que nadie lo mueva sin querer al retocar el
  // bloque de contacto.
  it('publica el horario de atención de los canales', async () => {
    await renderPie();

    expect(screen.getByText(es.pie.horario)).toBeTruthy();
    expect(screen.getByText(es.pie.direccion)).toBeTruthy();
  });

  it('muestra el año actual en el copyright', async () => {
    await renderPie();

    const anio = new Date().getFullYear();
    expect(screen.getByText(`© ${anio} Tecno Sport`)).toBeTruthy();
  });

  it('la primera columna lleva a la portada, al catálogo y al carrito', async () => {
    await renderPie();

    expect(screen.getByRole('link', { name: 'Portada' }).getAttribute('href')).toBe('/es');
    expect(screen.getByRole('link', { name: 'Catálogo' }).getAttribute('href')).toBe(
      '/es/productos',
    );
    expect(screen.getByRole('link', { name: 'Carrito' }).getAttribute('href')).toBe('/es/carrito');
  });

  // Es la única entrada al panel desde la vitrina, y tiene que estar sin sesión:
  // el enlace del encabezado solo aparece cuando `esAdmin()` ya es verdadero, así
  // que no sirve para llegar a iniciar sesión. Sin este, el panel —y con él la
  // pantalla de captura 360— solo se alcanzaba tecleando la ruta.
  it('el pie lleva al panel administrativo aunque no haya sesión', async () => {
    await renderPie();

    expect(screen.getByRole('link', { name: 'Panel administrativo' }).getAttribute('href')).toBe(
      '/es/admin',
    );
  });

  /**
   * La segunda columna, que es nueva: las tres páginas a las que se llega cuando algo no está
   * claro. Dos de ellas no existían antes de esta misma tanda de cambios.
   */
  it.each([
    ['Preguntas frecuentes', '/es/ayuda/preguntas-frecuentes'],
    ['Estado del pedido', '/es/checkout/estado'],
    ['Contáctanos', '/es/ayuda/contacto'],
  ])('la columna de ayuda lleva a %s', async (etiqueta, destino) => {
    await renderPie();

    expect(screen.getByRole('link', { name: etiqueta }).getAttribute('href')).toBe(destino);
  });

  // La ley pide la política de datos publicada y enlazada en el pie
  // (docs/08-seguridad-legal.md). Una página legal a la que solo se llega tecleando la ruta no
  // está publicada, está escondida — la misma regla de cierre de fase que ya costó una corrección.
  it.each([
    ['Términos y condiciones', '/es/legales/terminos'],
    ['Política de datos', '/es/legales/privacidad'],
    ['Cookies', '/es/legales/cookies'],
  ])('enlaza %s en la franja final', async (etiqueta, destino) => {
    await renderPie();

    const enlace = screen.getByRole('link', { name: etiqueta });

    expect(enlace.getAttribute('href')).toBe(destino);
  });

  /**
   * La franja final lleva el alternador de tema, como el pie de referencia. Se comprueba por su
   * nombre accesible y no por el selector del componente: lo que importa es que quien navega con
   * lector de pantalla encuentre el control, no qué etiqueta lo pinta.
   */
  it('la franja final lleva el alternador de tema', async () => {
    await renderPie();

    expect(screen.getByRole('button', { name: 'Cambiar el tema' })).toBeTruthy();
  });

  /**
   * La casilla se quitó a petición. Esta prueba fija la decisión y, sobre todo, deja escrita su
   * consecuencia: quien **no** tenga la preferencia puesta en su sistema operativo se queda sin
   * forma de pedir menos movimiento desde el sitio. La regla de `prefers-reduced-motion` de
   * `tokens.css` sigue intacta y sigue apagando el carrusel, el brillo de carga y el anillo; lo que
   * desapareció es el interruptor propio. Si alguien lo devuelve, que sea a sabiendas.
   */
  it('ya no ofrece la casilla de reducir movimiento', async () => {
    await renderPie();

    expect(screen.queryByRole('checkbox')).toBeNull();
  });
});
