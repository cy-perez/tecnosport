package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Slug;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListarMapaDelSitioTest {

  private static final Instant AHORA = Instant.parse("2026-09-08T12:00:00Z");

  @Test
  void devuelveLasEntradasDelRepositorio() {
    RepositorioMapaDelSitioDoble repositorio =
        new RepositorioMapaDelSitioDoble(
            List.of(
                new EntradaMapaDelSitio(new Slug("tenis-trail-runner"), AHORA),
                new EntradaMapaDelSitio(new Slug("morral-urbano-25l"), AHORA)));

    MapaDelSitio mapa = new ListarMapaDelSitio(repositorio).ejecutar();

    assertEquals(
        List.of("tenis-trail-runner", "morral-urbano-25l"),
        mapa.entradas().stream().map(e -> e.slug().valor()).toList());
    assertFalse(mapa.truncado());
  }

  /**
   * El tope es una regla del formato del sitemap, no del adaptador. Si viviera en la consulta SQL,
   * un segundo adaptador quedaría libre de respetarlo y nadie se enteraría hasta que Google
   * rechazara el archivo.
   *
   * <p>Pide una fila de más que el tope a propósito: esa fila sobrante es lo que permite saber que
   * hubo corte sin una segunda consulta que cuente.
   */
  @Test
  void pideAlRepositorioUnaFilaMasQueElTopeQueAdmiteUnSitemap() {
    RepositorioMapaDelSitioDoble repositorio = new RepositorioMapaDelSitioDoble(List.of());

    new ListarMapaDelSitio(repositorio).ejecutar();

    assertEquals(50_001, repositorio.limiteRecibido);
  }

  /**
   * El corte deja de ser silencioso, que es de lo que se trata: hasta hoy el caso de uso recortaba
   * y nadie se enteraba nunca. Con 50 001 productos publicados, el sitemap sale con 50 000 y lo
   * dice — quien lo publica registra el aviso (`MapaDelSitioControlador`).
   */
  @Test
  void pasadoElTopeCortaYLoDeclara() {
    MapaDelSitio mapa =
        new ListarMapaDelSitio(new RepositorioMapaDelSitioDoble(entradas(50_001))).ejecutar();

    assertEquals(50_000, mapa.entradas().size());
    assertTrue(mapa.truncado());
  }

  /** Justo en el tope no hay corte: 50 000 URL caben en un sitemap. */
  @Test
  void exactamenteElTopeNoSeConsideraCortado() {
    MapaDelSitio mapa =
        new ListarMapaDelSitio(new RepositorioMapaDelSitioDoble(entradas(50_000))).ejecutar();

    assertEquals(50_000, mapa.entradas().size());
    assertFalse(mapa.truncado());
  }

  private static List<EntradaMapaDelSitio> entradas(int cuantas) {
    return java.util.stream.IntStream.range(0, cuantas)
        .mapToObj(i -> new EntradaMapaDelSitio(new Slug("producto-" + i), AHORA))
        .toList();
  }

  @Test
  void unCatalogoVacioDevuelveListaVacia() {
    MapaDelSitio mapa =
        new ListarMapaDelSitio(new RepositorioMapaDelSitioDoble(List.of())).ejecutar();

    assertEquals(List.of(), mapa.entradas());
    assertFalse(mapa.truncado());
  }

  @Test
  void noSeConstruyeSinRepositorio() {
    assertThrows(NullPointerException.class, () -> new ListarMapaDelSitio(null));
  }

  /** Una entrada sin fecha no puede existir: sin ella no hay {@code <lastmod>} que escribir. */
  @Test
  void laEntradaExigeSlugYFecha() {
    assertThrows(NullPointerException.class, () -> new EntradaMapaDelSitio(new Slug("x"), null));
    assertThrows(NullPointerException.class, () -> new EntradaMapaDelSitio(null, AHORA));
  }

  private static final class RepositorioMapaDelSitioDoble implements RepositorioMapaDelSitio {

    private final List<EntradaMapaDelSitio> entradas;
    private int limiteRecibido;

    private RepositorioMapaDelSitioDoble(List<EntradaMapaDelSitio> entradas) {
      this.entradas = entradas;
    }

    /**
     * Respeta el límite, como hace el {@code limit} de la consulta real. Devolver la lista entera
     * sin mirarlo dejaba pasar una versión rota del caso de uso —la que pide el tope justo en vez
     * de una fila más—, porque el doble le entregaba igual la fila sobrante que él no había pedido.
     * Un doble más permisivo que la implementación esconde defectos.
     */
    @Override
    public List<EntradaMapaDelSitio> listarProductosPublicados(int limite) {
      this.limiteRecibido = limite;
      return entradas.size() <= limite ? entradas : entradas.subList(0, limite);
    }
  }
}
