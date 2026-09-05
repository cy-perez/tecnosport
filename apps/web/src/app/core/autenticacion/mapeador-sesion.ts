import type { components } from '@tecnosport/contratos';
import { Rol, Sesion } from './sesion.model';

type SesionDto = components['schemas']['SesionRespuesta'];

const ROLES_VALIDOS: readonly Rol[] = ['CLIENTE', 'ADMIN'];

/** Un valor ausente o desconocido cae al rol menos privilegiado, nunca a
 * `ADMIN` — un DTO mal formado no puede terminar concediendo acceso de más. */
function aRol(valor: string | undefined): Rol {
  return (ROLES_VALIDOS as readonly string[]).includes(valor ?? '') ? (valor as Rol) : 'CLIENTE';
}

/** DTO generado -> modelo propio del front. Ningún componente ve la forma de la respuesta HTTP. */
export function aSesion(dto: SesionDto): Sesion {
  return {
    usuarioId: dto.usuarioId ?? '',
    rol: aRol(dto.rol),
    accessToken: dto.accessToken ?? '',
  };
}
