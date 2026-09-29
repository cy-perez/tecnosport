package co.tecnosport.api.infrastructure.difusion;

import co.tecnosport.api.application.difusion.RepositorioPublicaciones;
import co.tecnosport.api.domain.difusion.EstadoPublicacion;
import co.tecnosport.api.domain.difusion.PublicacionEnRed;
import co.tecnosport.api.domain.difusion.RedSocial;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Las constancias de difusión contra Postgres.
 *
 * <p><b>{@code guardar} sirve para el alta y para la actualización</b>, y por eso es un {@code
 * save} y no un {@code insert}. El caso de uso llama dos veces con la misma publicación —una al
 * dejarla {@code PENDIENTE} antes de hablar con Meta y otra con el resultado—, y el id lo genera el
 * dominio, así que la segunda encuentra la fila y la pisa. Partirlo en dos métodos habría obligado
 * a quien llama a saber cuál de las dos veces es.
 */
@Repository
public class RepositorioPublicacionesJpa implements RepositorioPublicaciones {

  private final PublicacionEnRedJpaRepository jpa;

  public RepositorioPublicacionesJpa(PublicacionEnRedJpaRepository jpa) {
    this.jpa = Objects.requireNonNull(jpa, "El repositorio JPA no puede ser nulo.");
  }

  @Override
  public void guardar(PublicacionEnRed publicacion) {
    jpa.save(
        new PublicacionEnRedJpaEntity(
            publicacion.id(),
            publicacion.productoId().orElse(null),
            publicacion.red().name(),
            publicacion.estado().name(),
            publicacion.idPublicacionExterna().orElse(null),
            publicacion.pieDeFoto(),
            publicacion.urlImagen(),
            publicacion.solicitadaEn(),
            publicacion.publicadaEn().orElse(null),
            publicacion.detalleDelFallo().orElse(null)));
  }

  @Override
  public Optional<PublicacionEnRed> ultimaDe(UUID productoId, RedSocial red) {
    return jpa.findByProductoIdAndRedOrderBySolicitadaEnDesc(productoId, red.name()).stream()
        .findFirst()
        .map(RepositorioPublicacionesJpa::aDominio);
  }

  @Override
  public List<PublicacionEnRed> historialDe(UUID productoId) {
    return jpa.findByProductoIdOrderBySolicitadaEnDesc(productoId).stream()
        .map(RepositorioPublicacionesJpa::aDominio)
        .toList();
  }

  @Override
  public boolean hayUnaReciente(UUID productoId, RedSocial red, Instant desde) {
    return jpa.existsByProductoIdAndRedAndSolicitadaEnGreaterThanEqual(
        productoId, red.name(), desde);
  }

  private static PublicacionEnRed aDominio(PublicacionEnRedJpaEntity fila) {
    return new PublicacionEnRed(
        fila.getId(),
        fila.getProductoId(),
        RedSocial.valueOf(fila.getRed()),
        fila.getPieDeFoto(),
        fila.getUrlImagen(),
        EstadoPublicacion.valueOf(fila.getEstado()),
        fila.getIdPublicacionExterna(),
        fila.getSolicitadaEn(),
        fila.getPublicadaEn(),
        fila.getDetalleDelFallo());
  }
}
