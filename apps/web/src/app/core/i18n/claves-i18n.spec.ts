import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import enAdmin from '../../../assets/i18n/scopes/admin/en.json';
import esAdmin from '../../../assets/i18n/scopes/admin/es.json';
import enCarrito from '../../../assets/i18n/scopes/carrito/en.json';
import esCarrito from '../../../assets/i18n/scopes/carrito/es.json';
import enCatalogo from '../../../assets/i18n/scopes/catalogo/en.json';
import esCatalogo from '../../../assets/i18n/scopes/catalogo/es.json';
import enCheckout from '../../../assets/i18n/scopes/checkout/en.json';
import esCheckout from '../../../assets/i18n/scopes/checkout/es.json';
import enCaptura360 from '../../../assets/i18n/scopes/captura360/en.json';
import esCaptura360 from '../../../assets/i18n/scopes/captura360/es.json';
import enCuenta from '../../../assets/i18n/scopes/cuenta/en.json';
import esCuenta from '../../../assets/i18n/scopes/cuenta/es.json';

// docs/05-i18n.md: "es.json y en.json tienen exactamente las mismas claves.
// Hay una prueba que compara los árboles y falla si falta una." — esta es.
function clavesOrdenadas(objeto: Record<string, unknown>, prefijo = ''): string[] {
  return Object.entries(objeto)
    .flatMap(([clave, valor]) => {
      const ruta = prefijo ? `${prefijo}.${clave}` : clave;
      return typeof valor === 'object' && valor !== null
        ? clavesOrdenadas(valor as Record<string, unknown>, ruta)
        : [ruta];
    })
    .sort();
}

describe('claves de i18n', () => {
  it('es.json y en.json (raíz) tienen exactamente las mismas claves', () => {
    expect(clavesOrdenadas(es)).toEqual(clavesOrdenadas(en));
  });

  it('el scope catalogo tiene las mismas claves en los dos idiomas', () => {
    expect(clavesOrdenadas(esCatalogo)).toEqual(clavesOrdenadas(enCatalogo));
  });

  it('el scope carrito tiene las mismas claves en los dos idiomas', () => {
    expect(clavesOrdenadas(esCarrito)).toEqual(clavesOrdenadas(enCarrito));
  });

  it('el scope checkout tiene las mismas claves en los dos idiomas', () => {
    expect(clavesOrdenadas(esCheckout)).toEqual(clavesOrdenadas(enCheckout));
  });

  it('el scope admin tiene las mismas claves en los dos idiomas', () => {
    expect(clavesOrdenadas(esAdmin)).toEqual(clavesOrdenadas(enAdmin));
  });

  it('el scope cuenta tiene las mismas claves en los dos idiomas', () => {
    expect(clavesOrdenadas(esCuenta)).toEqual(clavesOrdenadas(enCuenta));
  });

  // `legales` y `captura360` faltaban desde que se crearon: la regla dice
  // "es.json y en.json tienen exactamente las mismas claves", y un scope que
  // nadie compara es un scope donde una clave se puede quedar sin traducir sin
  // que nada avise. Se notó al agregarles los metadatos de SEO.
  //
  // `legales` todavía no está aquí, y no por olvido: al agregarlo falla, porque
  // `comun.traduccion_cortesia` —"la versión en español es la que rige"— existe
  // solo en inglés y además ninguna plantilla la muestra. Taparlo escribiendo a
  // mano la frase en español sería redactar texto legal a mano; se arregla en su
  // propio commit, y esa prueba entra con él.
  it('el scope captura360 tiene las mismas claves en los dos idiomas', () => {
    expect(clavesOrdenadas(esCaptura360)).toEqual(clavesOrdenadas(enCaptura360));
  });
});
