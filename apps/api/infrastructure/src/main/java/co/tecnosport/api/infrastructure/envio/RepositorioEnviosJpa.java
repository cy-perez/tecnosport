package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.infrastructure.envio.entidad.EnvioJpaEntity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RepositorioEnviosJpa implements RepositorioEnvios {

  private final EnvioJpaRepository repositorio;

  public RepositorioEnviosJpa(EnvioJpaRepository repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  @Override
  public void guardar(Envio envio) {
    repositorio.save(
        new EnvioJpaEntity(
            envio.id(),
            envio.pedidoId(),
            envio.transportadora(),
            envio.guia(),
            envio.costoEnvio().valor(),
            envio.despachadoEn(),
            envio.comisionRecaudo().map(Dinero::valor).orElse(null),
            envio.recaudoConciliadoEn().orElse(null)));
  }

  @Override
  public Optional<Envio> buscarPorPedidoId(UUID pedidoId) {
    return repositorio.findByPedidoId(pedidoId).map(this::aEnvio);
  }

  private Envio aEnvio(EnvioJpaEntity entidad) {
    return new Envio(
        entidad.getId(),
        entidad.getPedidoId(),
        entidad.getTransportadora(),
        entidad.getGuia(),
        Dinero.deCop(entidad.getCostoEnvio()),
        entidad.getDespachadoEn(),
        entidad.getComisionRecaudo() == null ? null : Dinero.deCop(entidad.getComisionRecaudo()),
        entidad.getRecaudoConciliadoEn());
  }
}
