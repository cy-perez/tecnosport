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
import enLegales from '../../../assets/i18n/scopes/legales/en.json';
import esLegales from '../../../assets/i18n/scopes/legales/es.json';

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
  // Al agregar el de `legales` falló de inmediato, con un hallazgo que no era de
  // traducción sino legal: `comun.traduccion_cortesia` existía solo en inglés y
  // ninguna plantilla la mostraba. Es la nota de que la versión en castellano es
  // la que rige (Ley 1480 de 2011, arts. 23 y 37.1), o sea justo lo que impide
  // que la versión inglesa se lea como un segundo contrato.
  it('el scope legales tiene las mismas claves en los dos idiomas', () => {
    expect(clavesOrdenadas(esLegales)).toEqual(clavesOrdenadas(enLegales));
  });

  it('el scope captura360 tiene las mismas claves en los dos idiomas', () => {
    expect(clavesOrdenadas(esCaptura360)).toEqual(clavesOrdenadas(enCaptura360));
  });
});
