import { esEjeDeTalla } from '../../../catalogo/domain/seleccion-variante';
import { modelosSinColor } from './colores-del-producto';
import { VarianteResumenAdmin } from './producto-admin.model';

/** Una talla del producto, la que se corrige de una vez en todos sus colores. */
export interface TallaDelProducto {
  /** Una variante de esa talla —la primera—: el servidor encuentra las demás. */
  readonly modeloId: string;
  readonly talla: string;
  /** Con lo demás que distinga al modelo, si hay algo más que la talla: «M · Algodón». */
  readonly etiqueta: string;
}

/**
 * Las tallas del producto, una por modelo y en el orden en que llegaron. Es la misma agrupación que
 * `modelosSinColor` —y que `Producto.cambiarTalla` en la API—: corregir la talla de un modelo la
 * corrige en todos sus colores. Vacía si el producto no talla.
 */
export function tallasDelProducto(variantes: readonly VarianteResumenAdmin[]): TallaDelProducto[] {
  return modelosSinColor(variantes).flatMap((modelo) => {
    const variante = variantes.find((v) => v.id === modelo.modeloId);
    const talla = variante?.atributos.find(
      (atributo) => atributo.colorHex === null && esEjeDeTalla(atributo.nombre),
    );
    return talla
      ? [{ modeloId: modelo.modeloId, talla: talla.valor, etiqueta: modelo.etiqueta }]
      : [];
  });
}
