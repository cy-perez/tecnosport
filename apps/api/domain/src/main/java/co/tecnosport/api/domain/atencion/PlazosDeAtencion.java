package co.tecnosport.api.domain.atencion;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Cuántos días hábiles hay para responder cada tipo de solicitud, y cuántos más admite una
 * prórroga.
 *
 * <p>Valor del dominio y no constantes sueltas: los plazos son legales, cambian cuando cambia la
 * ley, y un número legal repartido en tres archivos se desincroniza el día que cambie — que cambia.
 * Los valores concretos entran por configuración desde {@code bootstrap}; aquí solo vive la forma y
 * la regla de que ninguno puede ser cero.
 *
 * <p>Tres perfiles y no siete variables, mismo criterio que los límites de intentos: consulta de
 * datos, reclamo de datos, y todo lo demás, que es lo que el sitio promete en sus términos. Si
 * algún día uno de los tipos del tercer grupo necesita el suyo, se separa entonces.
 */
public final class PlazosDeAtencion {

  private final Map<TipoSolicitud, PlazoHabil> porTipo;

  private PlazosDeAtencion(Map<TipoSolicitud, PlazoHabil> porTipo) {
    this.porTipo = porTipo;
  }

  /**
   * {@code diasProrroga} en cero significa que ese tipo no admite prórroga: los plazos de datos
   * personales la tienen escrita en la ley, y el que el sitio promete para el resto no la menciona
   * — prorrogar algo que el texto publicado no permite prorrogar sería incumplir el propio texto.
   */
  public record PlazoHabil(int diasHabiles, int diasProrroga) {

    public PlazoHabil {
      if (diasHabiles <= 0) {
        throw new ExcepcionDeDominio("Un plazo de respuesta de cero días hábiles no es un plazo.");
      }
      if (diasProrroga < 0) {
        throw new ExcepcionDeDominio("Una prórroga no puede ser de días negativos.");
      }
    }

    public boolean admiteProrroga() {
      return diasProrroga > 0;
    }
  }

  public static PlazosDeAtencion de(
      PlazoHabil consultaDatos, PlazoHabil reclamoDatos, PlazoHabil consumidor) {
    Objects.requireNonNull(consultaDatos, "El plazo de consulta de datos no puede ser nulo.");
    Objects.requireNonNull(reclamoDatos, "El plazo de reclamo de datos no puede ser nulo.");
    Objects.requireNonNull(consumidor, "El plazo del consumidor no puede ser nulo.");
    Map<TipoSolicitud, PlazoHabil> mapa = new EnumMap<>(TipoSolicitud.class);
    mapa.put(TipoSolicitud.CONSULTA_DATOS, consultaDatos);
    mapa.put(TipoSolicitud.RECLAMO_DATOS, reclamoDatos);
    for (TipoSolicitud tipo : TipoSolicitud.values()) {
      mapa.putIfAbsent(tipo, consumidor);
    }
    return new PlazosDeAtencion(Map.copyOf(mapa));
  }

  public PlazoHabil para(TipoSolicitud tipo) {
    Objects.requireNonNull(tipo, "El tipo de solicitud no puede ser nulo.");
    return porTipo.get(tipo);
  }
}
