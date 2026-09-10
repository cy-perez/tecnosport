import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * El orden de los middleware de `server.ts`, comprobado sobre la fuente y no sobre una petición.
 *
 * No es un capricho: montar el motor de Angular en Vitest exige el manifiesto que solo existe
 * después de construir, y las pruebas corren antes del build en `npm run verificar`. Lo que sí se
 * puede afirmar aquí es la única parte que se rompe en silencio — el **orden**. Que las respuestas
 * salgan de verdad comprimidas lo comprueba el flujo de despliegue contra el servicio ya
 * desplegado, que es donde esa afirmación significa algo.
 *
 * Mismo criterio que `tools/verificar-capas.mjs`: un invariante estructural se vigila donde se
 * puede vigilar, no donde queda más bonito.
 */
const fuente = readFileSync(join(import.meta.dirname, 'server.ts'), 'utf8');

describe('orden de los middleware del servidor', () => {
  const posicion = (patron: RegExp): number => fuente.search(patron);

  it('la compresión está montada', () => {
    expect(posicion(/^app\.use\(compression\(\)\);$/m)).toBeGreaterThan(-1);
  });

  // Si esto se invierte, el JSON del catálogo vuelve a viajar en claro y nadie se entera: el sitio
  // funciona igual, solo pesa el triple. `proxy-api.ts` borra `content-encoding` porque `fetch` ya
  // descomprimió el cuerpo, así que la respuesta del proxy sale sin codificar y solo este
  // middleware, puesto antes, puede volver a comprimirla.
  it('la compresión va antes del proxy de /api', () => {
    expect(posicion(/^app\.use\(compression\(\)\);$/m)).toBeLessThan(
      posicion(/app\.use\('\/api', crearProxyApi/),
    );
  });

  // Los estáticos son la mayor parte del peso: 243 kB de `main.js` contra 73 kB comprimido.
  it('la compresión va antes de los estáticos', () => {
    expect(posicion(/^app\.use\(compression\(\)\);$/m)).toBeLessThan(
      posicion(/express\.static\(browserDistFolder/),
    );
  });
});
