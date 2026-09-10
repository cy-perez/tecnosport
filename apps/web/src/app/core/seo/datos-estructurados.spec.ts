import {
  migasJsonLd,
  organizacionJsonLd,
  productoJsonLd,
  serializarJsonLd,
  sitioWebJsonLd,
} from './datos-estructurados';

const ORIGEN = 'https://tecnosport.co';

const NEGOCIO = {
  nombre: 'Tecno Sport',
  nit: 'NIT 1054994043-9',
  direccion: 'Cra. 26C #38B-31, Medellín, Antioquia',
  telefono: '+573104209655',
  correo: 'contacto@tecnosport.co',
};

const PRODUCTO = {
  nombre: 'Tenis trail runner',
  descripcion: 'Tejido técnico.',
  url: 'https://tecnosport.co/es/productos/tenis-trail-runner',
  imagen: 'https://imagenes.tecnosport.co/tenis.jpg',
  marca: 'Under Trail',
  precioMinimo: 189900,
  precioMaximo: 219900,
  moneda: 'COP',
  variantes: 3,
  hayExistencia: true,
};

describe('organizacionJsonLd', () => {
  it('publica los datos que la ley obliga a identificar', () => {
    const json = organizacionJsonLd(ORIGEN, NEGOCIO) as Record<string, unknown>;

    expect(json['@type']).toBe('Organization');
    expect(json['name']).toBe('Tecno Sport');
    expect(json['address']).toBe('Cra. 26C #38B-31, Medellín, Antioquia');
    expect(json['telephone']).toBe('+573104209655');
    expect(json['email']).toBe('contacto@tecnosport.co');
    expect(json['url']).toBe(ORIGEN);
  });

  // `taxID` quiere el identificador, no la etiqueta con la que el pie lo muestra.
  it('quita el prefijo NIT, que es de presentación y no del identificador', () => {
    const json = organizacionJsonLd(ORIGEN, NEGOCIO) as Record<string, unknown>;

    expect(json['taxID']).toBe('1054994043-9');
  });

  /**
   * El horario de atención sigue sin decidirse —los textos legales dejaron de prometerlo en vez de
   * publicar el marcador— y Google muestra `openingHours` en el resultado de búsqueda como si fuera
   * cierto.
   */
  it('no declara horario de atención, que es un dato de negocio que todavía no existe', () => {
    const json = organizacionJsonLd(ORIGEN, NEGOCIO) as Record<string, unknown>;

    expect(json['openingHours']).toBeUndefined();
    expect(json['openingHoursSpecification']).toBeUndefined();
  });
});

describe('sitioWebJsonLd', () => {
  // Si el parámetro de búsqueda cambiara de nombre en `query-params-filtro.ts`, esto quedaría
  // declarando un buscador que no responde.
  it('declara el buscador con el parámetro que la rejilla lee de verdad', () => {
    const json = sitioWebJsonLd(ORIGEN, 'es', 'Tecno Sport') as Record<string, never>;

    expect(json['potentialAction']['target']['urlTemplate']).toBe(
      'https://tecnosport.co/es/productos?texto={search_term_string}',
    );
  });

  it('apunta a la raíz del idioma activo', () => {
    expect((sitioWebJsonLd(ORIGEN, 'en', 'Tecno Sport') as Record<string, unknown>)['url']).toBe(
      'https://tecnosport.co/en',
    );
  });
});

describe('migasJsonLd', () => {
  it('numera los eslabones desde uno', () => {
    const json = migasJsonLd([
      { etiqueta: 'Portada', url: 'https://tecnosport.co/es' },
      { etiqueta: 'Catálogo', url: 'https://tecnosport.co/es/productos' },
      { etiqueta: 'Tenis trail runner' },
    ]) as Record<string, { position: number; name: string; item?: string }[]>;

    expect(json['itemListElement'].map((e) => e.position)).toEqual([1, 2, 3]);
    expect(json['itemListElement'][0].name).toBe('Portada');
  });

  // Enlazarse a sí misma no informa de nada.
  it('el último eslabón, que es la página actual, no lleva enlace', () => {
    const json = migasJsonLd([
      { etiqueta: 'Portada', url: 'https://tecnosport.co/es' },
      { etiqueta: 'Tenis trail runner' },
    ]) as Record<string, { item?: string }[]>;

    expect(json['itemListElement'][0].item).toBe('https://tecnosport.co/es');
    expect(json['itemListElement'][1].item).toBeUndefined();
  });

  it('sin eslabones no emite nada, en vez de una lista vacía', () => {
    expect(migasJsonLd([])).toBeNull();
  });
});

describe('productoJsonLd', () => {
  it('describe el producto con su marca y su imagen', () => {
    const json = productoJsonLd(PRODUCTO) as Record<string, never>;

    expect(json['@type']).toBe('Product');
    expect(json['name']).toBe('Tenis trail runner');
    expect(json['brand']['name']).toBe('Under Trail');
    expect(json['image']).toBe('https://imagenes.tecnosport.co/tenis.jpg');
  });

  /**
   * El precio vive en la variante y la ficha muestra "desde": un `Offer` único tendría que elegir
   * una y mentiría sobre las demás.
   */
  it('usa AggregateOffer con el rango real de precios de las variantes', () => {
    const json = productoJsonLd(PRODUCTO) as Record<string, never>;

    expect(json['offers']['@type']).toBe('AggregateOffer');
    expect(json['offers']['lowPrice']).toBe(189900);
    expect(json['offers']['highPrice']).toBe(219900);
    expect(json['offers']['offerCount']).toBe(3);
    expect(json['offers']['priceCurrency']).toBe('COP');
  });

  it('declara la disponibilidad real del producto', () => {
    const agotado = productoJsonLd({ ...PRODUCTO, hayExistencia: false }) as Record<string, never>;

    expect(agotado['offers']['availability']).toBe('https://schema.org/OutOfStock');
  });

  // Un producto sin variantes no tiene precio que ofrecer, y una oferta sin precio es inválida.
  it('omite la oferta entera cuando el producto no tiene precio', () => {
    const json = productoJsonLd({
      ...PRODUCTO,
      precioMinimo: null,
      precioMaximo: null,
      variantes: 0,
    }) as Record<string, unknown>;

    expect(json['offers']).toBeUndefined();
    expect(json['name']).toBe('Tenis trail runner');
  });

  it('omite la imagen y la descripción cuando no las hay, en vez de emitirlas vacías', () => {
    const json = productoJsonLd({ ...PRODUCTO, imagen: undefined, descripcion: '' }) as Record<
      string,
      unknown
    >;

    expect(json['image']).toBeUndefined();
    expect(json['description']).toBeUndefined();
  });
});

describe('serializarJsonLd', () => {
  /**
   * La prueba que importa de este archivo. El nombre y la descripción de un producto los escribe el
   * panel; una descripción con `</script>` cerraría la etiqueta en el HTML que sirve el SSR y todo
   * lo que viniera después se interpretaría como marcado.
   */
  it('escapa el menor-que para que un texto del panel no pueda cerrar el script', () => {
    const serializado = serializarJsonLd({
      name: 'Camiseta </script><img src=x onerror=alert(1)>',
    });

    expect(serializado).not.toContain('</script>');
    expect(serializado).not.toContain('<img');
    expect(serializado).toContain('\\u003c');
  });

  it('lo escapado sigue siendo el mismo texto al parsear', () => {
    const texto = 'Camiseta </script> azul';

    expect(JSON.parse(serializarJsonLd({ name: texto }))).toEqual({ name: texto });
  });
});
