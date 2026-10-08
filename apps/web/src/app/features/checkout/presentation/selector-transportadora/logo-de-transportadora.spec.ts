import {
  logo99Minutos,
  logoCoordinadora,
  logoEnvia,
  logoInterRapidisimo,
  logoServientrega,
} from '../../../../shared/ui/icono/logos-transportadora.generado';
import { logoDeTransportadora } from './logo-de-transportadora';

/**
 * Los nombres tal como los devuelve Skydropx (docs/13 §6). No es un código: la misma empresa llega
 * con y sin tilde, junta o separada, y 99 minutos llega en inglés.
 */
describe('logoDeTransportadora', () => {
  it.each([
    ['Servientrega', logoServientrega],
    ['Coordinadora', logoCoordinadora],
    ['Inter Rapidísimo', logoInterRapidisimo],
    ['Interrapidisimo', logoInterRapidisimo],
    ['Envía', logoEnvia],
    ['ENVIA', logoEnvia],
    ['99 minutes', logo99Minutos],
  ])('%s tiene su logo', (nombre, logo) => {
    expect(logoDeTransportadora(nombre)).toBe(logo);
  });

  /** Una que no tenemos se pinta con el camión genérico y su nombre, no con el logo de otra. */
  it('una transportadora desconocida no tiene logo', () => {
    expect(logoDeTransportadora('DHL Express')).toBeNull();
  });
});
