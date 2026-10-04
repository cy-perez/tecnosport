package co.tecnosport.api.bootstrap.usuario;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales del primer {@code ADMIN}, creado por {@link SembradorAdmin} si no existe al arrancar
 * — nunca actualizado si ya existe. Dato de negocio real, no inventado aquí (regla dura #5): los la
 * clave no tiene valor por omisión fuera del perfil {@code local} ({@code application-local.yml}).
 */
@ConfigurationProperties(prefix = "tecnosport.admin-semilla")
public record PropiedadesAdminSemilla(String correo, String clave) {

  public PropiedadesAdminSemilla {
    if (correo == null || correo.isBlank()) {
      throw new IllegalStateException("tecnosport.admin-semilla.correo no puede estar vacío.");
    }
    // `${` es un marcador que el Binder de Boot no resolvió y entregó como texto.
    if (clave == null || clave.isBlank() || clave.contains("${")) {
      throw new IllegalStateException(
          "tecnosport.admin-semilla.clave no está configurada: falta la variable de entorno"
              + " ADMIN_CLAVE.");
    }
  }
}
