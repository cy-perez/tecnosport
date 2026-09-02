import { urlEnOtroIdioma } from './idioma.servicio';

describe('urlEnOtroIdioma', () => {
  it('reemplaza el prefijo de idioma manteniendo el resto de la ruta', () => {
    expect(urlEnOtroIdioma('/es/productos', 'en')).toBe('/en/productos');
  });

  it('conserva los parámetros de consulta', () => {
    expect(urlEnOtroIdioma('/es/productos?categoria=bolsos&tamano=8', 'en')).toBe(
      '/en/productos?categoria=bolsos&tamano=8',
    );
  });

  it('funciona con la raíz de un idioma sin más segmentos', () => {
    expect(urlEnOtroIdioma('/es', 'en')).toBe('/en');
  });

  it('funciona con rutas más profundas', () => {
    expect(urlEnOtroIdioma('/en/productos/camiseta-running-dry-fit', 'es')).toBe(
      '/es/productos/camiseta-running-dry-fit',
    );
  });
});
