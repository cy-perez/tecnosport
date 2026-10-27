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

  /**
   * Graba el publicado nuevo de una variante, y nada más —el de contado no cambia con las tarifas—,
   * <b>solo si sigue teniendo ese contado y ese publicado</b>. Es una sola escritura condicional,
   * no una lectura seguida de una escritura: si alguien cambió el precio entre la revisión y la
   * aplicación, no se pisa.
   *
   * @return {@code false} si la variante cambió (o ya no existe) y no se grabó nada
   */
  boolean actualizarPublicado(
      UUID varianteId, Dinero contado, Dinero publicadoActual, Dinero publicadoNuevo);
}
