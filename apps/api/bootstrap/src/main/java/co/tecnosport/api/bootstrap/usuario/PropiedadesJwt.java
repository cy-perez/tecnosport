package co.tecnosport.api.bootstrap.usuario;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT_SECRETO, JWT_MINUTOS_ACCESO, JWT_DIAS_REFRESCO de docs/07-infra-gcp.md. */
@ConfigurationProperties(prefix = "tecnosport.jwt")
public record PropiedadesJwt(String secreto, int minutosAcceso, int diasRefresco) {

  public PropiedadesJwt {
    if (secreto == null || secreto.isBlank()) {
      throw new IllegalStateException("tecnosport.jwt.secreto no puede estar vacío.");
    }
    if (minutosAcceso <= 0) {
      throw new IllegalStateException("tecnosport.jwt.minutos-acceso debe ser mayor que cero.");
    }
    if (diasRefresco <= 0) {
      throw new IllegalStateException("tecnosport.jwt.dias-refresco debe ser mayor que cero.");
    }
  }
}
