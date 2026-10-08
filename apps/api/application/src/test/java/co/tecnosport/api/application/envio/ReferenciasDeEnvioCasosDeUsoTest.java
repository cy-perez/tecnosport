package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.CategoriaNoEncontradaException;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.envio.MedidasDeReferencia;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Los cuatro casos de uso de la pantalla de referencias de envío del panel (adr/0071). */
class ReferenciasDeEnvioCasosDeUsoTest {

  private static final Categoria ROPA_DAMA =
      Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
  private static final Categoria JEANS =
      Categoria.crearBajo(ROPA_DAMA, "Jeans", new Slug("ropa-dama-jeans"));
  private static final Categoria BLUSAS =
      Categoria.crearBajo(ROPA_DAMA, "Blusas", new Slug("ropa-dama-blusas"));
  private static final Categoria UNISEX =
      Categoria.crear("Unisex", new Slug("calzado-unisex"), LineaCatalogo.CALZADO);
  private static final Categoria AUDIFONOS =
      Categoria.crear("Audífonos", new Slug("audifonos"), LineaCatalogo.TECNOLOGIA);

  private RepositorioReferenciasDeEnvioFalso referencias;
  private RepositorioCategorias categorias;

  @BeforeEach
  void preparar() {
    referencias = new RepositorioReferenciasDeEnvioFalso();
    categorias = new CategoriasEnMemoria(List.of(ROPA_DAMA, JEANS, BLUSAS, UNISEX, AUDIFONOS));
  }

  // --- consultar
  // -----------------------------------------------------------------------------------

  /**
   * Solo hojas de las líneas que se promedian: "Dama" es rama —ningún producto cuelga de ella— y
   * los audífonos se miden con la ficha del fabricante.
   */
  @Test
  void la_consulta_lista_solo_las_hojas_de_ropa_calzado_y_bolsos() {
    ReferenciasDeEnvio resultado =
        new ConsultarReferenciasDeEnvio(referencias, categorias).ejecutar();

    assertEquals(
        List.of("Blusas", "Jeans", "Unisex"),
        resultado.categorias().stream().map(fila -> fila.categoria().nombre()).toList());
  }

  @Test
  void la_consulta_trae_la_rama_y_el_peso_de_cada_hoja() {
    referencias.conPeso(JEANS.id(), 700);

    ReferenciasDeEnvio resultado =
        new ConsultarReferenciasDeEnvio(referencias, categorias).ejecutar();

    ReferenciasDeEnvio.CategoriaConPeso jeans = fila(resultado, "Jeans");
    assertEquals("Dama", jeans.rama());
    assertEquals(700, jeans.pesoGramos());
    assertNull(fila(resultado, "Unisex").rama(), "Unisex cuelga directo de Calzado");
  }

  /** Las que no tienen peso van en la lista: son justo las que hay que llenar. */
  @Test
  void la_consulta_incluye_las_categorias_sin_peso() {
    ReferenciasDeEnvio resultado =
        new ConsultarReferenciasDeEnvio(referencias, categorias).ejecutar();

    assertNull(fila(resultado, "Blusas").pesoGramos());
  }

  @Test
  void la_consulta_trae_las_medidas_si_existen() {
    referencias.conMedidas(40, 30, 10);

    ReferenciasDeEnvio resultado =
        new ConsultarReferenciasDeEnvio(referencias, categorias).ejecutar();

    assertEquals(Optional.of(new MedidasDeReferencia(40, 30, 10)), resultado.medidas());
  }

  // --- fijar y quitar un peso --------------------------------------------------------------------

  @Test
  void fijar_un_peso_lo_guarda() {
    new FijarPesoDeReferencia(referencias, categorias)
        .ejecutar(new FijarPesoDeReferenciaComando(JEANS.id(), 650));

    assertEquals(Optional.of(650), referencias.pesoDe(JEANS.id()));
  }

