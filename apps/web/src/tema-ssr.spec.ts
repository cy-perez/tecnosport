import { conTemaAplicado, leerTema } from './tema-ssr';

describe('leerTema', () => {
  it('lee la cookie del tema entre otras cookies', () => {
    expect(leerTema('otra=1; ts-tema=oscuro; mas=2')).toBe('oscuro');
  });

  it('sin cookies no hay tema que aplicar', () => {
    expect(leerTema(undefined)).toBeUndefined();
    expect(leerTema('')).toBeUndefined();
  });

  // La cookie dura un año, así que quien eligió "Sistema" cuando esa opción
  // existía la sigue trayendo. No se migra ni se limpia: se ignora, el script
  // en línea resuelve por `prefers-color-scheme` —que es lo que esa persona
  // pidió— y el primer clic en el botón la reemplaza por un tema válido.
  it('la cookie "sistema", de cuando había tres opciones, se ignora', () => {
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
