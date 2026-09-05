import { InjectionToken } from '@angular/core';

export interface RepositorioCuenta {
  /** `POST /auth/registro`: nunca abre sesión — la cuenta nace sin verificar. */
  registrar(correo: string, clave: string): Promise<void>;
  /** `POST /auth/verificacion`. */
  verificarCorreo(token: string): Promise<void>;
  /** `POST /auth/recuperacion`: siempre resuelve, exista o no una cuenta con ese correo — ni el
   * frontend puede distinguir, ni debe (docs/08-seguridad-legal.md). */
  solicitarRecuperacion(correo: string): Promise<void>;
  /** `POST /auth/recuperacion/confirmar`. */
  restablecerClave(token: string, claveNueva: string): Promise<void>;
}

export const REPOSITORIO_CUENTA = new InjectionToken<RepositorioCuenta>('RepositorioCuenta');
