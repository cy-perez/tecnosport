package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.EmisionYaEnCursoException;
import co.tecnosport.api.application.envio.RepositorioEmisiones;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.infrastructure.envio.entidad.EmisionDeGuiaJpaEntity;
import co.tecnosport.api.infrastructure.envio.entidad.EnvioEnPlataformaJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
public class RepositorioEmisionesJpa implements RepositorioEmisiones {

  /**
   * Los tres en los que puede haber plata comprometida sin desenlace, como nombres para la base.
   */
  private static final List<String> ABIERTOS =
      List.of(
          EstadoEmision.SOLICITADA.name(),
          EstadoEmision.EN_CURSO.name(),
          EstadoEmision.INDETERMINADA.name());

  private final EmisionDeGuiaJpaRepository emisiones;
  private final EnvioEnPlataformaJpaRepository enviosEnPlataforma;

  public RepositorioEmisionesJpa(
      EmisionDeGuiaJpaRepository emisiones, EnvioEnPlataformaJpaRepository enviosEnPlataforma) {
    this.emisiones = Objects.requireNonNull(emisiones);
    this.enviosEnPlataforma = Objects.requireNonNull(enviosEnPlataforma);
  }

  /**
   * Los identificadores de la plataforma se escriben una sola vez, cuando la emisión pasa a en
   * curso, y no se vuelven a tocar: una emisión no crea envíos nuevos después. Resolverla solo
   * cambia estado, detalle y fecha.
   *
   * <p><strong>La violación de unicidad se traduce aquí.</strong> El índice parcial de la base es
   * lo que de verdad impide dos emisiones abiertas para el mismo pedido —entre leer y escribir cabe
   * un segundo clic— y si su excepción subiera cruda saldría por el manejador como un 500 genérico,
   * además de romper la regla de que ninguna excepción de JPA sale de {@code infrastructure}. Al
   * traducirla, quien llama recibe el mismo error que si la lectura previa la hubiera detectado.
   */
  @Override
  public void guardar(EmisionDeGuia emision) {
    exigirQueNoHayaOtraAbierta(emision);
    try {
      emisiones.saveAndFlush(
          new EmisionDeGuiaJpaEntity(
              emision.id(),
              emision.pedidoId(),
              emision.transportadora(),
              emision.idTarifa(),
              emision.actor(),
              emision.estado().name(),
              emision.detalle().orElse(null),
              emision.solicitadaEn(),
              emision.resueltaEn().orElse(null)));
    } catch (DataIntegrityViolationException e) {
      throw traducir(emision, e);
    }

    if (emision.enviosEnPlataforma().isEmpty()
        || !enviosEnPlataforma.findByEmisionIdOrderByPosicionAsc(emision.id()).isEmpty()) {
      return;
    }
    List<String> envios = emision.enviosEnPlataforma();
    List<EnvioEnPlataformaJpaEntity> filas =
        IntStream.range(0, envios.size())
            .mapToObj(
                posicion ->
                    new EnvioEnPlataformaJpaEntity(emision.id(), posicion, envios.get(posicion)))
            .toList();
    enviosEnPlataforma.saveAllAndFlush(filas);
  }

  /**
   * La comprobación que sí puede mirar los datos, porque corre <strong>antes</strong> del choque:
   * después de que un {@code flush} falle, la sesión de Hibernate queda inservible y cualquier
   * consulta sobre ella vuelve a reventar. Por eso la traducción de abajo no consulta nada.
   */
  private void exigirQueNoHayaOtraAbierta(EmisionDeGuia emision) {
    if (!emision.estado().abierta()) {
      return;
    }
    emisiones
        .findFirstByPedidoIdAndEstadoInOrderBySolicitadaEnAsc(emision.pedidoId(), ABIERTOS)
        .filter(abierta -> !abierta.getId().equals(emision.id()))
        .ifPresent(
            abierta -> {
              throw new EmisionYaEnCursoException(
                  emision.pedidoId(), abierta.getId(), EstadoEmision.valueOf(abierta.getEstado()));
            });
  }

  /**
   * Lo que la lectura de arriba no alcanzó a ver: dos peticiones a la vez, las dos leyeron "no hay
   * ninguna" y la base dejó entrar una sola. Se traduce sin volver a consultar —la sesión ya está
   * rota— y sin adivinar cuál ganó, que tampoco importa: lo que importa es que esta no entró.
   *
   * <p>Si el choque fuera otro —un identificador de envío repetido, por ejemplo— esto le pondría un
   * nombre equivocado. Se acota mirando el estado: solo una emisión abierta puede chocar contra ese
   * índice, y las demás violaciones se dejan subir.
   */
  private RuntimeException traducir(EmisionDeGuia emision, DataIntegrityViolationException e) {
    return emision.estado().abierta() ? new EmisionYaEnCursoException(emision.pedidoId()) : e;
  }

  @Override
  public Optional<EmisionDeGuia> buscarAbiertaDePedido(UUID pedidoId) {
    return emisiones
        .findFirstByPedidoIdAndEstadoInOrderBySolicitadaEnAsc(pedidoId, ABIERTOS)
        .map(this::aDominio);
  }

  @Override
  public List<EmisionDeGuia> buscarDePedido(UUID pedidoId) {
    return emisiones.findByPedidoIdOrderBySolicitadaEnAsc(pedidoId).stream()
        .map(this::aDominio)
        .toList();
  }

  @Override
  public List<EmisionDeGuia> buscarEnCurso(int maximo) {
    return emisiones
        .findByEstadoOrderBySolicitadaEnAsc(EstadoEmision.EN_CURSO.name(), Limit.of(maximo))
        .stream()
        .map(this::aDominio)
        .toList();
  }

  @Override
  public List<EmisionDeGuia> buscarSolicitadasAntesDe(Instant corte, int maximo) {
    return emisiones
        .findByEstadoAndSolicitadaEnBeforeOrderBySolicitadaEnAsc(
            EstadoEmision.SOLICITADA.name(), corte, Limit.of(maximo))
        .stream()
        .map(this::aDominio)
        .toList();
  }

  @Override
  public Optional<EmisionDeGuia> buscarPorId(UUID id) {
    return emisiones.findById(id).map(this::aDominio);
  }

  @Override
  public List<EmisionDeGuia> buscarQueExigenOjoHumano(int maximo) {
    return emisiones
        .findByEstadoInOrderBySolicitadaEnAsc(
            List.copyOf(EstadoEmision.nombresQueExigenOjoHumano()), Limit.of(maximo))
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
        entidad.getActor(),
        enviosEnPlataforma.findByEmisionIdOrderByPosicionAsc(entidad.getId()).stream()
            .map(EnvioEnPlataformaJpaEntity::getIdExterno)
            .toList(),
        entidad.getSolicitadaEn(),
        EstadoEmision.valueOf(entidad.getEstado()),
        entidad.getDetalle(),
        entidad.getResueltaEn());
  }
}
