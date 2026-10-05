import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { cabeceraDeCache } from './cache-estaticos';

const raiz = join('/srv', 'browser');
const cabecera = (relativa: string) => cabeceraDeCache(join(raiz, relativa), raiz);

describe('cabeceraDeCache', () => {
  it('lo que lleva el hash en el nombre se guarda un año', () => {
    expect(cabecera('main-IZ72O7P5.js')).toContain('max-age=31536000');
    expect(cabecera('chunk-305WEk-I2.js')).toContain('max-age=31536000');
    expect(cabecera('styles-636DG2W7.css')).toContain('max-age=31536000');
    expect(cabecera('media/archivo-variable-MSXXHINV.woff2')).toContain('max-age=31536000');
  });

  // El caso que se vio en dev: una clave nueva que el navegador no recibía.
  it('las traducciones se revalidan en cada uso', () => {
    expect(cabecera('assets/i18n/scopes/catalogo/es.json')).toBe('no-cache');
    expect(cabecera('assets/i18n/es.json')).toBe('no-cache');
  });

  it('lo que conserva su nombre entre despliegues se revalida', () => {
    for (const archivo of [
      'ngsw-worker.js',
      'safety-worker.js',
      'worker-basic.min.js',
      'manifest.webmanifest',
      'favicon.ico',
      'favicon-16x16.png',
      'assets/marca/logo.svg',
    ]) {
      expect(cabecera(archivo)).toBe('no-cache');
    }
  });

  // Un nombre con hash dentro de `assets/` no está: lo copia Angular tal cual, sin hash propio.
  it('un archivo de assets que parece un bundle no se trata como uno', () => {
    expect(cabecera('assets/chunk-ABCDEFGH.js')).toBe('no-cache');
  });
});

describe('server.ts usa la cabecera por archivo', () => {
  const fuente = readFileSync(join(import.meta.dirname, 'server.ts'), 'utf8');

  // Si `maxAge` vuelve, `express.static` pone su propio `Cache-Control` y el de un año para todo.
  it('los estáticos no fijan un maxAge global', () => {
    expect(fuente).not.toMatch(/maxAge:\s*'1y'/);
    expect(fuente).toMatch(/setHeaders:.*cabeceraDeCache/s);
  });
});
