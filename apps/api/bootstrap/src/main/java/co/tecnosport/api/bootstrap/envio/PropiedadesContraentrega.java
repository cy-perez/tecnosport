package co.tecnosport.api.bootstrap.envio;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code CONTRAENTREGA_HABILITADA}/{@code CONTRAENTREGA_MONTO_MINIMO}/{@code
 * CONTRAENTREGA_MONTO_MAXIMO}/{@code CONTRAENTREGA_CATEGORIAS_EXCLUIDAS} de docs/07-infra-gcp.md.
 *
 * <p><b>Ojo con lo que este javadoc afirmaba y era falso:</b> decía que sin los montos la
 * aplicación no arranca. Nunca fue cierto — el {@code application.yml} les da valor por omisión,
 * como a todo lo demás. Lo que cambió al subir el techo de 100.000 a 2.000.000 es la
 * <b>consecuencia</b> de olvidar la variable en un despliegue: antes se quedaba con un tope
 * conservador que rompía ventas y no plata, y ahora se queda con el máximo que el proveedor admite.
 * Un valor por omisión permisivo no avisa cuando falta; solo el conservador lo hace.
 *
 * <p>El rango es el que reporta la ayuda pública de Skydropx, no una preferencia del negocio.
 * {@code montoMaximo} estuvo en 100.000 durante toda la fase, con una nota en el {@code
 * application.yml} diciendo textualmente que era un marcador de desarrollo y no un límite decidido
 * — una cifra provisional con la etiqueta puesta es mejor que una provisional disfrazada de real,
 * pero solo hasta que llega la de verdad.
 */
@ConfigurationProperties(prefix = "tecnosport.contraentrega")
public record PropiedadesContraentrega(
    boolean habilitada, long montoMinimo, long montoMaximo, List<String> categoriasExcluidas) {

  public PropiedadesContraentrega {
    if (montoMinimo <= 0) {
      throw new IllegalStateException(
          "tecnosport.contraentrega.monto-minimo debe ser mayor que cero.");
    }
    if (montoMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.contraentrega.monto-maximo debe ser mayor que cero.");
    }
    // El rango invertido lo rechaza también CriteriosContraentrega, pero decirlo aquí nombra la
    // propiedad concreta que hay que corregir en el despliegue.
    if (montoMinimo > montoMaximo) {
      throw new IllegalStateException(
          "tecnosport.contraentrega.monto-minimo ("
              + montoMinimo
              + ") no puede superar a monto-maximo ("
              + montoMaximo
              + ").");
    }
    categoriasExcluidas =
        categoriasExcluidas == null ? List.of() : List.copyOf(categoriasExcluidas);
  }
}
