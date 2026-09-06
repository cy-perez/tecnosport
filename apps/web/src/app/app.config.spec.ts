import { Provider } from '@angular/core';
import { appConfig } from './app.config';
import { REPOSITORIO_SESION } from './core/autenticacion/repositorio-sesion.puerto';
import { REPOSITORIO_CARRITO } from './features/carrito/domain/repositorio-carrito.puerto';
import { REPOSITORIO_PAGOS } from './features/checkout/domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS } from './features/checkout/domain/repositorio-pedidos.puerto';

function tokensDeclarados(proveedores: readonly unknown[]): unknown[] {
  return proveedores.flatMap((proveedor) => {
    if (Array.isArray(proveedor)) {
      return tokensDeclarados(proveedor);
    }
    const posible = proveedor as Partial<Record<'provide', unknown>>;
    return posible && typeof posible === 'object' && 'provide' in posible ? [posible.provide] : [];
  });
}

/**
 * Un servicio `providedIn: 'root'` NO ve los proveedores de una ruta: se
 * construye en el inyector raíz. Cada puerto que consuma uno de esos stores
 * tiene que estar declarado aquí, no en el `providers` de su ruta.
 *
 * Encontrado a la mala: `REPOSITORIO_PEDIDOS` y `REPOSITORIO_PAGOS` vivían solo
 * en `checkout.routes.ts` mientras `CheckoutStore` era de raíz, así que todas
 * las pantallas del checkout morían con NG0201 antes de pintar nada. Ninguna
 * prueba lo atrapaba porque cada spec provee sus propios dobles.
 */
describe('appConfig', () => {
  it('provee en la raíz los puertos que consumen los stores de raíz', () => {
    const tokens = tokensDeclarados(appConfig.providers as Provider[]);

    expect(tokens).toContain(REPOSITORIO_CARRITO); // CarritoStore
    expect(tokens).toContain(REPOSITORIO_SESION); // SesionStore
    expect(tokens).toContain(REPOSITORIO_PEDIDOS); // CheckoutStore
    expect(tokens).toContain(REPOSITORIO_PAGOS); // CheckoutStore
  });
});
