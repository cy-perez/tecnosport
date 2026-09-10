import { urlAbsoluta } from './enlaces-alternativos';

/**
 * Constructores de JSON-LD. Funciones puras que reciben **primitivas, nunca modelos de dominio**:
 * `core/` no puede conocer el `Producto` de `features/catalogo` —sería la dependencia al revés—, y
 * además así se prueban sin montar nada. Cada pantalla traduce lo suyo al pasar por aquí.
 */

/** Los datos del comerciante que la Ley 1480 obliga a publicar, y que el pie ya muestra. */
export interface DatosNegocio {
  readonly nombre: string;
  /** Tal como lo publica el pie, con su etiqueta: `NIT 1054994043-9`. */
  readonly nit: string;
  readonly direccion: string;
  /** En E.164, como lo guarda el pie: `+573104209655`. */
  readonly telefono: string;
  readonly correo: string;
}

export interface ProductoEstructurado {
  readonly nombre: string;
  readonly descripcion: string;
  /** Absoluta y canónica: la misma que declara `<link rel="canonical">`. */
  readonly url: string;
  readonly imagen?: string;
  readonly marca: string;
  readonly precioMinimo: number | null;
  readonly precioMaximo: number | null;
  readonly moneda: string;
  readonly variantes: number;
  readonly hayExistencia: boolean;
}

export interface EslabonDeRuta {
  readonly etiqueta: string;
  /** Absoluta. El último eslabón —la página actual— no la lleva. */
  readonly url?: string;
}

/**
 * `taxID` quiere el identificador, no la etiqueta con la que se muestra. El pie publica
 * `NIT 1054994043-9` porque así se lee; aquí sobra el prefijo.
 */
function soloElNit(nit: string): string {
  return nit.replace(/^\s*NIT\s*/i, '').trim();
}

/**
 * Quién vende. Va en la portada y no en todas las páginas: repetirlo en cada una no añade nada y
 * multiplica el sitio donde un dato puede quedar desactualizado.
 *
 * `openingHours` **no se emite**: el horario de atención sigue siendo un dato de negocio sin
 * decidir. Los textos legales ya no lo prometen —la promesa concreta se quitó y quedó el plazo de
 * respuesta, que se sostiene sin horario—, y aquí callarlo es lo mismo: declarar un horario
 * inventado en datos estructurados es peor que no declararlo, porque Google lo muestra en el
 * resultado de búsqueda como si fuera cierto.
 */
export function organizacionJsonLd(origen: string, negocio: DatosNegocio): object {
  return {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: negocio.nombre,
    url: origen,
    logo: urlAbsoluta(origen, '/icon-512.png'),
    taxID: soloElNit(negocio.nit),
    // Texto y no `PostalAddress`: la dirección se publica como una sola cadena en el pie, y
    // partirla por comas para rellenar `streetAddress`/`addressLocality` sería adivinar. Schema.org
    // admite `address` como texto, y un dato correcto sin estructurar vale más que uno estructurado
    // a partir de una suposición.
    address: negocio.direccion,
    telephone: negocio.telefono,
    email: negocio.correo,
  };
}

/**
 * El sitio, con su buscador. `potentialAction` declara algo que existe de verdad: la rejilla
 * filtra por texto con el parámetro `texto` (`domain/query-params-filtro.ts`). Si ese parámetro
 * cambiara de nombre, esto quedaría mintiendo.
 */
export function sitioWebJsonLd(origen: string, idioma: string, nombre: string): object {
  const catalogo = urlAbsoluta(origen, `/${idioma}/productos`);
  return {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: nombre,
    url: urlAbsoluta(origen, `/${idioma}`),
    inLanguage: idioma,
    potentialAction: {
      '@type': 'SearchAction',
      target: {
        '@type': 'EntryPoint',
        urlTemplate: `${catalogo}?texto={search_term_string}`,
      },
      'query-input': 'required name=search_term_string',
    },
  };
}

/** La ruta de migas que la pantalla ya pinta, en la forma que entiende un buscador. */
export function migasJsonLd(eslabones: readonly EslabonDeRuta[]): object | null {
  if (eslabones.length === 0) {
    return null;
  }
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: eslabones.map((eslabon, indice) => ({
      '@type': 'ListItem',
      position: indice + 1,
      name: eslabon.etiqueta,
      // El último eslabón es la página actual y no lleva `item`: enlazarse a sí misma no informa.
      ...(eslabon.url ? { item: eslabon.url } : {}),
    })),
  };
}

/**
 * La ficha de producto.
 *
 * **`AggregateOffer` y no una lista de `Offer`**: el precio de este catálogo vive en la variante, y
 * la ficha muestra "desde $X" precisamente porque un producto puede tener varias. Un `Offer` único
 * tendría que elegir una variante y mentiría sobre las demás; una lista de `Offer` obligaría a
 * decidir qué SKU es el canónico. `AggregateOffer` dice exactamente lo que la página dice.
 *
 * La disponibilidad es la del producto —¿queda algo de algo?—, no la de una variante: es la misma
 * pregunta que responde el botón de agregar al carrito.
 */
export function productoJsonLd(producto: ProductoEstructurado): object {
  return {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: producto.nombre,
    ...(producto.descripcion ? { description: producto.descripcion } : {}),
    ...(producto.imagen ? { image: producto.imagen } : {}),
    brand: { '@type': 'Brand', name: producto.marca },
    ...(producto.precioMinimo === null || producto.precioMaximo === null
      ? {}
      : {
          offers: {
            '@type': 'AggregateOffer',
            url: producto.url,
            priceCurrency: producto.moneda,
            lowPrice: producto.precioMinimo,
            highPrice: producto.precioMaximo,
            offerCount: producto.variantes,
            availability: producto.hayExistencia
              ? 'https://schema.org/InStock'
              : 'https://schema.org/OutOfStock',
          },
        }),
  };
}

/**
 * Serializa un objeto para meterlo dentro de un `<script>`.
 *
 * **Escapar `<` no es cosmético.** El nombre y la descripción de un producto los escribe el panel,
 * y una descripción que contuviera `</script>` cerraría la etiqueta en el HTML que sirve el SSR:
 * todo lo que viniera después se interpretaría como marcado, que es una inyección de scripts en
 * regla. Poner el JSON con `textContent` no protege de esto —protege del parseo en el navegador,
 * no de la serialización del servidor—, así que se escapa aquí, en el único sitio por el que
 * pasan todos.
 */
export function serializarJsonLd(objeto: object): string {
  return JSON.stringify(objeto).replace(/</g, '\\u003c');
}
