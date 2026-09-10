package co.tecnosport.api.bootstrap.atencion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Los plazos de respuesta, en días hábiles. Van en configuración y no incrustados en el código
 * porque son legales: un número legal repartido en tres archivos se desincroniza el día que cambie
 * la ley, y cambia.
 *
 * <p>Tres perfiles y no uno por tipo, mismo criterio que los límites de intentos. Los dos primeros
 * salen de la Ley 1581 de 2012 (consultas y reclamos de datos personales, con sus prórrogas); el
 * tercero es el que el propio sitio promete en sus términos para las peticiones del consumidor, y
 * ese texto no menciona prórroga — de ahí el cero, que no es un olvido.
 *
 * <p><b>Aquí queda escrita una contradicción real de los documentos publicados:</b> los términos
 * prometen quince días hábiles para "toda petición" y la política de datos promete diez para una
 * consulta, apuntando las dos al mismo correo. El sistema cumple el más corto de los dos para cada
 * tipo, que es lo único defendible; corregir el texto es otra tarea y no es de código.
 */
@ConfigurationProperties(prefix = "tecnosport.atencion")
public record PropiedadesAtencion(
    int diasConsultaDatos,
    int diasProrrogaConsultaDatos,
    int diasReclamoDatos,
    int diasProrrogaReclamoDatos,
    int diasConsumidor) {

  public PropiedadesAtencion {
    exigirPositivo(diasConsultaDatos, "dias-consulta-datos");
    exigirPositivo(diasReclamoDatos, "dias-reclamo-datos");
    exigirPositivo(diasConsumidor, "dias-consumidor");
    if (diasProrrogaConsultaDatos < 0 || diasProrrogaReclamoDatos < 0) {
      throw new IllegalStateException("Una prórroga no puede ser de días negativos.");
    }
  }

  private static void exigirPositivo(int valor, String propiedad) {
    if (valor <= 0) {
      throw new IllegalStateException(
          "tecnosport.atencion."
              + propiedad
              + " debe ser mayor que cero: un plazo de respuesta"
              + " de cero días hábiles no es un plazo.");
    }
  }
}
