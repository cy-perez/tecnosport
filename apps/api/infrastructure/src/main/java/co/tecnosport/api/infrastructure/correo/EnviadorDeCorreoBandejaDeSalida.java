package co.tecnosport.api.infrastructure.correo;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.infrastructure.correo.entidad.CorreoPendienteJpaEntity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * El {@link EnviadorDeCorreo} de producción desde {@code adr/0045}: <b>no manda nada, encola</b>.
 *
 * <p><b>Sin {@code @Transactional}, y eso es todo el mecanismo.</b> Este adaptador se une a la
 * transacción que quien llama ya tenga abierta —los controladores la abren con {@code
 * TransactionTemplate}— de modo que la fila del correo y la escritura de negocio se comprometen
 * juntas o no se comprometen ninguna. Ésa es la propiedad que {@code adr/0044} no pudo dar y dejó
 * anotada: hasta entonces el correo salía <i>antes</i> del commit, así que un fallo al comprometer
 * dejaba a quien compró leyendo "reintegramos el dinero de tu pedido" y al sistema sin constancia
 * del reintegro. Ahora ese caso no manda nada, porque la fila se fue con la reversión.
 *
 * <p>Ponerle {@code REQUIRES_NEW} lo rompería entero, y conviene decirlo aquí para que nadie lo
 * "arregle": con transacción propia la fila sobreviviría a la reversión y volveríamos a mandar el
 * correo de una operación que no ocurrió. Es lo contrario de {@code EnTransaccionPropia}, que
 * existe justo para el caso opuesto.
 *
 * <p><b>Las tareas son el otro caso y también funciona</b>, aunque por otro camino: corren sin
 * transacción a propósito, así que aquí la escritura se compromete sola. No se gana atomicidad con
 * el reclamo —eso lo siguen cubriendo el {@code reclamar}/{@code liberar} de cada tarea— pero sí se
 * gana lo que la bandeja da a todos: reintentos y constancia.
 *
 * <p>No lanza {@code CorreoNoEnviadoException} salvo que la base de datos falle, y en ese caso la
 * transacción de quien llama ya está condenada de todas formas.
 */
public class EnviadorDeCorreoBandejaDeSalida implements EnviadorDeCorreo {

  private final CorreoPendienteJpaRepository correos;
  private final Reloj reloj;

  public EnviadorDeCorreoBandejaDeSalida(CorreoPendienteJpaRepository correos, Reloj reloj) {
    this.correos = Objects.requireNonNull(correos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  public void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {
    Objects.requireNonNull(destinatario, "El destinatario no puede ser nulo.");
    Objects.requireNonNull(asunto, "El asunto no puede ser nulo.");
    Objects.requireNonNull(cuerpoHtml, "El cuerpo no puede ser nulo.");
    Instant ahora = reloj.ahora();
    // proximoIntentoEn = ahora: el primer intento es inmediato, o sea en la próxima vuelta de la
    // tarea. El espaciado solo empieza a contar cuando algo falla.
    correos.save(
        new CorreoPendienteJpaEntity(
            UUID.randomUUID(),
            destinatario.valor(),
            asunto,
            cuerpoHtml,
            ahora,
            ahora,
            0,
            null,
            null));
  }
}
