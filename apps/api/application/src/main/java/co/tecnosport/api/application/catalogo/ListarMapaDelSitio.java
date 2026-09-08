package co.tecnosport.api.application.catalogo;

import java.util.List;
import java.util.Objects;

/**
 * Las URL de producto que deben aparecer en el {@code sitemap.xml} del sitio público.
 *
 * <p>El caso de uso existe aunque hoy solo delegue: el tope de filas es una regla del formato, no
 * del adaptador, y ponerlo en el repositorio lo escondería en infraestructura. Quien mañana escriba
 * un segundo adaptador no puede quedar libre de respetarlo.
 */
public final class ListarMapaDelSitio {

  /**
   * Tope del formato: un sitemap admite 50 000 URL. Pasado ese número hay que partirlo en varios y
   * publicar un índice — TODO cuando el catálogo se acerque, que con un catálogo joven no es
   * pronto. Se corta en silencio y no se revienta: un sitemap incompleto sigue siendo válido y
   * sirve; uno que devuelve error no sirve para nada.
   */
  static final int MAXIMO_URLS = 50_000;

  private final RepositorioMapaDelSitio repositorio;

  public ListarMapaDelSitio(RepositorioMapaDelSitio repositorio) {
    this.repositorio =
        Objects.requireNonNull(repositorio, "El repositorio del mapa del sitio no puede ser nulo.");
  }

  public List<EntradaMapaDelSitio> ejecutar() {
    return repositorio.listarProductosPublicados(MAXIMO_URLS);
  }
}
