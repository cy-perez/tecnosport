package co.tecnosport.api.infrastructure.reintegro;

import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import co.tecnosport.api.infrastructure.reintegro.entidad.ReintegroJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sin transacción propia, mismo criterio que {@code RepositorioSolicitudesRetractoJpa}: comparte la
 * de quien lo llama, para que la constancia y el estado de la solicitud que la exige no puedan
 * quedar una escrita sin el otro.
 */
@Component
public class RepositorioReintegrosJpa implements RepositorioReintegros {

  private final ReintegroJpaRepository repositorio;

  public RepositorioReintegrosJpa(ReintegroJpaRepository repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  @Override
  public void guardar(Reintegro reintegro) {
    repositorio.save(
        new ReintegroJpaEntity(
            reintegro.id(),
            reintegro.pedidoId(),
            reintegro.motivo().name(),
            reintegro.origenId(),
            reintegro.monto().valor(),
            reintegro.medio().name(),
            reintegro.comprobante().orElse(null),
            reintegro.registradoEn(),
            reintegro.registradoPor()));
  }

  @Override
  public Optional<Reintegro> buscarPorId(UUID id) {
    return repositorio.findById(id).map(this::aReintegro);
  }

  @Override
  public List<Reintegro> buscarPorPedido(UUID pedidoId) {
    return repositorio.findByPedidoIdOrderByRegistradoEnDesc(pedidoId).stream()
        .map(this::aReintegro)
        .toList();
  }

  @Override
  public Optional<Reintegro> buscarPorOrigen(UUID origenId) {
    return repositorio.findByOrigenId(origenId).map(this::aReintegro);
  }

  private Reintegro aReintegro(ReintegroJpaEntity entidad) {
    return new Reintegro(
        entidad.getId(),
        entidad.getPedidoId(),
        MotivoReintegro.valueOf(entidad.getMotivo()),
        entidad.getOrigenId(),
        Dinero.deCop(entidad.getMonto()),
        MedioReintegro.valueOf(entidad.getMedio()),
        entidad.getComprobante(),
        entidad.getRegistradoEn(),
        entidad.getRegistradoPor());
  }
}
