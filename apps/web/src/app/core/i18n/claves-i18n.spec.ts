import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import enCatalogo from '../../../assets/i18n/scopes/catalogo/en.json';
import esCatalogo from '../../../assets/i18n/scopes/catalogo/es.json';

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
});
