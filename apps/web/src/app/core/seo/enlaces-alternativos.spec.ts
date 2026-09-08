import {
  enlacesAlternativos,
  rutaCanonica,
  urlAbsoluta,
  urlDeRecursoAbsoluta,
} from './enlaces-alternativos';

describe('rutaCanonica', () => {
  it('deja intacta una ruta que ya es canónica', () => {
    expect(rutaCanonica('/es/productos/camiseta-running-dry-fit')).toBe(
      '/es/productos/camiseta-running-dry-fit',
    );
  });

  // La razón de existir de esta poda: sin ella, cada filtro, cada orden y cada
  // página del catálogo sería una URL canónica distinta con el mismo contenido.
  it('quita los parámetros de consulta', () => {
    expect(rutaCanonica('/es/productos?categoria=bolsos&orden=precio_asc')).toBe('/es/productos');
  });

  it('quita el fragmento', () => {
    expect(rutaCanonica('/es/legales/terminos#retracto')).toBe('/es/legales/terminos');
  });

  it('quita el fragmento aunque venga antes que la consulta', () => {
    expect(rutaCanonica('/es/productos#arriba?categoria=bolsos')).toBe('/es/productos');
  });

  it('quita la barra final, que haría de la misma página dos URL', () => {
    expect(rutaCanonica('/es/productos/')).toBe('/es/productos');
  });

  it('conserva la barra cuando la ruta es solo la barra', () => {
    expect(rutaCanonica('/')).toBe('/');
  });
});

describe('urlAbsoluta', () => {
  it('une origen y ruta', () => {
    expect(urlAbsoluta('https://tecnosport.co', '/es')).toBe('https://tecnosport.co/es');
  });

  it('no deja barra doble cuando el origen ya termina en barra', () => {
    expect(urlAbsoluta('https://tecnosport.co/', '/es')).toBe('https://tecnosport.co/es');
  });
});

describe('urlDeRecursoAbsoluta', () => {
  it('deja intacta una URL que ya es absoluta, como las del bucket', () => {
    expect(
      urlDeRecursoAbsoluta('https://tecnosport.co', 'https://imagenes.tecnosport.co/x.jpg'),
    ).toBe('https://imagenes.tecnosport.co/x.jpg');
  });

  it('completa una ruta relativa con el origen', () => {
    expect(urlDeRecursoAbsoluta('https://tecnosport.co', '/icon-512.png')).toBe(
      'https://tecnosport.co/icon-512.png',
    );
  });

  it('devuelve cadena vacía cuando no hay URL, para que quien llama pueda omitir la etiqueta', () => {
    expect(urlDeRecursoAbsoluta('https://tecnosport.co', undefined)).toBe('');
  });
});

describe('enlacesAlternativos', () => {
  const ORIGEN = 'https://tecnosport.co';

  it('declara los dos idiomas con el código que pide docs/05-i18n.md, más x-default', () => {
    expect(enlacesAlternativos(ORIGEN, '/es/productos')).toEqual([
      { hreflang: 'es-CO', href: 'https://tecnosport.co/es/productos' },
      { hreflang: 'en', href: 'https://tecnosport.co/en/productos' },
      { hreflang: 'x-default', href: 'https://tecnosport.co/es/productos' },
    ]);
  });

  it('da el mismo juego de enlaces se entre por el idioma que se entre', () => {
    expect(enlacesAlternativos(ORIGEN, '/en/productos')).toEqual(
      enlacesAlternativos(ORIGEN, '/es/productos'),
    );
  });

  // Si esto se rompiera, `hreflang` le prometería al rastreador una página que
  // el selector de idioma del encabezado no abre.
  it('apunta a la misma página en el otro idioma, no a la portada', () => {
    const alternativas = enlacesAlternativos(ORIGEN, '/es/productos/camiseta-running-dry-fit');
    expect(alternativas.find((a) => a.hreflang === 'en')?.href).toBe(
      'https://tecnosport.co/en/productos/camiseta-running-dry-fit',
    );
  });

  it('poda la consulta antes de armar las alternativas', () => {
    expect(enlacesAlternativos(ORIGEN, '/es/productos?categoria=bolsos')).toEqual(
      enlacesAlternativos(ORIGEN, '/es/productos'),
    );
  });

  it('x-default apunta al español, que es adonde redirige la raíz del sitio', () => {
    const alternativas = enlacesAlternativos(ORIGEN, '/en');
    expect(alternativas.find((a) => a.hreflang === 'x-default')?.href).toBe(
      'https://tecnosport.co/es',
    );
  });
});
