package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VarianteJpaRepository extends JpaRepository<VarianteJpaEntity, UUID> {

  List<VarianteJpaEntity> findByProductoIdIn(Collection<UUID> productoIds);

  boolean existsBySku(String sku);

  /**
   * Las variantes de los productos de este proveedor que llevan este SKU suyo, en cualquier estado.
   */
  @Query(
      "select v from VarianteJpaEntity v, ProductoJpaEntity p"
          + " where p.id = v.productoId and p.proveedorId = :proveedorId"
          + " and v.referenciaProveedor = :referencia")
  List<VarianteJpaEntity> buscarPorReferenciaDeProveedor(
      @Param("proveedorId") UUID proveedorId, @Param("referencia") String referencia);

  /**
   * Las activas agotadas porque el costo del proveedor les quitó el margen (V125): producto, su
   * nombre, variante y SKU.
   */
  @Query(
      "select p.id, p.nombre, v.id, v.sku from VarianteJpaEntity v, ProductoJpaEntity p"
          + " where p.id = v.productoId and v.agotadaPorMargen = true and v.estado = 'ACTIVA'"
          + " order by p.nombre, v.sku")
  List<Object[]> agotadasPorMargen();

  long countByProductoIdAndEstadoAndAgotadaPorProveedorFalse(UUID productoId, String estado);
}
