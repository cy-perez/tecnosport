package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.domain.envio.TipoDeRevision;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface RepositorioAcusesDeRevision {

  void guardar(AcuseDeRevision acuse);

  /**
   * Cuándo se miró por última vez cada una de {@code referencias}, para las que se hayan mirado
   * alguna vez. Las que no aparecen en el mapa no tienen acuse.
   *
   * <p>De golpe y no una por una: la bandeja pregunta por todo lo que trae en un lote, y una
   * consulta por fila convertiría una pantalla en tantas idas a la base como guías quietas haya.
   *
   * <p>Devuelve el instante y no el acuse entero porque es lo único que la regla necesita: una guía
   * vuelve a la bandeja si le llegó un evento <em>después</em> de la última vez que alguien la
   * miró. Quién lo miró y qué anotó es historia, y la historia se lee en otro sitio.
   */
  Map<UUID, Instant> ultimaRevisionDe(TipoDeRevision tipo, Collection<UUID> referencias);
}
