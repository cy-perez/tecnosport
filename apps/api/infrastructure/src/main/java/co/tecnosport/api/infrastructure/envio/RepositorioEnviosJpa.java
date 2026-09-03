package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.infrastructure.envio.entidad.EnvioJpaEntity;
import java.util.Objects;
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
            envio.despachadoEn()));
  }
}
