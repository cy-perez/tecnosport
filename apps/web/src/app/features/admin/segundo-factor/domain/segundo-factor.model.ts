import { Sesion } from '../../../../core/autenticacion/sesion.model';

/** `GET /auth/segundo-factor`: si la cuenta tiene el factor y cuántos códigos de respaldo le quedan. */
export interface EstadoSegundoFactor {
  readonly activo: boolean;
  /** `null` mientras no está activo. */
  readonly activadoEn: string | null;
  readonly codigosDeRespaldoRestantes: number;
  /** El servidor lo exige al administrador: entonces no se puede desactivar. */
  readonly obligatorio: boolean;
}

/**
 * La clave TOTP recién generada, pendiente de confirmar. Se muestra **como texto**: sin QR, porque
 * pintarlo pediría una dependencia, y la clave en Base32 o la URI `otpauth://` se escriben o se
 * pegan en cualquier aplicación de autenticación (ADR-0096).
 */
export interface ActivacionIniciada {
  readonly secreto: string;
  readonly uri: string;
  readonly digitos: number;
  readonly periodoSegundos: number;
}

/**
 * Activar el factor y regenerar los códigos revocan todas las sesiones y abren una nueva: vuelve
 * la sesión que la pestaña tiene que adoptar, y los diez códigos de respaldo **en claro, una sola
 * vez** —el servidor solo guarda su huella—.
 */
export interface SesionConCodigos {
  readonly sesion: Sesion;
  readonly codigos: readonly string[];
}

/**
 * La clave en grupos de cuatro, que es como se copia a mano sin perderse: `JBSW Y3DP EHPK 3PXP`. Las
 * aplicaciones de autenticación ignoran los espacios.
 */
export function agruparDeACuatro(secreto: string): string {
  return (secreto.replace(/\s/g, '').match(/.{1,4}/g) ?? []).join(' ');
}

/** Los códigos de respaldo como texto para guardar: uno por línea, con un salto al final. */
export function codigosComoTexto(codigos: readonly string[]): string {
  return codigos.join('\n') + '\n';
}
