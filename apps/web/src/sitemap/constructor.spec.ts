import { construirRobots, construirSitemap } from './constructor';

const ORIGEN = 'https://tecnosport.co';

describe('construirSitemap', () => {
  it('emite una entrada por idioma de cada página', () => {
    const xml = construirSitemap(ORIGEN, [{ ruta: '' }, { ruta: '/productos' }]);

    expect(xml).toContain('<loc>https://tecnosport.co/es</loc>');
    expect(xml).toContain('<loc>https://tecnosport.co/en</loc>');
    expect(xml).toContain('<loc>https://tecnosport.co/es/productos</loc>');
    expect(xml).toContain('<loc>https://tecnosport.co/en/productos</loc>');
    expect(xml.match(/<url>/g)).toHaveLength(4);
  });

  // Si el sitemap declarara alternativas distintas de las del `<head>`, el rastreador recibiría dos
  // versiones contradictorias del mismo hecho. Las dos salen de `enlacesAlternativos`.
  it('cada entrada declara los dos idiomas y x-default', () => {
    const xml = construirSitemap(ORIGEN, [{ ruta: '/legales/terminos' }]);

    expect(xml).toContain(
      '<xhtml:link rel="alternate" hreflang="es-CO" href="https://tecnosport.co/es/legales/terminos"/>',
    );
    expect(xml).toContain(
      '<xhtml:link rel="alternate" hreflang="en" href="https://tecnosport.co/en/legales/terminos"/>',
    );
    expect(xml).toContain(
      '<xhtml:link rel="alternate" hreflang="x-default" href="https://tecnosport.co/es/legales/terminos"/>',
    );
  });

  it('escribe lastmod solo cuando se conoce', () => {
    const xml = construirSitemap(ORIGEN, [
      { ruta: '/productos/tenis', lastmod: '2026-03-04T15:30:00Z' },
      { ruta: '/productos/morral' },
    ]);

    expect(xml).toContain('<lastmod>2026-03-04T15:30:00Z</lastmod>');
    expect(xml.match(/<lastmod>/g)).toHaveLength(2); // solo las dos entradas de /tenis
  });

  // Un sitemap mal formado no se ignora a medias: se descarta entero. Una URL rara no puede
  // tumbar las demás.
  it('escapa los caracteres que romperían el XML', () => {
    const xml = construirSitemap(ORIGEN, [{ ruta: '/productos/a&b' }]);

    expect(xml).toContain('a&amp;b');
    expect(xml).not.toMatch(/a&b/);
  });

  it('es un documento XML bien formado, con la declaración y el espacio de nombres', () => {
    const xml = construirSitemap(ORIGEN, [{ ruta: '' }]);

    expect(xml.startsWith('<?xml version="1.0" encoding="UTF-8"?>\n')).toBe(true);
    expect(xml).toContain('xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"');
    expect(xml).toContain('xmlns:xhtml="http://www.w3.org/1999/xhtml"');
    expect(xml.trimEnd().endsWith('</urlset>')).toBe(true);
    expect(() => new DOMParser().parseFromString(xml, 'application/xml')).not.toThrow();
  });

  it('un catálogo vacío deja un sitemap válido con las páginas fijas', () => {
    const xml = construirSitemap(ORIGEN, [{ ruta: '' }]);

    expect(xml.match(/<url>/g)).toHaveLength(2);
    expect(xml).toContain('</urlset>');
  });

  it('no pasa del tope de URL que admite el formato', () => {
    const muchas = Array.from({ length: 50_010 }, (_, i) => ({ ruta: `/productos/p${i}` }));

    const xml = construirSitemap(ORIGEN, muchas);

    // 50 000 páginas × 2 idiomas.
    expect(xml.match(/<url>/g)).toHaveLength(100_000);
  });
});

describe('construirRobots', () => {
  it('apunta al sitemap con la URL absoluta del sitio', () => {
    expect(construirRobots(ORIGEN)).toContain('Sitemap: https://tecnosport.co/sitemap.xml');
  });

  it('prohíbe el panel en los dos idiomas, que es lo único que el SSR no puede marcar', () => {
    const robots = construirRobots(ORIGEN);

    expect(robots).toContain('Disallow: /es/admin');
    expect(robots).toContain('Disallow: /en/admin');
  });

  /**
   * La prueba que protege de un error clásico: prohibir el rastreo de una página con `noindex`
   * impide que el rastreador lea ese `noindex`, y la URL puede acabar indexada igual y sin forma
   * de sacarla. Para no aparecer hay que dejar entrar.
   */
  it('no prohíbe el carrito, el checkout ni la cuenta, que ya salen con noindex', () => {
    const robots = construirRobots(ORIGEN);

    expect(robots).not.toContain('carrito');
    expect(robots).not.toContain('checkout');
    expect(robots).not.toContain('cuenta');
  });
});
