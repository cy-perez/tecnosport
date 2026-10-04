package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.DependenciasDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.infrastructure.proveedores.entidad.ProveedorJpaEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioProveedoresJpa implements RepositorioProveedores {

  /**
   * El orden es el de las llaves foráneas, todas {@code restrict} salvo la de {@code
   * publicacion_mensaje}, que cae en cascada con su publicación.
   */
  private static final List<String> BORRAR_HISTORIAL =
      List.of(
          "delete from borrador_producto where proveedor_id = ?1",
          "delete from publicacion_proveedor where proveedor_id = ?1",
          "delete from mensaje_proveedor where proveedor_id = ?1",
          "delete from lote_ingesta where proveedor_id = ?1",
          "delete from proveedor where id = ?1");

  private final ProveedorJpaRepository jpa;
  private final Reloj reloj;
  private final EntityManager entityManager;

  public RepositorioProveedoresJpa(
      ProveedorJpaRepository jpa, Reloj reloj, EntityManager entityManager) {
    this.jpa = Objects.requireNonNull(jpa);
    this.reloj = Objects.requireNonNull(reloj);
    this.entityManager = Objects.requireNonNull(entityManager);
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

  @Override
  public DependenciasDeProveedor dependenciasDe(UUID id) {
    Number productos =
        (Number)
            entityManager
                .createNativeQuery("select count(*) from producto where proveedor_id = ?1")
                .setParameter(1, id)
                .getSingleResult();
    Number abiertos =
        (Number)
            entityManager
                .createNativeQuery(
                    "select count(*) from lote_ingesta where proveedor_id = ?1"
                        + " and estado in ('RECIBIDO', 'PROCESANDO')")
                .setParameter(1, id)
                .getSingleResult();
    @SuppressWarnings("unchecked")
    List<String> archivos =
        entityManager
            .createNativeQuery(
                "select referencia_archivo from lote_ingesta"
                    + " where proveedor_id = ?1 and referencia_archivo is not null"
                    + " union"
                    + " select referencia_archivo from mensaje_proveedor"
                    + " where proveedor_id = ?1 and referencia_archivo is not null")
            .setParameter(1, id)
            .getResultList();
    return new DependenciasDeProveedor(
        productos.longValue(), abiertos.longValue() > 0, archivos.stream().sorted().toList());
  }

  @Override
  public void eliminarConSuHistorial(UUID id) {
    for (String sentencia : BORRAR_HISTORIAL) {
      entityManager.createNativeQuery(sentencia).setParameter(1, id).executeUpdate();
    }
    // Las sentencias nativas no pasan por el contexto de persistencia: sin esto, una entidad del
    // proveedor ya cargada en esta transacción seguiría devolviéndose como si existiera.
    entityManager.clear();
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
