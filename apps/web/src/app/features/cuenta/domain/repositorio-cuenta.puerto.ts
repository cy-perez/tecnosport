import { InjectionToken } from '@angular/core';

export interface RepositorioCuenta {
  /** `POST /auth/registro`: nunca abre sesión — la cuenta nace sin verificar. */
  /**
   * `autorizaDatos` va explícito y no por omisión: el servidor lo exige (Ley 1581 de 2012) y un
   * cuerpo que omita el campo lo rechaza entero, así que no hay valor implícito que valga.
   */
  registrar(correo: string, clave: string, autorizaDatos: boolean): Promise<void>;
  /** `POST /auth/verificacion`. */
  verificarCorreo(token: string): Promise<void>;
  /** `POST /auth/recuperacion`: siempre resuelve, exista o no una cuenta con ese correo — ni el
   * frontend puede distinguir, ni debe (docs/08-seguridad-legal.md). */
  solicitarRecuperacion(correo: string): Promise<void>;
  /** `POST /auth/recuperacion/confirmar`. */
  restablecerClave(token: string, claveNueva: string): Promise<void>;
}

export const REPOSITORIO_CUENTA = new InjectionToken<RepositorioCuenta>('RepositorioCuenta');
