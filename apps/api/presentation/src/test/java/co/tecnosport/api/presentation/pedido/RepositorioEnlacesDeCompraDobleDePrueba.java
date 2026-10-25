package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.pedido.RepositorioEnlacesDeCompra;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Los enlaces de compra en memoria, para los controladores que crean pedidos o los consultan. */
final class RepositorioEnlacesDeCompraDobleDePrueba implements RepositorioEnlacesDeCompra {

  final Map<UUID, EnlaceDeCompra> porId = new LinkedHashMap<>();

  @Override
  public void guardar(EnlaceDeCompra enlace) {
    porId.put(enlace.id(), enlace);
  }

  @Override
  public void actualizar(EnlaceDeCompra enlace) {
    porId.put(enlace.id(), enlace);
  }

  @Override
  public Optional<EnlaceDeCompra> buscarPorId(UUID id) {
    return Optional.ofNullable(porId.get(id));
  }

  @Override
  public Optional<EnlaceDeCompra> buscarPorToken(String token) {
    return porId.values().stream().filter(e -> e.token().equals(token)).findFirst();
  }

  @Override
  public Optional<EnlaceDeCompra> buscarPorIdParaActualizar(UUID id) {
    return buscarPorId(id);
  }

  @Override
  public Optional<EnlaceDeCompra> buscarParaUsar(String token, UUID varianteId) {
    return buscarPorToken(token).filter(e -> e.varianteId().equals(varianteId));
  }

  @Override
  public List<EnlaceDeCompra> listarCreadosDesde(Instant desde) {
    return porId.values().stream().filter(e -> !e.creadoEn().isBefore(desde)).toList();
  }
}
