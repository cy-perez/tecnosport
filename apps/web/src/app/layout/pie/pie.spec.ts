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
  /**
   * La columna de medios de pago. Lo que esta prueba protege no es el adorno sino <b>qué se
   * promete</b>: la lista tiene que ser la del checkout, y Addi tiene que seguir diciendo
   * "próximamente" mientras `MetodoPago` no lo tenga —`V61__sin_addi.sql` lo quitó a propósito—.
   * Anunciar como disponible un medio que el checkout no ofrece es lo que la Ley 1480 llama
   * información engañosa.
   */
  it('publica los medios de pago del checkout, y Addi como próximo', async () => {
    await renderPie();

    expect(screen.getByText(es.pie.pagos.titulo)).toBeTruthy();
    expect(screen.getByText(es.pie.pagos.visa)).toBeTruthy();
    expect(screen.getByText(es.pie.pagos.mastercard)).toBeTruthy();
    expect(screen.getByText(es.pie.pagos.american_express)).toBeTruthy();
    expect(screen.getByText(es.pie.pagos.pse)).toBeTruthy();
    expect(screen.getByText(es.pie.pagos.nequi)).toBeTruthy();
    expect(screen.getByText(es.pie.pagos.bancolombia)).toBeTruthy();
    expect(screen.getByText(es.pie.pagos.sistecredito)).toBeTruthy();
    expect(screen.getByText(es.pie.pagos.contraentrega)).toBeTruthy();
    expect(screen.getByText(es.pie.pagos.addi)).toBeTruthy();
    expect(es.pie.pagos.addi.toLowerCase()).toContain('próximamente');
    expect(en.pie.pagos.addi.toLowerCase()).toContain('coming soon');
  });

  /**
   * Los logos son decorativos: el nombre lo dice el texto de al lado, igual que en la columna de
   * redes. Un nombre accesible con la marca dentro los convertiría en una segunda copia del mismo
   * nombre para quien escucha.
   *
   * <p>Eran siete `<img>` de `assets/pagos/` hasta el 28 de septiembre de 2026, y desde entonces
   * son SVG en línea —`ts-logo-pago`— porque un `<img>` no hereda `currentColor` y los logos
   * pasaron a monocromo. Lo que la prueba cuida es lo de siempre: que sigan siendo los que son y
   * que ninguno hable.
   *
   * <p><b>Son diez y los medios son once</b>: la contraentrega no tiene logo, va con su nombre a la
   * vista. Ese uno de diferencia es justo lo que fija la prueba de más abajo.
   */
  it('los logos de pago son decorativos, no el nombre del medio', async () => {
    const { container } = await renderPie();

    const logos = [...container.querySelectorAll('ts-logo-pago svg')];
    expect(logos.length).toBe(10);
    expect(logos.every((svg) => svg.getAttribute('aria-hidden') === 'true')).toBe(true);
    expect(logos.every((svg) => svg.getAttribute('focusable') === 'false')).toBe(true);
  });

  /**
   * <b>Quitar el texto de la vista no puede dejar la columna muda.</b> Desde el 28 de septiembre de
   * 2026 los medios con logo van sin nombre visible —el logo se explica solo— y el logo es
   * `aria-hidden`, así que sin el `sr-only` de al lado quien escucha no oiría absolutamente nada:
   * diez elementos de lista vacíos bajo el título "Medios de pago".
   *
   * <p>Esta prueba es la que sostiene esa mitad. Y de paso sostiene lo de Addi: su nombre accesible
   * sigue diciendo "próximamente" aunque el texto ya no se vea.
   */
  it('cada logo sin texto visible conserva su nombre para el lector de pantalla', async () => {
    const { container } = await renderPie();

    const nombres = [...container.querySelectorAll('ts-logo-pago')].map(
      (logo) => logo.parentElement?.querySelector('.sr-only')?.textContent?.trim() ?? '',
    );

    expect(nombres).toEqual([
      es.pie.pagos.visa,
      es.pie.pagos.mastercard,
      es.pie.pagos.american_express,
      es.pie.pagos.pse,
      es.pie.pagos.nequi,
      es.pie.pagos.bancolombia,
      es.pie.pagos.daviplata,
      es.pie.pagos.bbva,
      es.pie.pagos.sistecredito,
      es.pie.pagos.addi,
    ]);
  });

  /**
   * <b>Solo Mastercard trae color propio</b>, y lo que sostiene la regla es la lista, no el cero.
   * El generador ya lo verifica al escribir `logos-pago.generado.ts`, y esto lo vuelve a mirar
   * sobre lo que de verdad se pinta: un `fill` o un `stroke` con un hex se vería de su color en una
   * franja grafito, y los tres logos negros no se verían en absoluto —que es justo el problema que
   * la pastilla tapaba antes—.
   *
   * <p><b>La excepción entró el 28 de septiembre de 2026 y está medida</b>: el dibujo de Mastercard
   * son dos círculos que se solapan, y aplanarlo a un color lo deja en dos manchas con el logotipo
   * de encima borrado. Se miró en el navegador antes de decidirlo. Lo que esta prueba vigila es que
   * la excepción siga siendo <i>una</i>: el día que un segundo logo se cuele a color, el nombre que
   * sobre aquí lo dice.
   *
   * <p>No sustituye a mirarlo en el navegador, y no puede: en jsdom un color es una cadena más, y
   * lo que un `currentColor` resuelve en cada tema no se sabe aquí (regla dura #8).
   */
  it('solo Mastercard lleva color propio, y el resto hereda currentColor', async () => {
    const { container } = await renderPie();

    const conColor = [...container.querySelectorAll('ts-logo-pago')]
      .filter(
        (logo) =>
          [...logo.querySelectorAll('[fill], [stroke]')]
            .flatMap((nodo) => [nodo.getAttribute('fill'), nodo.getAttribute('stroke')])
            .filter((valor): valor is string => valor !== null && valor !== 'currentColor').length >
          0,
      )
      .map((logo) => logo.parentElement?.querySelector('.sr-only')?.textContent?.trim() ?? '');

    expect(conColor).toEqual([es.pie.pagos.mastercard]);
  });

  /**
   * <b>La contraentrega es la única sin logo, y por eso la única con el nombre a la vista.</b> Va al
   * final, que es donde una excepción estorba menos.
   *
   * <p>Tuvo símbolo propio —dos manos y una caja en 512×512— unas horas del 28 de septiembre de
   * 2026, con el nombre al lado; se quitó a pedido.
   *
   * <p><b>Lo que la prueba cuida son las dos mitades de una misma regla</b>, porque ya no hay una
   * bandera que las ate: quien tiene logo calla —si no, la columna vuelve a ser la lista de nombres
   * que se quitó a propósito— y quien no lo tiene habla —si no, queda un elemento de lista vacío
   * que nadie ve ni oye—. Un `@if` invertido en la plantilla rompe exactamente una de las dos.
   */
  it('solo el medio sin logo lleva su nombre a la vista, y va de último', async () => {
    const { container } = await renderPie();

    const filas = [...container.querySelectorAll('ul[aria-labelledby="pie-columna-pagos"] > li')];

    const alaVista = filas.filter((fila) => fila.querySelector('span:not(.sr-only)'));
    expect(alaVista.length).toBe(1);
    expect(alaVista[0]).toBe(filas.at(-1));
    expect(alaVista[0]?.textContent?.trim()).toBe(es.pie.pagos.contraentrega);

    // Y la otra mitad: el único que habla es también el único sin logo.
    expect(alaVista[0]?.querySelector('ts-logo-pago')).toBeNull();
    expect(filas.filter((fila) => !fila.querySelector('ts-logo-pago'))).toEqual(alaVista);
  });

  /**
   * <b>Todos los logos se pegan al borde izquierdo de su caja, y no hay excepciones.</b> Una lista
   * se lee por su borde izquierdo, y con el centrado que hubo hasta el 28 de septiembre de 2026 ese
   * borde era un zigzag que no había decidido nadie: cada dibujo se apartaba la mitad de lo que le
   * sobraba en la caja de 64 px —Visa 14, Daviplata 17,4, Mastercard 12, BBVA 0—, según su
   * relación de aspecto.
   *
   * <p>Daviplata estuvo centrado unos minutos de ese día, por ser el dibujo más cuadrado de los
   * diez —50×41, y a ras deja 34,7 px vacíos a su derecha—, y se descartó.
   *
   * <p><b>Lo que esta prueba cuida es que no vuelva a aparecer una excepción</b>, no que se vea
   * bien: lo que un `preserveAspectRatio` hace con un dibujo no se sabe en jsdom, donde es una
   * cadena más (regla dura #8). Los píxeles de arriba salen del navegador.
   */
  it('todos los logos dibujan a ras del borde izquierdo de su caja', async () => {
    const { container } = await renderPie();

    const encuadres = [...container.querySelectorAll('ts-logo-pago svg')].map((svg) =>
      svg.getAttribute('preserveAspectRatio'),
    );

    expect(encuadres.length).toBe(10);
    expect(encuadres.every((encuadre) => encuadre === 'xMinYMid meet')).toBe(true);
  });

  /**
   * El buzón va en "El sitio" y **no** entre los enlaces de ayuda: cerca de "Contáctanos"
   * invitaría a mandar por ahí un reclamo, que tiene plazo legal y en el buzón no corre.
   */
  it('enlaza el buzón de sugerencias desde la columna del sitio', async () => {
    await renderPie();

    const enlace = screen.getByRole('link', { name: es.pie.sugerencias });
    expect(enlace.getAttribute('href')).toBe('/es/ayuda/sugerencias');
  });

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
