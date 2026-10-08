package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.MedidasDeReferencia;
import co.tecnosport.api.domain.envio.PesoDeReferencia;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de las referencias de envío ({@code adr/0071}): las medidas transversales de la bolsa y el
 * peso promedio de cada categoría. Implementación de producción: JPA con PostgreSQL.
 *
 * <p>Las dos cosas en un solo puerto porque se leen siempre juntas —una sin la otra no arma ninguna
 * bolsa— y las edita la misma pantalla del panel.
 */
public interface RepositorioReferenciasDeEnvio {

  /** Vacío si nadie las ha fijado: entonces nada se cotiza con promedios. */
  Optional<MedidasDeReferencia> medidas();

  void guardarMedidas(MedidasDeReferencia medidas);

  /** Todos los pesos fijados. Una categoría que no está aquí no tiene promedio. */
  List<PesoDeReferencia> pesos();

  /** Inserta o reemplaza el de esa categoría. */
  void guardarPeso(PesoDeReferencia peso);

  /** Si la categoría no tenía peso, no hace nada. */
  void quitarPeso(UUID categoriaId);
}
