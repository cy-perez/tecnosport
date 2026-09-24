package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditarCategoriaTest {

  private final RepositorioCategoriasFalso repositorio = new RepositorioCategoriasFalso();
  private final EditarCategoria editar = new EditarCategoria(repositorio);

  /**
   * El caso real que motivó el método: {@code V63} renombró "Consolas" a "Consolas de videojuegos"
   * y dejó el slug quieto, porque el slug está en URLs publicadas. Lo mismo tiene que valer desde
   * el panel — si renombrar arrastrara el slug, corregir una tilde rompería enlaces vivos.
   */
  @Test
  void renombrarNoCambiaElSlug() {
    Categoria consolas =
        Categoria.crear("Consolas", new Slug("consolas"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(consolas);

    Categoria editada =
        editar.ejecutar(
            new EditarCategoriaComando(consolas.id(), "Consolas de videojuegos", null, null, null));

    assertEquals("Consolas de videojuegos", editada.nombre());
    assertEquals("consolas", editada.slug().valor());
    assertEquals(LineaCatalogo.TECNOLOGIA, editada.linea());
  }

  @Test
  void cambiaElSlugSiLoEscriben() {
    Categoria bodis = Categoria.crear("Bodis", new Slug("ropa-dama-bodis"), LineaCatalogo.ROPA);
    repositorio.conCategorias(bodis);

    Categoria editada =
        editar.ejecutar(
            new EditarCategoriaComando(bodis.id(), "Bodis", "ropa-dama-bodys", null, null));

    assertEquals("ropa-dama-bodys", editada.slug().valor());
  }

  @Test
  void conservarSuPropioSlugNoCuentaComoRepetido() {
    Categoria faldas = Categoria.crear("Faldas", new Slug("ropa-dama-faldas"), LineaCatalogo.ROPA);
    repositorio.conCategorias(faldas);

    Categoria editada =
        editar.ejecutar(
            new EditarCategoriaComando(
                faldas.id(), "Faldas largas", "ropa-dama-faldas", null, null));

    assertEquals("Faldas largas", editada.nombre());
  }

  @Test
  void rechazaUnSlugQueYaTieneOtra() {
    Categoria faldas = Categoria.crear("Faldas", new Slug("ropa-dama-faldas"), LineaCatalogo.ROPA);
    Categoria shorts = Categoria.crear("Shorts", new Slug("ropa-dama-shorts"), LineaCatalogo.ROPA);
    repositorio.conCategorias(faldas, shorts);

    assertThrows(
        CategoriaSlugYaExisteException.class,
        () ->
            editar.ejecutar(
                new EditarCategoriaComando(shorts.id(), "Shorts", "ropa-dama-faldas", null, null)));
  }

  @Test
  void moverUnaCategoriaBajoOtraLeCambiaLaLinea() {
    Categoria dama = Categoria.crear("Dama", new Slug("bolsos-dama"), LineaCatalogo.BOLSOS);
    Categoria sueltas = Categoria.crear("Morrales", new Slug("morrales"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(dama, sueltas);

    Categoria movida =
        editar.ejecutar(
            new EditarCategoriaComando(sueltas.id(), "Morrales", null, null, dama.id()));

    assertEquals(LineaCatalogo.BOLSOS, movida.linea());
    assertEquals(dama.id(), movida.padreId().orElseThrow());
  }

  @Test
  void sacarlaDeSuRamaLaDejaEnLaLineaQueLeDigan() {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    Categoria licras = Categoria.crearBajo(dama, "Licras", new Slug("ropa-dama-licras"));
    repositorio.conCategorias(dama, licras);

    Categoria movida =
        editar.ejecutar(
            new EditarCategoriaComando(licras.id(), "Licras", null, LineaCatalogo.CALZADO, null));

    assertTrue(movida.esRaiz());
    assertEquals(LineaCatalogo.CALZADO, movida.linea());
  }

  @Test
  void rechazaColgarlaDeSiMisma() {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    repositorio.conCategorias(dama);

    assertThrows(
        CicloDeCategoriasException.class,
        () ->
            editar.ejecutar(new EditarCategoriaComando(dama.id(), "Dama", null, null, dama.id())));
  }

  @Test
  void rechazaColgarlaDeSuPropiaHija() {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    Categoria faldas = Categoria.crearBajo(dama, "Faldas", new Slug("ropa-dama-faldas"));
    repositorio.conCategorias(dama, faldas);

    assertThrows(
        CicloDeCategoriasException.class,
        () ->
            editar.ejecutar(
                new EditarCategoriaComando(dama.id(), "Dama", null, null, faldas.id())));
  }

  /** La quinta regla, la que se olvida: una rama con hojas no puede pasar a ser hoja de otra. */
  @Test
  void rechazaMoverUnaRamaConHojasBajoOtraRama() {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    Categoria caballero =
        Categoria.crear("Caballero", new Slug("ropa-caballero"), LineaCatalogo.ROPA);
    Categoria faldas = Categoria.crearBajo(dama, "Faldas", new Slug("ropa-dama-faldas"));
    repositorio.conCategorias(dama, caballero, faldas);

    assertThrows(
        ProfundidadDeCategoriaExcedidaException.class,
        () ->
            editar.ejecutar(
                new EditarCategoriaComando(dama.id(), "Dama", null, null, caballero.id())));
  }

  @Test
  void rechazaMoverlaBajoUnaCategoriaConProductos() {
    Categoria camisas =
        Categoria.crear("Camisas", new Slug("ropa-dama-camisas"), LineaCatalogo.ROPA);
    Categoria blusas = Categoria.crear("Blusas", new Slug("ropa-dama-blusas"), LineaCatalogo.ROPA);
    repositorio.conCategorias(camisas, blusas);
    repositorio.conProductosEn(camisas);

    assertThrows(
        CategoriaConProductosException.class,
        () ->
            editar.ejecutar(
                new EditarCategoriaComando(blusas.id(), "Blusas", null, null, camisas.id())));
  }

  @Test
  void rechazaUnaCategoriaQueNoExiste() {
    assertThrows(
        CategoriaNoEncontradaException.class,
        () ->
            editar.ejecutar(
                new EditarCategoriaComando(UUID.randomUUID(), "Nada", null, null, null)));
  }
}
