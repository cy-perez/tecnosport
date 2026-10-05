import { InjectionToken } from '@angular/core';
import { Carrito, CarritoCotizado } from './carrito.model';

export interface RepositorioCarrito {
  crear(): Promise<Carrito>;
  /** `null` si el id guardado ya no existe en el servidor (carrito vencido, base reiniciada). */
  ver(carritoId: string): Promise<Carrito | null>;
  agregarLinea(carritoId: string, varianteId: string, cantidad: number): Promise<Carrito>;
  actualizarCantidad(carritoId: string, lineaId: string, cantidad: number): Promise<Carrito>;
  eliminarLinea(carritoId: string, lineaId: string): Promise<Carrito>;
  /** Los precios de hoy del servidor; `null` si el carrito ya no existe. */
  cotizar(carritoId: string): Promise<CarritoCotizado | null>;
}

export const REPOSITORIO_CARRITO = new InjectionToken<RepositorioCarrito>('RepositorioCarrito');
