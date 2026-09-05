package co.tecnosport.api.infrastructure.compartido;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.infrastructure.compartido.entidad.LimiteIntentosJpaEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura simple + {@code save()}, sin bloqueo pesimista a propósito (ver el javadoc de {@link
 * LimitadorDeIntentos#permitir}): a diferencia de {@code RepositorioInventarioJpa}, una carrera
 * aquí en el peor caso deja pasar uno o dos intentos de más bajo concurrencia alta, no vende dos
 * veces la última unidad.
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

  private final LimiteIntentosJpaRepository limites;

  public LimitadorDeIntentosJpa(LimiteIntentosJpaRepository limites) {
    this.limites = Objects.requireNonNull(limites);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean permitir(String clave, int maximoIntentos, Duration ventana, Instant ahora) {
    Optional<LimiteIntentosJpaEntity> existente = limites.findById(clave);
    if (existente.isEmpty() || !existente.get().getVentanaExpiraEn().isAfter(ahora)) {
      limites.save(new LimiteIntentosJpaEntity(clave, 1, ahora.plus(ventana)));
      return true;
    }

    LimiteIntentosJpaEntity entidad = existente.get();
    if (entidad.getContador() >= maximoIntentos) {
      return false;
    }
    limites.save(
        new LimiteIntentosJpaEntity(
            clave, entidad.getContador() + 1, entidad.getVentanaExpiraEn()));
    return true;
  }
}
