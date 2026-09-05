package co.tecnosport.api.infrastructure.compartido;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Un solo {@code INSERT ... ON CONFLICT ... DO UPDATE} (Postgres 16, confirmado en los tres
 * entornos) en vez de leer y luego escribir: esto último tenía dos problemas bajo concurrencia real
 * sobre la misma llave — un "lost update" (dos peticiones leen el mismo contador, la segunda pisa
 * el incremento de la primera) y, si ambas caían en la rama "crear fila nueva" a la vez, un choque
 * de clave primaria que se escapaba como excepción de JPA cruda fuera de esta capa. La sentencia
 * atómica resuelve los dos a la vez: no hay lectura previa que pisar ni una segunda inserción que
 * choque.
 *
 * <p>{@code REQUIRES_NEW}, no simplemente {@code @Transactional}: el límite por cuenta lo llaman
 * casos de uso ({@code IniciarSesion}, {@code RegistrarUsuario}...) desde dentro de la transacción
 * que ya abrió el controlador. Si el contador compartiera esa transacción, cada intento que termina
 * en una excepción de negocio (clave incorrecta, correo ya registrado) revertiría también el
 * incremento del contador — exactamente los intentos que este mecanismo existe para contar. Mismo
 * razonamiento que {@code RepositorioIdempotenciaJpa}: el conteo tiene que quedar comprometido
 * antes de que la transacción de negocio decida si revierte o no.
 */
@Component
public class LimitadorDeIntentosJpa implements LimitadorDeIntentos {

  private static final String UPSERT =
      """
      insert into limite_intentos (clave, contador, ventana_expira_en)
      values (?1, 1, ?3)
      on conflict (clave) do update set
        contador = case when limite_intentos.ventana_expira_en > ?2
                     then limite_intentos.contador + 1 else 1 end,
        ventana_expira_en = case when limite_intentos.ventana_expira_en > ?2
                              then limite_intentos.ventana_expira_en else ?3 end
      returning contador
      """;

  private final EntityManager entityManager;

  public LimitadorDeIntentosJpa(EntityManager entityManager) {
    this.entityManager = Objects.requireNonNull(entityManager);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean permitir(String clave, int maximoIntentos, Duration ventana, Instant ahora) {
    Number contador =
        (Number)
            entityManager
                .createNativeQuery(UPSERT)
                .setParameter(1, clave)
                .setParameter(2, ahora)
                .setParameter(3, ahora.plus(ventana))
                .getSingleResult();
    return contador.intValue() <= maximoIntentos;
  }
}
