package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.List;
import java.util.UUID;

/**
 * Los dos precios de cada variante del catálogo, para recalcular el publicado cuando cambian las
 * tarifas de la pasarela (ADR-0095). Aparte de {@link RepositorioProductos} porque no carga
 * productos: el recálculo recorre el catálogo entero y solo necesita los dos números.
 */
public interface PreciosDelCatalogo {

  /** Todas las variantes del catálogo, con el nombre de su producto, ordenadas por ese nombre. */
  List<PreciosDeVariante> listar();

  /** Graba el publicado de una variante y nada más: el de contado no cambia con las tarifas. */
  void actualizarPublicado(UUID varianteId, Dinero publicado);
}
