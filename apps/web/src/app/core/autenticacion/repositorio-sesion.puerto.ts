import { InjectionToken } from '@angular/core';
import { Sesion } from './sesion.model';

export interface RepositorioSesion {
  iniciarSesion(correo: string, clave: string): Promise<Sesion>;
  /** `null` sin una cookie de refresco válida — no es un error, es "sin sesión". */
  refrescar(): Promise<Sesion | null>;
  cerrarSesion(): Promise<void>;
}

export const REPOSITORIO_SESION = new InjectionToken<RepositorioSesion>('RepositorioSesion');
