import { InjectionToken } from '@angular/core';
import { CotizarEnvioComando, ResultadoCotizacion } from './envio.model';

export interface RepositorioEnvios {
  /**
   * Nunca lanza por las dos respuestas de negocio, que no son fallos: **sin cobertura** —no hay
   * transportadora que llegue a esa dirección hoy— y **artículo no asegurable** —algo del carrito
   * vale más de lo que la transportadora responde (`ADR-0036`)—. Las dos terminan en la recogida
   * en el punto y se cuentan distinto, porque una se arregla cambiando la dirección y la otra no
   * se arregla de ninguna manera.
   *
   * El backend las dice con un 409 y los códigos `ENVIO_SIN_COBERTURA` y `ARTICULO_NO_ASEGURABLE`
   * (`docs/03-api.md`). Cualquier otro fallo sí se lanza: "no se pudo cotizar" y "no hay cómo
   * enviar" no se le cuentan igual a quien está comprando.
   */
  cotizar(comando: CotizarEnvioComando): Promise<ResultadoCotizacion>;
}

export const REPOSITORIO_ENVIOS = new InjectionToken<RepositorioEnvios>('RepositorioEnvios');
