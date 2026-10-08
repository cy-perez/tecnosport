import type { LogoPago } from '../../../../shared/ui/icono/logos-pago.generado';
import {
  logo99Minutos,
  logoCoordinadora,
  logoEnvia,
  logoInterRapidisimo,
  logoServientrega,
} from '../../../../shared/ui/icono/logos-transportadora.generado';

/**
 * El logo de una transportadora a partir del nombre con que la devuelve Skydropx, o `null` si es
 * una que no tenemos: el botón la pinta con el camión genérico y su nombre, que es lo que de verdad
 * la identifica.
 *
 * Se compara sin tildes, mayúsculas ni espacios porque el nombre no es un código: la plataforma
 * dice "Inter Rapidísimo" o "Interrapidisimo" según el servicio, y "99 minutes" en inglés aunque la
 * empresa se llame 99 minutos (docs/13 §6).
 */
export function logoDeTransportadora(nombre: string): LogoPago | null {
  const clave = nombre
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');
  if (clave.startsWith('servientrega')) {
    return logoServientrega;
  }
  if (clave.startsWith('coordinadora')) {
    return logoCoordinadora;
  }
  if (clave.startsWith('interrapidisimo') || clave.startsWith('inter')) {
    return logoInterRapidisimo;
  }
  if (clave.startsWith('envia')) {
    return logoEnvia;
  }
  if (clave.startsWith('99')) {
    return logo99Minutos;
  }
  return null;
}
