import { POLITICA_DE_CONTENIDO, POLITICA_DE_PERMISOS } from './cabeceras-seguridad';

function directivas(politica: string, separador: string): Map<string, string> {
  return new Map(
    politica.split(separador).map((parte) => {
      const limpia = parte.trim();
      const corte = limpia.search(/[ =]/);
      return [limpia.slice(0, corte), limpia.slice(corte + 1)];
    }),
  );
}

describe('POLITICA_DE_PERMISOS', () => {
  const permisos = directivas(POLITICA_DE_PERMISOS, ',');

  // El portapapeles es lo único que el sitio usa: el panel copia enlaces de compra y códigos de
  // respaldo.
  it('deja al propio sitio el portapapeles', () => {
    expect(permisos.get('clipboard-write')).toBe('(self)');
  });

  // La cámara, los sensores y la pantalla despierta eran del asistente de captura 360, retirado
  // en ADR-0097: sin él, dejarlos abiertos es superficie sin uso.
  it('apaga lo que el sitio no usa', () => {
    for (const funcion of [
      'camera',
      'accelerometer',
      'gyroscope',
      'magnetometer',
      'screen-wake-lock',
      'microphone',
      'geolocation',
      'payment',
      'usb',
      'display-capture',
    ]) {
      expect(permisos.get(funcion), funcion).toBe('()');
    }
  });

  it('nunca le abre una función a cualquier origen', () => {
    expect(POLITICA_DE_PERMISOS).not.toContain('*');
  });
});

describe('POLITICA_DE_CONTENIDO', () => {
  const contenido = directivas(POLITICA_DE_CONTENIDO, ';');

  it('no permite scripts en línea ni eval', () => {
    expect(contenido.get('script-src')).not.toContain('unsafe-inline');
    expect(contenido.get('script-src')).not.toContain('unsafe-eval');
    expect(contenido.get('object-src')).toBe("'none'");
    expect(contenido.get('base-uri')).toBe("'self'");
  });

  it('deja cargar el botón de Google y llamar al bucket de las imágenes', () => {
    expect(contenido.get('script-src')).toContain('https://accounts.google.com/gsi/client');
    expect(contenido.get('frame-src')).toContain('https://accounts.google.com/gsi/');
    expect(contenido.get('connect-src')).toContain('https://storage.googleapis.com');
  });

  it('nadie enmarca el sitio', () => {
    expect(contenido.get('frame-ancestors')).toBe("'none'");
  });
});
