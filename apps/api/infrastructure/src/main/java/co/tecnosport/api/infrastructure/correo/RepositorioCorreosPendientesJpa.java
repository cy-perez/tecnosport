package co.tecnosport.api.infrastructure.correo;

import co.tecnosport.api.application.compartido.CorreoPendiente;
import co.tecnosport.api.application.compartido.RepositorioCorreosPendientes;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.infrastructure.correo.entidad.CorreoPendienteJpaEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cada método abre su propia transacción, igual que {@code RepositorioIdempotenciaJpa} y por una
 * razón parecida: el drenaje corre desde una tarea que a propósito no abre ninguna, y una sentencia
 * {@code @Modifying} sin transacción activa revienta con {@code TransactionRequiredException}
 * (apps/api/CLAUDE.md).
 *
 * <p><b>Y envolver el lote sería un error, no un descuido.</b> Con una transacción por correo, un
 * fallo en el número veinte no revierte los diecinueve ya marcados como enviados — que es lo mismo
 * que sostiene {@code TareaAvisoDePlazoDeEntrega} y lo que impide que alguien reciba dos veces el
 * mismo mensaje.
 */
@Component
public class RepositorioCorreosPendientesJpa implements RepositorioCorreosPendientes {

  private static final Logger log = LoggerFactory.getLogger(RepositorioCorreosPendientesJpa.class);

  private final CorreoPendienteJpaRepository correos;

  public RepositorioCorreosPendientesJpa(CorreoPendienteJpaRepository correos) {
    this.correos = Objects.requireNonNull(correos);
  }

  /**
   * Una fila que no se deja leer —un destinatario que ya no pasa la validación de {@code
   * CorreoElectronico}— revienta al mapearse. Antes eso lanzaba antes del bucle del drenaje y
   * ningún correo salía mientras esa fila existiera; ahora se rinde con su motivo y las demás
   * siguen.
   */
  @Override
  @Transactional
  public List<CorreoPendiente> buscarEnviables(int maxIntentos, Instant ahora, int limite) {
    Objects.requireNonNull(ahora, "La fecha no puede ser nula.");
    List<CorreoPendiente> enviables = new ArrayList<>();
    for (CorreoPendienteJpaEntity entidad :
        correos.buscarEnviables(maxIntentos, ahora, PageRequest.of(0, limite))) {
      try {
        enviables.add(aCorreoPendiente(entidad));
      } catch (RuntimeException ilegible) {
        log.error(
            "Correo pendiente {} imposible de mandar: {}", entidad.getId(), ilegible.toString());
        correos.rendir(
            entidad.getId(), maxIntentos, "ilegible: " + ilegible.getClass().getSimpleName());
      }
    }
    return enviables;
  }

  @Override
  @Transactional
  public boolean reclamar(UUID id, Instant ahora, Instant proximoIntento) {
    Objects.requireNonNull(id, "El id no puede ser nulo.");
    Objects.requireNonNull(ahora, "La fecha no puede ser nula.");
    Objects.requireNonNull(proximoIntento, "El próximo intento no puede ser nulo.");
    return correos.reclamar(id, ahora, proximoIntento) == 1;
  }

  @Override
  @Transactional
  public void marcarEnviado(UUID id, Instant ahora) {
    Objects.requireNonNull(id, "El id no puede ser nulo.");
    Objects.requireNonNull(ahora, "La fecha no puede ser nula.");
    correos.marcarEnviado(id, ahora);
  }

  @Override
  @Transactional
  public void registrarFallo(UUID id, String detalle) {
    Objects.requireNonNull(id, "El id no puede ser nulo.");
    correos.registrarFallo(id, detalle);
  }

  @Override
  @Transactional
  public int purgarEnviados(Instant limite) {
    Objects.requireNonNull(limite, "El límite no puede ser nulo.");
    return correos.purgarEnviados(limite);
  }

  /**
   * El destinatario vuelve a pasar por {@code CorreoElectronico}, que valida el formato. Si una
   * fila trajera algo que ya no es un correo, revienta aquí y no al mandarlo — que es donde se
   * puede ver.
   */
  private static CorreoPendiente aCorreoPendiente(CorreoPendienteJpaEntity entidad) {
    return new CorreoPendiente(
        entidad.getId(),
        new CorreoElectronico(entidad.getDestinatario()),
        entidad.getAsunto(),
        entidad.getCuerpoHtml(),
        entidad.getIntentos());
  }
}
