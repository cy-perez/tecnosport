import { InjectionToken } from '@angular/core';
import { Sesion } from './sesion.model';

export interface RepositorioSesion {
  iniciarSesion(correo: string, clave: string): Promise<Sesion>;
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
