package co.tecnosport.api.infrastructure.compartido;

import co.tecnosport.api.application.compartido.RepositorioIdempotencia;
import co.tecnosport.api.application.compartido.RespuestaIdempotente;
import co.tecnosport.api.infrastructure.compartido.entidad.IdempotenciaJpaEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A diferencia de {@code RepositorioPedidosJpa}/{@code RepositorioInventarioJpa}, este adaptador SÍ
 * abre su propia transacción en cada método, a propósito: {@code reclamar} tiene que quedar
 * comprometido en la base de datos antes de que arranque la transacción de negocio, no compartirla
 * — si compartiera transacción y esa transacción de negocio revierte, la reclamación revertiría con
 * ella y el reintento volvería a colarse.
 *
 * <p>24 horas de vigencia (docs/03-api.md) hardcodeadas: es una constante técnica del mecanismo, no
 * una tarifa o un plazo de negocio configurable como {@code MINUTOS_RESERVA_INVENTARIO}.
 */
@Component
public class RepositorioIdempotenciaJpa implements RepositorioIdempotencia {

  private static final Duration VIGENCIA = Duration.ofHours(24);

  /**
   * Una reclamación sin completar más vieja que esto es de un proceso que murió a mitad de la
   * petición: ninguna de las rutas protegidas tarda tanto. Antes no vencía nunca, y esa llave
   * respondía 409 "petición en curso" para siempre.
   */
  private static final Duration ABANDONO = Duration.ofMinutes(10);

  private static final String COMPLETADA = "COMPLETADA";

  private final IdempotenciaJpaRepository idempotencias;

  public RepositorioIdempotenciaJpa(IdempotenciaJpaRepository idempotencias) {
    this.idempotencias = Objects.requireNonNull(idempotencias);
  }

  @Override
  @Transactional
  public Optional<RespuestaIdempotente> buscarCompletada(String llave, Instant ahora) {
    return idempotencias
        .findById(llave)
        .filter(e -> COMPLETADA.equals(e.getEstado()))
        .filter(e -> e.getCompletadoEn() != null && !vencida(e.getCompletadoEn(), ahora))
        .map(e -> new RespuestaIdempotente(e.getEstadoHttp(), e.getTipoContenido(), e.getCuerpo()));
  }

  @Override
  @Transactional
  public boolean reclamar(String llave, String metodo, String ruta, Instant ahora) {
    idempotencias.borrarVencidas(llave, ahora.minus(VIGENCIA), ahora.minus(ABANDONO));
    return idempotencias.reclamarSiEstaLibre(llave, metodo, ruta, ahora) == 1;
  }

  /**
   * La tabla guarda el cuerpo de la respuesta —el de crear un pedido lleva nombre, teléfono,
   * dirección y correo— y nadie la purgaba: una llave es un UUID que no se repite, así que la fila
   * solo se borraba si alguien reutilizaba esa misma llave. Datos personales retenidos sin plazo, y
   * que la eliminación de cuenta no alcanzaba.
   */
  @Override
  @Transactional
  public int purgarVencidas(Instant ahora) {
    return idempotencias.borrarVencidas(null, ahora.minus(VIGENCIA), ahora.minus(ABANDONO));
  }

  @Override
  @Transactional
  public void completar(String llave, RespuestaIdempotente respuesta, Instant ahora) {
    IdempotenciaJpaEntity existente =
        idempotencias
            .findById(llave)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No hay una llave de idempotencia reclamada: " + llave));
    idempotencias.save(
        new IdempotenciaJpaEntity(
            llave,
            existente.getMetodo(),
            existente.getRuta(),
            COMPLETADA,
            respuesta.estadoHttp(),
            respuesta.tipoContenido(),
            respuesta.cuerpo(),
            existente.getCreadoEn(),
            ahora));
  }

  @Override
  @Transactional
  public void liberar(String llave) {
    idempotencias.deleteById(llave);
  }

  private boolean vencida(Instant completadoEn, Instant ahora) {
    return completadoEn.plus(VIGENCIA).isBefore(ahora);
  }
}
