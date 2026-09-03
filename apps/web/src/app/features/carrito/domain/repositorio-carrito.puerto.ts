import { InjectionToken } from '@angular/core';
import { Carrito } from './carrito.model';

export interface RepositorioCarrito {
  crear(): Promise<Carrito>;
  /** `null` si el id guardado ya no existe en el servidor (carrito vencido, base reiniciada). */
  ver(carritoId: string): Promise<Carrito | null>;
  agregarLinea(carritoId: string, varianteId: string, cantidad: number): Promise<Carrito>;
  actualizarCantidad(carritoId: string, lineaId: string, cantidad: number): Promise<Carrito>;
  eliminarLinea(carritoId: string, lineaId: string): Promise<Carrito>;
}

export const REPOSITORIO_CARRITO = new InjectionToken<RepositorioCarrito>('RepositorioCarrito');
