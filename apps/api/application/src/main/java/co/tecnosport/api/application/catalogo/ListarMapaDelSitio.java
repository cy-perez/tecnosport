package co.tecnosport.api.application.catalogo;

import java.util.List;
import java.util.Objects;

/**
 * Las URL de producto que deben aparecer en el {@code sitemap.xml} del sitio público.
 *
 * <p>El caso de uso existe aunque casi solo delegue: el tope de filas es una regla del formato, no
 * del adaptador, y ponerlo en el repositorio lo escondería en infraestructura. Quien mañana escriba
 * un segundo adaptador no puede quedar libre de respetarlo.
 */
public final class ListarMapaDelSitio {

  /**
   * Tope del formato: un sitemap admite 50 000 URL. Pasado ese número hay que partirlo en varios y
   * publicar un índice.
   *
   * <p>Se corta y no se revienta: un sitemap incompleto sigue siendo válido y sirve; uno que
   * devuelve error no sirve para nada. Lo que <b>no</b> se hace ya es cortar en silencio — el
   * resultado dice si cortó, y quien lo publica lo registra. Eso era un {@code TODO} "cuando el
   * catálogo se acerque", y un aviso que depende de que alguien se acuerde de mirar el tamaño del
   * catálogo no es un aviso.
   */
  static final int MAXIMO_URLS = 50_000;

  private final RepositorioMapaDelSitio repositorio;

  public ListarMapaDelSitio(RepositorioMapaDelSitio repositorio) {
    this.repositorio =
        Objects.requireNonNull(repositorio, "El repositorio del mapa del sitio no puede ser nulo.");
  }

  /**
   * Pide una fila de más que el tope, y esa fila sobrante es todo el mecanismo: si vuelve, es que
   * hay al menos una URL que no cabe. Contar aparte sería una segunda consulta para saber lo mismo.
   */
  public MapaDelSitio ejecutar() {
    List<EntradaMapaDelSitio> encontradas = repositorio.listarProductosPublicados(MAXIMO_URLS + 1);
    if (encontradas.size() <= MAXIMO_URLS) {
      return new MapaDelSitio(encontradas, false);
    }
    return new MapaDelSitio(encontradas.subList(0, MAXIMO_URLS), true);
  }
}
