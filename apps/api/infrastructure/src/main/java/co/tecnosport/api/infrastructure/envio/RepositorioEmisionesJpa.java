package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.EmisionYaEnCursoException;
import co.tecnosport.api.application.envio.RepositorioEmisiones;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.infrastructure.envio.entidad.EmisionDeGuiaJpaEntity;
import co.tecnosport.api.infrastructure.envio.entidad.EnvioEnPlataformaJpaEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RepositorioEmisionesJpa implements RepositorioEmisiones {

  private static final Logger log = LoggerFactory.getLogger(RepositorioEmisionesJpa.class);

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
  /**
   * <strong>{@code @Transactional}, y es lo que impide una fila que el dominio no sabe
   * leer.</strong> Este método escribe en dos tablas —la emisión y sus envíos en la plataforma— y
   * sin transacción propia cada {@code saveAndFlush} se comprometía solo. Si la instancia moría
   * entre los dos (Cloud Run reciclando, un despliegue, un OOM), quedaba una emisión {@code
   * EN_CURSO} con cero envíos: un estado que {@link EmisionDeGuia} rechaza al reconstruirse, así
   * que esa fila reventaba la consulta que la trajera. Y como la tarea de resolución mapea antes de
   * devolver, <b>una sola fila mala detenía el despacho automático de todos los pedidos</b>. Lo
   * levantó una revisión adversarial.
   *
   * <p>No contradice a {@code ADR-0033}: lo que ese ADR saca de una transacción es el <i>caso de
   * uso</i>, porque ninguna transacción de base de datos revierte un cobro de Skydropx. Aquí no hay
   * ningún tercero en la mitad — son dos escrituras nuestras que describen un solo hecho.
   */
  @Override
  @Transactional
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
    return sinLasIlegibles(emisiones.findByPedidoIdOrderBySolicitadaEnAsc(pedidoId));
  }

  @Override
  public List<EmisionDeGuia> buscarEnCurso(int maximo) {
    return sinLasIlegibles(
        emisiones.findByEstadoOrderBySolicitadaEnAsc(
            EstadoEmision.EN_CURSO.name(), Limit.of(maximo)));
  }

  /**
   * Las que el dominio no puede reconstruir se registran y se saltan, en vez de tumbar la consulta
   * entera.
   *
   * <p>Segunda mitad de la defensa: el {@code @Transactional} de {@code guardar} impide crear filas
   * así, pero una que ya exista no puede seguir deteniendo el despacho de todos los demás pedidos.
   * Saltarla también es lo que manda {@code ADR-0038} para el camino de la cancelación: anular una
   * guía no puede tumbar la cancelación del pedido ni el reintegro de quien compró.
   *
   * <p>Se registra en {@code error} con el identificador, y no en {@code warn}: es una fila que
   * alguien tiene que mirar a mano, y hoy no sale en ninguna pantalla.
   */
  private List<EmisionDeGuia> sinLasIlegibles(List<EmisionDeGuiaJpaEntity> filas) {
    List<EmisionDeGuia> leidas = new ArrayList<>(filas.size());
    for (EmisionDeGuiaJpaEntity fila : filas) {
      try {
        leidas.add(aDominio(fila));
      } catch (RuntimeException ilegible) {
        log.error(
            "Emisión {} del pedido {} no se puede reconstruir y se salta: {}. Hay que mirarla a"
                + " mano: puede tener saldo comprometido en la plataforma.",
            fila.getId(),
            fila.getPedidoId(),
            ilegible.getMessage());
      }
    }
    return List.copyOf(leidas);
  }

  @Override
  public List<EmisionDeGuia> buscarSolicitadasAntesDe(Instant corte, int maximo) {
    return sinLasIlegibles(
        emisiones.findByEstadoAndSolicitadaEnBeforeOrderBySolicitadaEnAsc(
            EstadoEmision.SOLICITADA.name(), corte, Limit.of(maximo)));
  }

  @Override
  public List<EmisionDeGuia> buscarEnCursoAntesDe(Instant corte, int maximo) {
    return sinLasIlegibles(
        emisiones.findByEstadoAndSolicitadaEnBeforeOrderBySolicitadaEnAsc(
            EstadoEmision.EN_CURSO.name(), corte, Limit.of(maximo)));
  }

  @Override
  public Optional<EmisionDeGuia> buscarPorId(UUID id) {
    return emisiones.findById(id).map(this::aDominio);
  }

  /**
   * Se sale temprano con el identificador vacío en vez de preguntarle a la base por una cadena en
   * blanco: la respuesta de un cobro extra puede traer el envío sin valor, y una consulta que
   * siempre devuelve vacío es ruido que después nadie sabe leer en un registro.
   */
  @Override
  public Optional<EmisionDeGuia> buscarPorEnvioEnPlataforma(String envioEnPlataforma) {
    if (envioEnPlataforma == null || envioEnPlataforma.isBlank()) {
      return Optional.empty();
    }
    return enviosEnPlataforma
        .findByIdExterno(envioEnPlataforma)
        .map(EnvioEnPlataformaJpaEntity::getEmisionId)
        .flatMap(emisiones::findById)
        .map(this::aDominio);
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
