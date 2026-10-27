import type { components } from '@tecnosport/contratos';
import { ResultadoInicioSesion, Rol, Sesion } from './sesion.model';

type SesionDto = Partial<components['schemas']['SesionRespuesta']>;
type DesafioDto = components['schemas']['DesafioSegundoFactorRespuesta'];

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
    // Solo `true` restringe: un campo ausente en una respuesta vieja no puede encerrar al panel.
    // Restringir de más tampoco protegería nada, porque quien decide el alcance es el token.
    segundoFactorPorActivar: dto.segundoFactorPorActivar === true,
  };
}

/** El 202 del primer paso: el desafío que el segundo paso tiene que presentar con el código. */
export function aDesafio(dto: DesafioDto): ResultadoInicioSesion {
  return { tipo: 'segundo-factor', desafio: dto.desafio, venceEn: dto.venceEn };
}
