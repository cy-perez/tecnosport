import { InjectionToken } from '@angular/core';
import { Sesion } from '../../../../core/autenticacion/sesion.model';
import { ActivacionIniciada, EstadoSegundoFactor, SesionConCodigos } from './segundo-factor.model';

/**
 * El segundo factor de la cuenta que tiene la sesión (ADR-0096), en `/api/v1/auth/segundo-factor`.
 *
 * Los rechazos que la pantalla tiene que distinguir llegan con tipo propio, igual que en el cambio
 * de clave: `ClaveActualIncorrectaError`, `CodigoSegundoFactorInvalidoError`,
 * `DemasiadosIntentosError` y `SesionExpiradaError`. Los cuatro 409 de estado —ya activo, no
 * iniciado, no activo, obligatorio— llegan como `ErrorHttp` con su `codigo`, y la pantalla los
 * traduce con `mensajeDeError` desde `admin.errores.*`.
 */
export interface RepositorioSegundoFactor {
  consultar(): Promise<EstadoSegundoFactor>;
  /** Pide la clave actual: quien tiene la sesión abierta no tiene por qué ser el dueño. */
  iniciarActivacion(claveActual: string): Promise<ActivacionIniciada>;
  confirmarActivacion(codigo: string): Promise<SesionConCodigos>;
  regenerarCodigos(claveActual: string, codigo: string): Promise<SesionConCodigos>;
  /** También revoca las sesiones y abre una nueva. */
  desactivar(claveActual: string, codigo: string): Promise<Sesion>;
}

export const REPOSITORIO_SEGUNDO_FACTOR = new InjectionToken<RepositorioSegundoFactor>(
  'RepositorioSegundoFactor',
);
