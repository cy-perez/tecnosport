package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.proveedores.tecnologia.RepositorioVariantesDeProveedor;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.VarianteDeProveedor;
import co.tecnosport.api.infrastructure.proveedores.entidad.VarianteDeProveedorJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioVariantesDeProveedorJpa implements RepositorioVariantesDeProveedor {

  private final VarianteDeProveedorJpaRepository jpa;

  public RepositorioVariantesDeProveedorJpa(VarianteDeProveedorJpaRepository jpa) {
    this.jpa = Objects.requireNonNull(jpa);
  }

  @Override
  public void guardar(VarianteDeProveedor v) {
    jpa.save(
        new VarianteDeProveedorJpaEntity(
            v.varianteId(),
            v.productoId(),
            v.proveedorId(),
            v.configuracion(),
            v.color(),
            v.costo().valor(),
            v.actualizadoEn()));
  }

  @Override
  public List<VarianteDeProveedor> deProducto(UUID productoId) {
    return jpa.findByProductoId(productoId).stream()
        .map(RepositorioVariantesDeProveedorJpa::aDominio)
        .toList();
  }

  @Override
  public List<VarianteDeProveedor> deConfiguracion(UUID proveedorId, String configuracion) {
    return jpa.findByProveedorIdAndConfiguracion(proveedorId, configuracion).stream()
        .map(RepositorioVariantesDeProveedorJpa::aDominio)
        .toList();
  }

  private static VarianteDeProveedor aDominio(VarianteDeProveedorJpaEntity f) {
    return new VarianteDeProveedor(
        f.getVarianteId(),
        f.getProductoId(),
        f.getProveedorId(),
        f.getConfiguracion(),
        f.getColor(),
        Dinero.deCop(f.getCosto()),
        f.getActualizadoEn());
  }
}
