package co.tecnosport.api.bootstrap.usuario;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales del primer {@code ADMIN}, creado por {@link SembradorAdmin} si no existe al arrancar
 * — nunca actualizado si ya existe. Dato de negocio real, no inventado aquí (regla dura #5): los
 * valores por defecto son placeholders de desarrollo.
 */
@ConfigurationProperties(prefix = "tecnosport.admin-semilla")
public record PropiedadesAdminSemilla(String correo, String clave) {

  public PropiedadesAdminSemilla {
    if (correo == null || correo.isBlank()) {
      throw new IllegalStateException("tecnosport.admin-semilla.correo no puede estar vacío.");
    }
    if (clave == null || clave.isBlank()) {
      throw new IllegalStateException("tecnosport.admin-semilla.clave no puede estar vacía.");
    }
  }
}
