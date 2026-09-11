import { InjectionToken } from '@angular/core';
import { CotizacionEnvio, CotizarEnvioComando } from './envio.model';

export interface RepositorioEnvios {
  /**
   * `null` significa **sin cobertura**, que es una respuesta de negocio y no un
   * fallo: no hay transportadora que llegue a esa dirección hoy, y lo que
   * corresponde es ofrecer la recogida en el punto. El backend lo dice con un
   * 409 y el código `ENVIO_SIN_COBERTURA` (`docs/03-api.md`); cualquier otro
   * fallo sí se lanza, porque "no se pudo cotizar" y "no hay cómo enviar" no se
   * le cuentan igual a quien está comprando.
   */
  cotizar(comando: CotizarEnvioComando): Promise<CotizacionEnvio | null>;
}

export const REPOSITORIO_ENVIOS = new InjectionToken<RepositorioEnvios>('RepositorioEnvios');
