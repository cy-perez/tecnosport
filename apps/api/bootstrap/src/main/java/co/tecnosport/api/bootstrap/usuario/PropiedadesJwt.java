package co.tecnosport.api.bootstrap.usuario;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT_SECRETO, JWT_MINUTOS_ACCESO, JWT_DIAS_REFRESCO de docs/07-infra-gcp.md. */
@ConfigurationProperties(prefix = "tecnosport.jwt")
public record PropiedadesJwt(String secreto, int minutosAcceso, int diasRefresco) {

  public PropiedadesJwt {
    // `${` es un marcador que el Binder de Boot no resolvió y entregó como texto: sin esta guarda,
    // `${JWT_SECRETO}` sería una clave HS256 válida y conocida por quien lea el YAML.
    if (secreto == null || secreto.isBlank() || secreto.contains("${")) {
      throw new IllegalStateException(
          "tecnosport.jwt.secreto no está configurado: falta la variable de entorno JWT_SECRETO.");
    }
    if (minutosAcceso <= 0) {
      throw new IllegalStateException("tecnosport.jwt.minutos-acceso debe ser mayor que cero.");
    }
    if (diasRefresco <= 0) {
      throw new IllegalStateException("tecnosport.jwt.dias-refresco debe ser mayor que cero.");
    }
  }
}
