import { aMilisegundos, leerToken } from './leer-token';

describe('leerToken', () => {
  afterEach(() => document.documentElement.style.removeProperty('--prueba-token'));

  it('devuelve el valor computado del token, sin espacios', () => {
    document.documentElement.style.setProperty('--prueba-token', ' 4000ms ');

    expect(leerToken(document, '--prueba-token')).toBe('4000ms');
  });

  it('devuelve la cadena vacía si el token no está', () => {
    expect(leerToken(document, '--prueba-token')).toBe('');
  });
});

describe('aMilisegundos', () => {
  it('lee milisegundos y segundos', () => {
    expect(aMilisegundos('4000ms')).toBe(4000);
    expect(aMilisegundos('1.5s')).toBe(1500);
  });

  it('no se inventa nada con lo que no es una duración', () => {
    expect(aMilisegundos('')).toBeNull();
    expect(aMilisegundos('4000')).toBeNull();
    expect(aMilisegundos('rápido')).toBeNull();
  });
});
