import { aSesion } from './mapeador-sesion';

describe('aSesion', () => {
  it('mapea el DTO completo al modelo de dominio', () => {
    const sesion = aSesion({ usuarioId: 'usuario-1', rol: 'ADMIN', accessToken: 'jwt.valido' });

    expect(sesion).toEqual({ usuarioId: 'usuario-1', rol: 'ADMIN', accessToken: 'jwt.valido' });
  });

  it('un rol desconocido o ausente cae a CLIENTE, nunca a ADMIN', () => {
    expect(aSesion({ usuarioId: 'usuario-1', accessToken: 'jwt' }).rol).toBe('CLIENTE');
    expect(aSesion({ usuarioId: 'usuario-1', rol: 'SUPERUSUARIO', accessToken: 'jwt' }).rol).toBe('CLIENTE');
  });

  it('campos ausentes se rellenan con valores por defecto, nunca undefined', () => {
    expect(aSesion({})).toEqual({ usuarioId: '', rol: 'CLIENTE', accessToken: '' });
  });
});
