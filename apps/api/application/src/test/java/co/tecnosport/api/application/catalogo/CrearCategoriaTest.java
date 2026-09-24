package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Slug;
import org.junit.jupiter.api.Test;

class CrearCategoriaTest {

  private final RepositorioCategoriasFalso repositorio = new RepositorioCategoriasFalso();
  private final CrearCategoria crear = new CrearCategoria(repositorio);

  @Test
  void creaUnaCategoriaDePrimerNivelConElSlugDerivadoDelNombre() {
    Categoria creada =
        crear.ejecutar(
            new CrearCategoriaComando("Proyectores", null, LineaCatalogo.TECNOLOGIA, null));

    assertEquals("proyectores", creada.slug().valor());
    assertEquals(LineaCatalogo.TECNOLOGIA, creada.linea());
    assertTrue(creada.esRaiz());
    assertEquals(1, repositorio.listarTodas().size());
  }

  /** Las tildes y los espacios los resuelve {@code Slug.generarDesde}; aquí solo se comprueba. */
  @Test
  void elSlugDerivadoNoLlevaTildesNiEspacios() {
    Categoria creada =
        crear.ejecutar(
            new CrearCategoriaComando("Audífonos", null, LineaCatalogo.TECNOLOGIA, null));

    assertEquals("audifonos", creada.slug().valor());
  }

  /**
   * El caso que obliga al prefijo: "Busos" existe bajo Dama y bajo Caballero, y el slug es único en
   * toda la tabla porque el filtro de la vitrina viaja por él. Sin prefijo, la segunda no se puede
   * crear.
   */
  @Test
  void elSlugDeUnaHijaLlevaElDelPadreDelante() {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    Categoria caballero =
        Categoria.crear("Caballero", new Slug("ropa-caballero"), LineaCatalogo.ROPA);
    repositorio.conCategorias(dama, caballero);

    Categoria busosDama = crear.ejecutar(new CrearCategoriaComando("Busos", null, null, dama.id()));
    Categoria busosCaballero =
        crear.ejecutar(new CrearCategoriaComando("Busos", null, null, caballero.id()));

    assertEquals("ropa-dama-busos", busosDama.slug().valor());
    assertEquals("ropa-caballero-busos", busosCaballero.slug().valor());
  }

  @Test
  void laHijaHeredaLaLineaDelPadreAunqueLaManden() {
    Categoria dama = Categoria.crear("Dama", new Slug("bolsos-dama"), LineaCatalogo.BOLSOS);
    repositorio.conCategorias(dama);

    Categoria morrales =
        crear.ejecutar(
            new CrearCategoriaComando("Morrales", null, LineaCatalogo.TECNOLOGIA, dama.id()));

    assertEquals(LineaCatalogo.BOLSOS, morrales.linea());
    assertEquals(dama.id(), morrales.padreId().orElseThrow());
  }

  @Test
  void aceptaUnSlugEscritoAMano() {
    Categoria creada =
        crear.ejecutar(
            new CrearCategoriaComando(
                "Consolas de videojuegos", "consolas", LineaCatalogo.TECNOLOGIA, null));

    assertEquals("consolas", creada.slug().valor());
  }

  @Test
  void rechazaUnSlugYaUsado() {
    repositorio.conCategorias(
        Categoria.crear("Relojes", new Slug("relojes"), LineaCatalogo.TECNOLOGIA));

    assertThrows(
        CategoriaSlugYaExisteException.class,
        () ->
            crear.ejecutar(
                new CrearCategoriaComando("Relojes", null, LineaCatalogo.TECNOLOGIA, null)));
  }

  @Test
  void rechazaUnPadreQueNoExiste() {
    assertThrows(
        CategoriaNoEncontradaException.class,
        () ->
            crear.ejecutar(
                new CrearCategoriaComando("Camisas", null, null, java.util.UUID.randomUUID())));
  }

  /** El tercer nivel no existe: lo razona {@code ProfundidadDeCategoriaExcedidaException}. */
  @Test
  void rechazaColgarDeUnaCategoriaQueYaEsHija() {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    Categoria faldas = Categoria.crearBajo(dama, "Faldas", new Slug("ropa-dama-faldas"));
    repositorio.conCategorias(dama, faldas);

    assertThrows(
        ProfundidadDeCategoriaExcedidaException.class,
        () -> crear.ejecutar(new CrearCategoriaComando("Largas", null, null, faldas.id())));
  }

  /**
   * La otra mitad de la regla de la hoja: si "Camisas" ya tiene productos, darle hijas dejaría esos
   * productos en un nodo intermedio.
   */
  @Test
  void rechazaColgarDeUnaCategoriaQueYaTieneProductos() {
    Categoria camisas =
        Categoria.crear("Camisas", new Slug("ropa-dama-camisas"), LineaCatalogo.ROPA);
    repositorio.conCategorias(camisas);
    repositorio.conProductosEn(camisas);

    assertThrows(
        CategoriaConProductosException.class,
        () -> crear.ejecutar(new CrearCategoriaComando("Manga larga", null, null, camisas.id())));
  }

  @Test
  void rechazaUnaCategoriaDePrimerNivelSinLinea() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> crear.ejecutar(new CrearCategoriaComando("Huérfana", null, null, null)));
  }

  @Test
  void rechazaUnNombreVacio() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> crear.ejecutar(new CrearCategoriaComando("   ", "algo", LineaCatalogo.BOLSOS, null)));
  }
}
