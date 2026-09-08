package co.tecnosport.api.bootstrap.legal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La versión del texto de la política de tratamiento de datos que se estampa en cada constancia
 * (Ley 1581 de 2012, docs/08-seguridad-legal.md: "se guarda fecha, versión del texto e IP").
 *
 * <p>Es la fecha de vigencia del texto publicado en {@code /legales/privacidad}, y va acoplada a
 * él: el texto vive en los JSON de Transloco de este mismo repositorio, así que <strong>cambiar el
 * texto y cambiar esta fecha son el mismo commit</strong>. Si se separaran, las constancias nuevas
 * dirían que el titular aceptó una versión que no es la que se le mostró, y esa constancia no
 * serviría ante la SIC.
 *
 * <p>Por eso tiene valor por omisión y no se exige por entorno: no es configuración de despliegue
 * sino un dato que viaja con el código. La variable de entorno existe para poder corregirla sin
 * redesplegar, no para tener que fijarla.
 */
@ConfigurationProperties(prefix = "tecnosport.legal")
public record PropiedadesLegal(String politicaDatosVersion) {

  public PropiedadesLegal {
    if (politicaDatosVersion == null || politicaDatosVersion.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.legal.politica-datos-version no puede estar vacío: sin versión no hay"
              + " constancia de qué texto aceptó el titular.");
    }
  }
}
