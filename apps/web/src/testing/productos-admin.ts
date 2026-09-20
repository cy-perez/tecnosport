import {
  ImagenAdmin,
  InventarioSinMedir,
  MedirVarianteAdmin,
  ProductoAdmin,
  ProductosPaginadosAdmin,
  VarianteMedida,
} from '../app/features/admin/productos/domain/producto-admin.model';
import { RepositorioProductosAdmin } from '../app/features/admin/productos/domain/repositorio-productos-admin.puerto';

/**
 * Doble escrito a mano, sin librería de dobles (docs/06-testing.md). Vive aquí y no dentro de una
 * prueba porque lo necesitan dos pantallas que no se parecen: la lista de lo que falta por medir y
 * el panel, que solo enseña el conteo.
 *
 * <p>Solo implementa de verdad lo que toca la medición; el resto declara que no lo usa, igual que
 * los dobles del backend.
 */
export class RepositorioMedicionFalso implements RepositorioProductosAdmin {
  readonly medidas: MedirVarianteAdmin[] = [];

  constructor(
    private inventario: InventarioSinMedir = { total: 0, totalEnPublicados: 0, items: [] },
  ) {}

  async listarSinMedir(): Promise<InventarioSinMedir> {
    return this.inventario;
  }

  async medirVariante(comando: MedirVarianteAdmin): Promise<VarianteMedida> {
    this.medidas.push(comando);
    const variante = this.inventario.items.find((v) => v.varianteId === comando.varianteId);
    // La fila medida sale de la lista, que es lo que hace el servidor: la consulta solo devuelve
    // las que no tienen paquete. Sin esto, la prueba de la confirmación no distinguiría "se midió"
    // de "no pasó nada".
    this.inventario = {
      ...this.inventario,
      total: this.inventario.total - 1,
      items: this.inventario.items.filter((v) => v.varianteId !== comando.varianteId),
    };
    return {
      varianteId: comando.varianteId,
      sku: variante?.sku ?? '',
      pesoGramos: comando.pesoGramos,
      largoCm: comando.largoCm,
      anchoCm: comando.anchoCm,
      altoCm: comando.altoCm,
      correccion: false,
    };
  }

  listar(): Promise<ProductosPaginadosAdmin> {
    throw new Error('no usado por las pruebas de medición');
  }

  crear(): Promise<ProductoAdmin> {
    throw new Error('no usado por las pruebas de medición');
  }

  obtener(): Promise<ProductoAdmin> {
    throw new Error('no usado por las pruebas de medición');
  }

  editar(): Promise<ProductoAdmin> {
    throw new Error('no usado por las pruebas de medición');
  }

  agregarVariante(): Promise<void> {
    throw new Error('no usado por las pruebas de medición');
  }

  subirImagenPrincipal(): Promise<ImagenAdmin> {
    throw new Error('no usado por las pruebas de medición');
  }
}
