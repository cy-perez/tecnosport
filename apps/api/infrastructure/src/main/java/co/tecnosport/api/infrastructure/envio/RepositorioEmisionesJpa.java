package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioEmisiones;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.infrastructure.envio.entidad.EmisionDeGuiaJpaEntity;
import co.tecnosport.api.infrastructure.envio.entidad.EnvioEnPlataformaJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
public class RepositorioEmisionesJpa implements RepositorioEmisiones {

  private final EmisionDeGuiaJpaRepository emisiones;
  private final EnvioEnPlataformaJpaRepository enviosEnPlataforma;

  public RepositorioEmisionesJpa(
      EmisionDeGuiaJpaRepository emisiones, EnvioEnPlataformaJpaRepository enviosEnPlataforma) {
    this.emisiones = Objects.requireNonNull(emisiones);
    this.enviosEnPlataforma = Objects.requireNonNull(enviosEnPlataforma);
  }

  /**
   * Los identificadores de la plataforma se escriben una sola vez, al nacer la emisión, y no se
   * vuelven a tocar: una emisión no crea envíos nuevos después. Resolverla solo cambia estado,
   * detalle y fecha, así que reescribir la lista en cada guardado sería borrar y volver a insertar
   * lo mismo — y en el camino, perder el rastro de una guía pagada si algo fallara a mitad.
   */
  @Override
  public void guardar(EmisionDeGuia emision) {
    emisiones.saveAndFlush(
        new EmisionDeGuiaJpaEntity(
            emision.id(),
            emision.pedidoId(),
            emision.transportadora(),
            emision.idTarifa(),
            emision.estado().name(),
            emision.detalle().orElse(null),
            emision.solicitadaEn(),
            emision.resueltaEn().orElse(null)));

    if (enviosEnPlataforma.findByEmisionIdOrderByPosicionAsc(emision.id()).isEmpty()) {
      List<String> envios = emision.enviosEnPlataforma();
      for (int posicion = 0; posicion < envios.size(); posicion++) {
        enviosEnPlataforma.save(
            new EnvioEnPlataformaJpaEntity(emision.id(), posicion, envios.get(posicion)));
      }
    }
  }

  /**
   * La emisión abierta de un pedido. Hay como mucho una, y no lo garantiza esta consulta sino un
   * índice único parcial: entre leer y escribir cabe un segundo clic en el panel, y ese segundo
   * clic sería otro cobro.
   */
  @Override
  public Optional<EmisionDeGuia> buscarEnCursoDePedido(UUID pedidoId) {
    return emisiones
        .findByPedidoIdAndEstado(pedidoId, EstadoEmision.EN_CURSO.name())
        .map(this::aDominio);
  }

  @Override
  public List<EmisionDeGuia> buscarEnCurso(int maximo) {
    return emisiones
        .findByEstadoOrderBySolicitadaEnAsc(EstadoEmision.EN_CURSO.name(), Limit.of(maximo))
        .stream()
        .map(this::aDominio)
        .toList();
  }

  private EmisionDeGuia aDominio(EmisionDeGuiaJpaEntity entidad) {
    return new EmisionDeGuia(
        entidad.getId(),
        entidad.getPedidoId(),
        entidad.getTransportadora(),
        entidad.getIdTarifa(),
        enviosEnPlataforma.findByEmisionIdOrderByPosicionAsc(entidad.getId()).stream()
            .map(EnvioEnPlataformaJpaEntity::getIdExterno)
            .toList(),
        entidad.getSolicitadaEn(),
        EstadoEmision.valueOf(entidad.getEstado()),
        entidad.getDetalle(),
        entidad.getResueltaEn());
  }
}
