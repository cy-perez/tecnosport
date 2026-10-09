package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.proveedores.tecnologia.RepositorioListasDeTecnologia;
import jakarta.persistence.EntityManager;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Nativo y sin entidad: tres consultas sobre una tabla de constancias que nadie edita ni lee
 * entera.
 */
@Repository
public class RepositorioListasDeTecnologiaJpa implements RepositorioListasDeTecnologia {

  private final EntityManager entityManager;

  public RepositorioListasDeTecnologiaJpa(EntityManager entityManager) {
    this.entityManager = Objects.requireNonNull(entityManager);
  }

  @Override
  public Optional<LocalDate> fechaDeLaUltima(UUID proveedorId) {
    Object fecha =
        entityManager
            .createNativeQuery(
                "select max(fecha_lista) from lista_tecnologia_importada where proveedor_id = ?1")
            .setParameter(1, proveedorId)
            .getSingleResult();
    if (fecha == null) {
      return Optional.empty();
    }
    return Optional.of(fecha instanceof Date sql ? sql.toLocalDate() : (LocalDate) fecha);
  }

  @Override
  public boolean yaEntro(UUID proveedorId, String huella) {
    Number cuantas =
        (Number)
            entityManager
                .createNativeQuery(
                    "select count(*) from lista_tecnologia_importada"
                        + " where proveedor_id = ?1 and huella = ?2")
                .setParameter(1, proveedorId)
                .setParameter(2, huella)
                .getSingleResult();
    return cuantas.longValue() > 0;
  }

  @Override
  public void registrar(
      UUID proveedorId, LocalDate fechaLista, String huella, Instant importadaEn) {
    entityManager
        .createNativeQuery(
            "insert into lista_tecnologia_importada (proveedor_id, huella, fecha_lista,"
                + " importada_en) values (?1, ?2, ?3, ?4)")
        .setParameter(1, proveedorId)
        .setParameter(2, huella)
        .setParameter(3, fechaLista)
        .setParameter(4, importadaEn)
        .executeUpdate();
  }
}