  @Test
  void fijar_un_peso_en_una_categoria_que_no_existe_falla() {
    FijarPesoDeReferencia caso = new FijarPesoDeReferencia(referencias, categorias);

    assertThrows(
        CategoriaNoEncontradaException.class,
        () -> caso.ejecutar(new FijarPesoDeReferenciaComando(UUID.randomUUID(), 300)));
  }

  @Test
  void la_tecnologia_no_admite_peso_de_referencia() {
    FijarPesoDeReferencia caso = new FijarPesoDeReferencia(referencias, categorias);

    assertThrows(
        PesoDeReferenciaNoAdmitidoException.class,
        () -> caso.ejecutar(new FijarPesoDeReferenciaComando(AUDIFONOS.id(), 300)));
    assertTrue(referencias.pesoDe(AUDIFONOS.id()).isEmpty());
  }

  /** Un peso en "Dama" no lo leería ningún producto: cuelgan de sus hojas. */
  @Test
  void una_rama_no_admite_peso_de_referencia() {
    FijarPesoDeReferencia caso = new FijarPesoDeReferencia(referencias, categorias);

    assertThrows(
        PesoDeReferenciaNoAdmitidoException.class,
        () -> caso.ejecutar(new FijarPesoDeReferenciaComando(ROPA_DAMA.id(), 500)));
  }

  @Test
  void un_peso_en_cero_se_rechaza_y_no_se_guarda() {
    FijarPesoDeReferencia caso = new FijarPesoDeReferencia(referencias, categorias);

    assertThrows(
        ExcepcionDeDominio.class,
        () -> caso.ejecutar(new FijarPesoDeReferenciaComando(JEANS.id(), 0)));
    assertTrue(referencias.pesoDe(JEANS.id()).isEmpty());
  }

  @Test
  void quitar_un_peso_lo_borra_y_quitar_uno_que_no_esta_no_falla() {
    referencias.conPeso(JEANS.id(), 700);
    QuitarPesoDeReferencia caso = new QuitarPesoDeReferencia(referencias);

    caso.ejecutar(JEANS.id());
    caso.ejecutar(JEANS.id());

    assertTrue(referencias.pesoDe(JEANS.id()).isEmpty());
  }

  // --- las medidas -------------------------------------------------------------------------------

  @Test
  void fijar_las_medidas_las_guarda() {
    new FijarMedidasDeReferencia(referencias)
        .ejecutar(new FijarMedidasDeReferenciaComando(40, 30, 5));

    assertEquals(Optional.of(new MedidasDeReferencia(40, 30, 5)), referencias.medidas());
  }

  @Test
  void unas_medidas_en_cero_se_rechazan_y_no_se_guardan() {
    FijarMedidasDeReferencia caso = new FijarMedidasDeReferencia(referencias);

    assertThrows(
        ExcepcionDeDominio.class,
        () -> caso.ejecutar(new FijarMedidasDeReferenciaComando(40, 30, 0)));
    assertTrue(referencias.medidas().isEmpty());
  }

  private static ReferenciasDeEnvio.CategoriaConPeso fila(
      ReferenciasDeEnvio resultado, String nombre) {
    return resultado.categorias().stream()
        .filter(fila -> fila.categoria().nombre().equals(nombre))
        .findFirst()
        .orElseThrow();
  }

  /** Lo justo del árbol para estas pruebas: leer. Escribir no lo hace ninguno de estos casos. */
  private record CategoriasEnMemoria(List<Categoria> todas) implements RepositorioCategorias {

    @Override
    public List<Categoria> listarTodas() {
      return todas;
    }

    @Override
    public Optional<Categoria> buscarPorId(UUID id) {
      return todas.stream().filter(categoria -> categoria.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Categoria> buscarPorSlug(Slug slug) {
      return todas.stream().filter(categoria -> categoria.slug().equals(slug)).findFirst();
    }

    @Override
    public List<Categoria> hijasDe(UUID padreId) {
      return todas.stream()
          .filter(categoria -> categoria.padreId().map(padreId::equals).orElse(false))
          .toList();
    }

    @Override
    public void guardar(Categoria categoria) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void eliminar(UUID id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean tieneProductos(UUID categoriaId) {
      return false;
    }
  }
}
