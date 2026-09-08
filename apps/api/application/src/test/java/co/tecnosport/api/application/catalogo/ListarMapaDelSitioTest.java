package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    List<EntradaMapaDelSitio> entradas = new ListarMapaDelSitio(repositorio).ejecutar();

    assertEquals(
        List.of("tenis-trail-runner", "morral-urbano-25l"),
        entradas.stream().map(e -> e.slug().valor()).toList());
  }

  /**
   * El tope es una regla del formato del sitemap, no del adaptador. Si viviera en la consulta SQL,
   * un segundo adaptador quedaría libre de respetarlo y nadie se enteraría hasta que Google
   * rechazara el archivo.
   */
  @Test
  void pideAlRepositorioElTopeDeUrlsQueAdmiteUnSitemap() {
    RepositorioMapaDelSitioDoble repositorio = new RepositorioMapaDelSitioDoble(List.of());

    new ListarMapaDelSitio(repositorio).ejecutar();

    assertEquals(50_000, repositorio.limiteRecibido);
  }

  @Test
  void unCatalogoVacioDevuelveListaVacia() {
    List<EntradaMapaDelSitio> entradas =
        new ListarMapaDelSitio(new RepositorioMapaDelSitioDoble(List.of())).ejecutar();

    assertEquals(List.of(), entradas);
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

    @Override
    public List<EntradaMapaDelSitio> listarProductosPublicados(int limite) {
      this.limiteRecibido = limite;
      return entradas;
    }
  }
}
