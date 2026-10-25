package co.tecnosport.api.infrastructure.pedido;

import co.tecnosport.api.application.pedido.RepositorioEnlacesDeCompra;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import co.tecnosport.api.infrastructure.pedido.entidad.EnlaceDeCompraJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioEnlacesDeCompraJpa implements RepositorioEnlacesDeCompra {

  private final EnlaceDeCompraJpaRepository enlaces;

  public RepositorioEnlacesDeCompraJpa(EnlaceDeCompraJpaRepository enlaces) {
    this.enlaces = Objects.requireNonNull(enlaces);
  }

  @Override
  public void guardar(EnlaceDeCompra enlace) {
    enlaces.save(aEntidad(enlace));
  }

  @Override
  public void actualizar(EnlaceDeCompra enlace) {
    enlaces.save(aEntidad(enlace));
  }

  @Override
  public Optional<EnlaceDeCompra> buscarPorId(UUID id) {
    return enlaces.findById(id).map(RepositorioEnlacesDeCompraJpa::aDominio);
  }

  @Override
  public Optional<EnlaceDeCompra> buscarPorToken(String token) {
    return enlaces.findByToken(token).map(RepositorioEnlacesDeCompraJpa::aDominio);
  }

  @Override
  public Optional<EnlaceDeCompra> buscarPorIdParaActualizar(UUID id) {
    return enlaces.findByIdParaActualizar(id).map(RepositorioEnlacesDeCompraJpa::aDominio);
  }

  @Override
  public Optional<EnlaceDeCompra> buscarParaUsar(String token, UUID varianteId) {
    return enlaces.findParaUsar(token, varianteId).map(RepositorioEnlacesDeCompraJpa::aDominio);
  }

  @Override
  public List<EnlaceDeCompra> listarCreadosDesde(Instant desde) {
    return enlaces.findByCreadoEnGreaterThanEqualOrderByCreadoEnDesc(desde).stream()
        .map(RepositorioEnlacesDeCompraJpa::aDominio)
        .toList();
  }

  private static EnlaceDeCompraJpaEntity aEntidad(EnlaceDeCompra e) {
    return new EnlaceDeCompraJpaEntity(
        e.id(),
        e.token(),
        e.varianteId(),
        e.reservaId(),
        e.creadoEn(),
        e.expiraEn(),
        e.entradaPropia(),
        e.usadoEn().orElse(null),
        e.pedidoId().orElse(null),
        e.anuladoEn().orElse(null));
  }

  private static EnlaceDeCompra aDominio(EnlaceDeCompraJpaEntity e) {
    return new EnlaceDeCompra(
        e.getId(),
        e.getToken(),
        e.getVarianteId(),
        e.getReservaId(),
        e.getCreadoEn(),
        e.getExpiraEn(),
        e.isEntradaPropia(),
        e.getUsadoEn(),
        e.getPedidoId(),
        e.getAnuladoEn());
  }
}
