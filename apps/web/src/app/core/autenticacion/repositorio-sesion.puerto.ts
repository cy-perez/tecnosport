import { InjectionToken } from '@angular/core';
import { ResultadoInicioSesion, Sesion } from './sesion.model';

export interface RepositorioSesion {
  /**
   * `POST /auth/sesion`. Con la clave bien, la sesión abierta —o, si la cuenta tiene segundo factor
   * (ADR-0096), el desafío que hay que completar con {@link verificarSegundoFactor}—.
   */
  iniciarSesion(correo: string, clave: string): Promise<ResultadoInicioSesion>;
  /**
   * `POST /auth/segundo-factor/verificacion`: el segundo paso, con el código de la aplicación o uno
   * de respaldo. Rechaza con `CodigoSegundoFactorInvalidoError` si el código no sirve —el desafío
   * sigue vivo— y con `DesafioSegundoFactorVencidoError` si hay que volver a la clave.
   */
  verificarSegundoFactor(desafio: string, codigo: string): Promise<Sesion>;
  /**
   * `POST /auth/google` (ADR-0074): la misma sesión que la de la clave, abierta con la credencial
   * que entregó el botón de Google. `autorizaDatos` solo cuenta si la cuenta no existe todavía.
   */
  iniciarSesionConGoogle(credencial: string, autorizaDatos: boolean): Promise<Sesion>;
  /**
   * `POST /auth/clave`: cambia la clave de quien ya tiene sesión y devuelve una **nueva**, porque
   * el servidor revoca todas las anteriores. Es el único camino que no depende del correo.
   *
   * El token va como argumento y no lo pone un interceptor: `crearClienteAutenticado` lo tomaría
   * de `SesionStore`, y este puerto es justamente lo que `SesionStore` inyecta — la dependencia
   * sería circular. Y además ese cliente reintenta cualquier 401 tras refrescar, así que una
   * clave actual equivocada —que aquí responde 401— se enviaría dos veces y contaría doble contra
   * el límite de intentos del servidor.
   */
  cambiarClave(accessToken: string, claveActual: string, claveNueva: string): Promise<Sesion>;
  /** `null` sin una cookie de refresco válida — no es un error, es "sin sesión". */
  refrescar(): Promise<Sesion | null>;
  cerrarSesion(): Promise<void>;
}

export const REPOSITORIO_SESION = new InjectionToken<RepositorioSesion>('RepositorioSesion');
