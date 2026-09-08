package co.tecnosport.api.application.catalogo;

import java.util.List;

/**
 * Puerto propio y estrecho, en vez de un método más en {@link RepositorioProductos}.
 *
 * <p>Son dos lecturas sin nada en común salvo la tabla: aquella pagina, filtra, ordena e hidrata el
 * agregado completo para la vitrina; esta barre el catálogo entero y devuelve dos columnas. Meterla
 * allí obligaría a implementarla en cada doble de prueba del repositorio de productos —hay varios,
 * repartidos por módulos— para que ninguno la use.
 *
 * <p>Implementación de producción: JPA con PostgreSQL.
 */
public interface RepositorioMapaDelSitio {

  /**
   * Los productos publicados, con la fecha en que se tocaron por última vez.
   *
   * <p>La implementación debe filtrar por {@code estado == PUBLICADO} en la propia consulta: un
   * borrador en el sitemap es una invitación a indexar una ficha que responde 404 público.
   *
   * @param limite tope de filas. Lo impone el formato, no la base — ver {@link ListarMapaDelSitio}.
   */
  List<EntradaMapaDelSitio> listarProductosPublicados(int limite);
}
