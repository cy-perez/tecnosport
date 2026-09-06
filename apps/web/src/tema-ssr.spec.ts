import { conTemaAplicado, leerTema } from './tema-ssr';

describe('leerTema', () => {
  it('lee la cookie del tema entre otras cookies', () => {
    expect(leerTema('otra=1; ts-tema=oscuro; mas=2')).toBe('oscuro');
  });

  it('sin cookies no hay tema que aplicar', () => {
    expect(leerTema(undefined)).toBeUndefined();
    expect(leerTema('')).toBeUndefined();
  });

  it('"sistema" no se resuelve en el servidor: lo termina el script de index.html', () => {
    expect(leerTema('ts-tema=sistema')).toBeUndefined();
  });

  it('un valor inventado se ignora en vez de escribirse en el documento', () => {
    expect(leerTema('ts-tema=<script>')).toBeUndefined();
  });
});

describe('conTemaAplicado', () => {
  it('aplica el tema en español', () => {
    expect(conTemaAplicado('<!doctype html><html lang="es"><head>', 'oscuro')).toContain(
      '<html data-tema="oscuro" lang="es">',
    );
  });

  // La regresión que motivó extraer esto: el reemplazo iba contra la cadena
  // literal `<html lang="es">`, así que en inglés no coincidía y el tema
  // elegido no se aplicaba — medio sitio recargaba en claro.
  it('aplica el tema en inglés, no solo en español', () => {
    expect(conTemaAplicado('<!doctype html><html lang="en"><head>', 'oscuro')).toContain(
      '<html data-tema="oscuro" lang="en">',
    );
  });

  it('no toca nada más del documento', () => {
    const html = '<!doctype html><html lang="en"><head><title>Tecno Sport</title></head></html>';

    expect(conTemaAplicado(html, 'claro')).toBe(
      '<!doctype html><html data-tema="claro" lang="en"><head><title>Tecno Sport</title></head></html>',
    );
  });
});
