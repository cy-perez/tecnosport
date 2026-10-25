package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Los enlaces de compra en memoria; guarda el mismo objeto, como el adaptador guarda la fila. */
final class RepositorioEnlacesDeCompraFalso implements RepositorioEnlacesDeCompra {

  final Map<UUID, EnlaceDeCompra> porId = new LinkedHashMap<>();
  int actualizaciones;

  @Override
  public void guardar(EnlaceDeCompra enlace) {
    porId.put(enlace.id(), enlace);
  }

  @Override
  public void actualizar(EnlaceDeCompra enlace) {
    porId.put(enlace.id(), enlace);
    actualizaciones++;
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
    List<EnlaceDeCompra> desdeAhi = new ArrayList<>();
    for (EnlaceDeCompra e : porId.values()) {
      if (!e.creadoEn().isBefore(desde)) {
        desdeAhi.add(e);
      }
    }
    desdeAhi.sort(Comparator.comparing(EnlaceDeCompra::creadoEn).reversed());
    return desdeAhi;
  }
}
