import { defineConfig } from 'vitest/config';

/**
 * Solo el tiempo máximo por prueba. El resto de la configuración la genera el constructor de
 * Angular (`@angular/build:unit-test`) y aquí no se toca: `runnerConfig` en `angular.json` hace
 * que este archivo se lea encima de la suya.
 *
 * Existe porque el valor por omisión de Vitest son 5 s y **la suite ya roza ese techo en esta
 * máquina**: `construirSitemap > no pasa del tope de URL que admite el formato` tarda 4,7 s
 * —construye las 50.000 URL del límite del formato, no puede hacer menos— y las comprobaciones
 * de `axe` van entre 2 y 3,3 s. Con la máquina ocupada, seis pruebas se cayeron por tiempo sin
 * que nada estuviera roto. Un ejecutor de integración continua tiene la mitad de núcleos que
 * este equipo: con 5 s, el guardián nuevo habría nacido intermitente, y a un guardián
 * intermitente se le deja de creer en la segunda semana.
 *
 * 30 s no esconde nada: ninguna prueba sana se acerca. Lo que quita es el falso rojo por carga
 * de la máquina, que no dice nada sobre el código.
 */
export default defineConfig({
  test: {
    testTimeout: 30_000,
    hookTimeout: 30_000,
  },
});
