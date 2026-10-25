import { InjectionToken } from '@angular/core';
import { EnlaceDeCompraAdmin } from './enlace-de-compra-admin.model';

export interface RepositorioEnlacesDeCompraAdmin {
  /** Los de la última semana, los más nuevos primero. */
  listar(): Promise<EnlaceDeCompraAdmin[]>;
  /** El proveedor confirmó el color: aparta una unidad y devuelve el enlace para mandar. */
  generar(varianteId: string): Promise<EnlaceDeCompraAdmin>;
  /** La unidad apartada vuelve a quedar libre. */
  anular(enlaceId: string): Promise<EnlaceDeCompraAdmin>;
  /**
   * La paleta oficial del modelo. Devuelve cuántas variantes nacieron para que cada color se pueda
   * elegir en cada configuración que ya se vende.
   */
  definirPaleta(productoId: string, colores: readonly string[]): Promise<number>;
}

export const REPOSITORIO_ENLACES_DE_COMPRA_ADMIN =
  new InjectionToken<RepositorioEnlacesDeCompraAdmin>('RepositorioEnlacesDeCompraAdmin');
