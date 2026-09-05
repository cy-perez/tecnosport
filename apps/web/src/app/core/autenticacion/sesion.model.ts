export type Rol = 'CLIENTE' | 'ADMIN';

export interface Sesion {
  readonly usuarioId: string;
  readonly rol: Rol;
  readonly accessToken: string;
}
