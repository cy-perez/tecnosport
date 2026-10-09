package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.VarianteDeProveedorJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VarianteDeProveedorJpaRepository
    extends JpaRepository<VarianteDeProveedorJpaEntity, UUID> {

  List<VarianteDeProveedorJpaEntity> findByProductoId(UUID productoId);

  List<VarianteDeProveedorJpaEntity> findByProveedorIdAndConfiguracion(
      UUID proveedorId, String configuracion);
}
