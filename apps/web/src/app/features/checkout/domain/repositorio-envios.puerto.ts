import { InjectionToken } from '@angular/core';
import { CotizarEnvioComando, ResultadoCotizacion } from './envio.model';

export interface RepositorioEnvios {
  /**
   * Nunca lanza por las tres respuestas de negocio, que no son fallos: **sin cobertura** —no hay
   * transportadora que llegue a esa dirección hoy—, **artículo no asegurable** —algo del carrito
   * vale más de lo que la transportadora responde (`ADR-0036`)— y **cotización rechazada** —la
   * plataforma contestó que los datos de ese envío no le sirven—. Las tres terminan en la recogida
   * en el punto y se cuentan distinto, porque la primera se arregla cambiando la dirección y las
   * otras dos no se arreglan de ninguna manera.
   *
   * El backend las dice con un 409 y los códigos `ENVIO_SIN_COBERTURA`, `ARTICULO_NO_ASEGURABLE` y
   * `COTIZACION_RECHAZADA` (`docs/03-api.md`). Cualquier otro fallo sí se lanza: "no se pudo
   * cotizar" y "no hay cómo enviar" no se le cuentan igual a quien está comprando — y esa
   * diferencia es justo la que el 503 y el 409 marcan.
   */
  cotizar(comando: CotizarEnvioComando): Promise<ResultadoCotizacion>;
}

export const REPOSITORIO_ENVIOS = new InjectionToken<RepositorioEnvios>('RepositorioEnvios');
