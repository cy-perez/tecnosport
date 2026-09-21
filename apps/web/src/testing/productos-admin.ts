import {
  AjustarExistenciaAdmin,
  ExistenciaAjustada,
  ExistenciaDeVariante,
  ExistenciasDelCatalogo,
  ImagenAdmin,
  InventarioSinMedir,
  MedidasDelCatalogo,
  MedirVarianteAdmin,
  ProductoAdmin,
  ProductosPaginadosAdmin,
  VarianteMedida,
  ImagenDeGaleriaAdmin,
  ProductoAdminDetalle,
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

  /**
   * El segundo argumento existe porque el panel mira <b>las dos</b> consultas: lo que falta por
   * medir y lo que no tiene existencia. Por omisión, nada sin existencia — así una prueba que solo
   * hable de medición no tiene que enterarse de que existe la otra.
   */
  constructor(
    private inventario: InventarioSinMedir = { total: 0, totalEnPublicados: 0, items: [] },
    private existencias: ExistenciasDelCatalogo = {
      total: 0,
      totalSinExistencia: 0,
      totalSinExistenciaEnPublicados: 0,
      items: [],
    },
    private catalogoDeMedidas: MedidasDelCatalogo = {
      total: 0,
      totalSinMedir: 0,
      totalSinMedirEnPublicados: 0,
      items: [],
    },
  ) {}

  async listarSinMedir(): Promise<InventarioSinMedir> {
    return this.inventario;
  }

  /**
   * Refleja lo que hace el servidor: la lista de medidas trae **todas** las activas, así que una
   * variante medida sigue aquí con sus cifras nuevas — al revés que en `listarSinMedir`, de donde
   * desaparece. Sin esto, la prueba de la pantalla de corrección no distinguiría "se corrigió" de
   * "no pasó nada".
   */
  async listarMedidas(): Promise<MedidasDelCatalogo> {
    return this.catalogoDeMedidas;
  }

  publicar(): Promise<ProductoAdmin> {
    throw new Error('no usado por las pruebas de medición');
  }

  despublicar(): Promise<ProductoAdmin> {
    throw new Error('no usado por las pruebas de medición');
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

    // En la lista de medidas la fila **no** desaparece: se queda con las cifras nuevas, que es lo
    // que hace el servidor y lo que permite ver que una corrección entró.
    const enMedidas = this.catalogoDeMedidas.items.find((v) => v.varianteId === comando.varianteId);
    const correccion = enMedidas ? !enMedidas.sinMedir : false;
    const items = this.catalogoDeMedidas.items.map((v) =>
      v.varianteId === comando.varianteId
        ? {
            ...v,
            pesoGramos: comando.pesoGramos,
            largoCm: comando.largoCm,
            anchoCm: comando.anchoCm,
            altoCm: comando.altoCm,
            sinMedir: false,
          }
        : v,
    );
    this.catalogoDeMedidas = {
      ...this.catalogoDeMedidas,
      items,
      totalSinMedir: items.filter((v) => v.sinMedir).length,
      totalSinMedirEnPublicados: items.filter((v) => v.sinMedir && v.estadoProducto === 'PUBLICADO')
        .length,
    };

    return {
      varianteId: comando.varianteId,
      sku: variante?.sku ?? enMedidas?.sku ?? '',
      pesoGramos: comando.pesoGramos,
      largoCm: comando.largoCm,
      anchoCm: comando.anchoCm,
      altoCm: comando.altoCm,
      correccion,
    };
  }

  listar(): Promise<ProductosPaginadosAdmin> {
    throw new Error('no usado por las pruebas de medición');
  }

  crear(): Promise<ProductoAdmin> {
    throw new Error('no usado por las pruebas de medición');
  }

  obtener(): Promise<ProductoAdminDetalle> {
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

  async listarExistencias(): Promise<ExistenciasDelCatalogo> {
    return this.existencias;
  }

  ajustarExistencia(): Promise<ExistenciaAjustada> {
    throw new Error('no usado por las pruebas de medición');
  }

  subirImagenDeGaleria(): Promise<ImagenDeGaleriaAdmin> {
    throw new Error('no usado por esta prueba');
  }

  quitarImagenDeGaleria(): Promise<void> {
    throw new Error('no usado por esta prueba');
  }
}

/**
 * El gemelo del anterior para la pantalla de existencias y para el aviso del panel, que también
 * son dos pantallas distintas mirando la misma consulta.
 *
 * <p>Al ajustar recalcula la fila como lo hace el servidor —el libro pasa a decir lo contado y el
 * disponible baja lo que haya reservado (`ADR-0050`)—, porque sin eso una prueba no podría
 * distinguir "se ajustó" de "no pasó nada".
 */
export class RepositorioExistenciasFalso implements RepositorioProductosAdmin {
  readonly ajustes: AjustarExistenciaAdmin[] = [];

  constructor(
    private existencias: ExistenciasDelCatalogo = {
      total: 0,
      totalSinExistencia: 0,
      totalSinExistenciaEnPublicados: 0,
      items: [],
    },
    private catalogoDeMedidas: MedidasDelCatalogo = {
      total: 0,
      totalSinMedir: 0,
      totalSinMedirEnPublicados: 0,
      items: [],
    },
  ) {}

  async listarExistencias(): Promise<ExistenciasDelCatalogo> {
    return this.existencias;
  }

  async ajustarExistencia(comando: AjustarExistenciaAdmin): Promise<ExistenciaAjustada> {
    this.ajustes.push(comando);
    const variante = this.existencias.items.find((v) => v.varianteId === comando.varianteId);
    const saldoAnterior = variante?.saldoTotal ?? 0;
    const reservadas = variante?.reservadas ?? 0;

    const items: ExistenciaDeVariante[] = this.existencias.items.map((v) =>
      v.varianteId === comando.varianteId
        ? {
            ...v,
            saldoTotal: comando.cantidadContada,
            disponible: comando.cantidadContada - v.reservadas,
          }
        : v,
    );
    this.existencias = {
      ...this.existencias,
      items,
      totalSinExistencia: items.filter((v) => v.saldoTotal === 0).length,
      totalSinExistenciaEnPublicados: items.filter(
        (v) => v.saldoTotal === 0 && v.estadoProducto === 'PUBLICADO',
      ).length,
    };

    return {
      varianteId: comando.varianteId,
      sku: variante?.sku ?? '',
      nombreProducto: variante?.nombreProducto ?? '',
      saldoAnterior,
      saldoNuevo: comando.cantidadContada,
      diferencia: comando.cantidadContada - saldoAnterior,
      unidadesReservadas: reservadas,
      sinCambios: comando.cantidadContada === saldoAnterior,
      dejaReservasSinRespaldo: comando.cantidadContada < reservadas,
    };
  }

  listarSinMedir(): Promise<InventarioSinMedir> {
    throw new Error('no usado por las pruebas de existencias');
  }

  listarMedidas(): Promise<MedidasDelCatalogo> {
    throw new Error('no usado por las pruebas de existencias');
  }

  publicar(): Promise<ProductoAdmin> {
    throw new Error('no usado por las pruebas de existencias');
  }

  despublicar(): Promise<ProductoAdmin> {
    throw new Error('no usado por las pruebas de existencias');
  }

  medirVariante(): Promise<VarianteMedida> {
    throw new Error('no usado por las pruebas de existencias');
  }

  listar(): Promise<ProductosPaginadosAdmin> {
    throw new Error('no usado por las pruebas de existencias');
  }

  crear(): Promise<ProductoAdmin> {
    throw new Error('no usado por las pruebas de existencias');
  }

  obtener(): Promise<ProductoAdminDetalle> {
    throw new Error('no usado por las pruebas de existencias');
  }

  editar(): Promise<ProductoAdmin> {
    throw new Error('no usado por las pruebas de existencias');
  }

  agregarVariante(): Promise<void> {
    throw new Error('no usado por las pruebas de existencias');
  }

  subirImagenPrincipal(): Promise<ImagenAdmin> {
    throw new Error('no usado por las pruebas de existencias');
  }

  subirImagenDeGaleria(): Promise<ImagenDeGaleriaAdmin> {
    throw new Error('no usado por esta prueba');
  }

  quitarImagenDeGaleria(): Promise<void> {
    throw new Error('no usado por esta prueba');
  }
}
