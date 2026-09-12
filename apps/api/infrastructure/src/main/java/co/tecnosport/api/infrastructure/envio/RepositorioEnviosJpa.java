package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import co.tecnosport.api.infrastructure.envio.entidad.EnvioJpaEntity;
import co.tecnosport.api.infrastructure.envio.entidad.EventoSeguimientoJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RepositorioEnviosJpa implements RepositorioEnvios {

  private final EnvioJpaRepository repositorio;
  private final EventoSeguimientoJpaRepository eventos;

  public RepositorioEnviosJpa(
      EnvioJpaRepository repositorio, EventoSeguimientoJpaRepository eventos) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.eventos = Objects.requireNonNull(eventos);
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
    guardarEventosNuevos(envio);
  }

  /**
   * Solo se insertan los que no estaban. Las líneas y el historial del pedido se reemplazan
   * completos en cada guardado —a esa escala es más simple—, pero aquí no: el rastro es append-only
   * (adr/0022), y borrar para volver a insertar cambiaría el {@code id} de filas que alguien puede
   * estar citando en una reclamación. La comparación es por identificador externo, que es la misma
   * llave con la que el dominio decide si un evento ya estaba.
   */
  private void guardarEventosNuevos(Envio envio) {
    Set<String> yaGuardados =
        eventos.findByEnvioIdOrderByOcurrioEnAsc(envio.id()).stream()
            .map(EventoSeguimientoJpaEntity::getIdExterno)
            .collect(Collectors.toSet());

    List<EventoSeguimientoJpaEntity> nuevos =
        envio.eventos().stream()
            .filter(evento -> !yaGuardados.contains(evento.idExterno()))
            .map(evento -> aEntidad(envio.id(), evento))
            .toList();

    if (!nuevos.isEmpty()) {
      eventos.saveAll(nuevos);
    }
  }

  private EventoSeguimientoJpaEntity aEntidad(UUID envioId, EventoSeguimiento evento) {
    return new EventoSeguimientoJpaEntity(
        evento.id(),
        envioId,
        evento.estado().name(),
        evento.descripcion(),
        evento.ocurrioEn(),
        evento.recibidoEn(),
        evento.idExterno());
  }

  @Override
  public Optional<Envio> buscarPorPedidoId(UUID pedidoId) {
    return repositorio.findByPedidoId(pedidoId).map(this::aEnvio);
  }

  @Override
  public Optional<Envio> buscarPorGuia(String guia) {
    return repositorio.findByGuia(guia).map(this::aEnvio);
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
        entidad.getRecaudoConciliadoEn(),
        eventos.findByEnvioIdOrderByOcurrioEnAsc(entidad.getId()).stream()
            .map(this::aEvento)
            .toList());
  }

  private EventoSeguimiento aEvento(EventoSeguimientoJpaEntity entidad) {
    return new EventoSeguimiento(
        entidad.getId(),
        EstadoEnvio.valueOf(entidad.getEstado()),
        entidad.getDescripcion(),
        entidad.getOcurrioEn(),
        entidad.getRecibidoEn(),
        entidad.getIdExterno());
  }
}
