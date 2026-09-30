package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.infrastructure.proveedores.entidad.ProveedorJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioProveedoresJpa implements RepositorioProveedores {

  private final ProveedorJpaRepository jpa;
  private final Reloj reloj;

  public RepositorioProveedoresJpa(ProveedorJpaRepository jpa, Reloj reloj) {
    this.jpa = Objects.requireNonNull(jpa);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  public void guardar(Proveedor proveedor) {
    Instant ahora = reloj.ahora();
    jpa.save(aFila(proveedor, ahora, ahora));
  }

  @Override
  public void actualizar(Proveedor proveedor) {
    Instant creadoEn =
        jpa.findById(proveedor.id())
            .map(ProveedorJpaEntity::getCreadoEn)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No se actualiza un proveedor que no existe: " + proveedor.id()));
    jpa.save(aFila(proveedor, creadoEn, reloj.ahora()));
  }

  @Override
  public Optional<Proveedor> buscarPorId(UUID id) {
    return jpa.findById(id).map(RepositorioProveedoresJpa::aDominio);
  }

  @Override
  public List<Proveedor> listar() {
    return jpa.findAllByOrderByNombreAsc().stream()
        .map(RepositorioProveedoresJpa::aDominio)
        .toList();
  }

  private static ProveedorJpaEntity aFila(Proveedor p, Instant creadoEn, Instant actualizadoEn) {
    return new ProveedorJpaEntity(
        p.id(),
        p.nombre(),
        p.linea().name(),
        p.telefonoWhatsApp(),
        p.nombreEnExportacion(),
        p.activo(),
        p.publicacionAutomatica(),
        p.factorDeMargen().orElse(null),
        creadoEn,
        actualizadoEn);
  }

  private static Proveedor aDominio(ProveedorJpaEntity fila) {
    return new Proveedor(
        fila.getId(),
        fila.getNombre(),
        LineaCatalogo.valueOf(fila.getLinea()),
        fila.getTelefonoWhatsapp(),
        fila.getNombreEnExportacion(),
        fila.isActivo(),
        fila.isPublicacionAutomatica(),
        fila.getFactorDeMargen());
  }
}
