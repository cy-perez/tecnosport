package co.tecnosport.api.infrastructure.sugerencia;

import co.tecnosport.api.application.sugerencia.RepositorioSugerencias;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.sugerencia.Sugerencia;
import co.tecnosport.api.infrastructure.sugerencia.entidad.SugerenciaJpaEntity;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Sin transacción propia: se guarda dentro de la que abrió el controlador, junto con la constancia
 * de autorización cuando la hay. Las dos escrituras tienen que vivir o morir juntas — una
 * sugerencia con el correo de alguien y sin su constancia es exactamente lo que la Ley 1581 no
 * permite. Mismo criterio que {@code RepositorioAutorizacionesJpa}.
 */
@Component
public class RepositorioSugerenciasJpa implements RepositorioSugerencias {

  private final SugerenciaJpaRepository sugerencias;

  public RepositorioSugerenciasJpa(SugerenciaJpaRepository sugerencias) {
    this.sugerencias = Objects.requireNonNull(sugerencias);
  }

  @Override
  public void guardar(Sugerencia sugerencia) {
    sugerencias.save(
        new SugerenciaJpaEntity(
            sugerencia.id(),
            sugerencia.mensaje(),
            sugerencia.correo().map(CorreoElectronico::valor).orElse(null),
            sugerencia.recibidaEn()));
  }
}
