package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de categorías. Implementación de producción: JPA con PostgreSQL. */
public interface RepositorioCategorias {

  /**
   * Todas las categorías, sin filtrar por línea: quien llama agrupa o filtra si lo necesita.
   * Incluye las que no tienen ni un producto, porque es lo que necesita el panel para poder cargar
   * el primero.
   */
  List<Categoria> listarTodas();

  /**
   * Solo las que tienen al menos un producto {@code PUBLICADO}, que es el mismo criterio con el que
   * la vitrina arma su rejilla.
   *
   * <p>Desde {@code V38__linea_tecnologia.sql} existen varias categorías de tecnología —ocho desde
   * que {@code V62} quitó las tres que ninguna lista de proveedor puede llenar— y el catálogo
   * sembrado solo llena unas pocas, así que la vitrina llevaba ofreciendo filtros que llevan a una
   * rejilla vacía. Ver {@code RepositorioMarcas#listarConProductosPublicados()}.
   */
  List<Categoria> listarConProductosPublicados();

  Optional<Categoria> buscarPorId(UUID id);
}
