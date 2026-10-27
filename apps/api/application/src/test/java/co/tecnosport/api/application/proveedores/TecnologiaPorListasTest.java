package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.ObjetoDeImagenNoEncontradoException;
import co.tecnosport.api.application.catalogo.TarifasDePrueba;
import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioAtributosFijo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioCategoriasFijo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioInventarioEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioMarcasFijo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosDeProveedorEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.application.proveedores.tecnologia.AprobarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.AprobarBorradorTecnologiaComando;
import co.tecnosport.api.application.proveedores.tecnologia.BorradorTecnologiaNoEliminableException;
import co.tecnosport.api.application.proveedores.tecnologia.DefinirPaletaDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.EditarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.EditarFichaBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.EliminarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.EliminarBorradoresTecnologiaSinAprobar;
import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologiaComando;
import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologiaComando.Modo;
import co.tecnosport.api.application.proveedores.tecnologia.InventarioDeLaListaPendienteException;
import co.tecnosport.api.application.proveedores.tecnologia.ListaDeTecnologiaDesactualizadaException;
import co.tecnosport.api.application.proveedores.tecnologia.ListaDeTecnologiaYaImportadaException;
import co.tecnosport.api.application.proveedores.tecnologia.ProveedorSinListasException;
import co.tecnosport.api.application.proveedores.tecnologia.RechazarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ReordenarFotosBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioBorradoresTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioListasDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioVariantesDeProveedor;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.catalogo.EstadoDisponibilidad;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia.Eleccion;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoDeBorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.VarianteDeProveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * La tecnología por listas de punta a punta: importar, revisar, aprobar, y lo que hacen las listas
 * siguientes con lo que ya se vende.
 */
class TecnologiaPorListasTest {

  private static final Instant AHORA = Instant.parse("2026-10-08T15:00:00Z");
  private static final LocalDate LUNES = LocalDate.parse("2026-10-05");
  private static final LocalDate JUEVES = LocalDate.parse("2026-10-08");
  private static final int EXISTENCIA = 2;

  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();
  private final RepositorioProductosEnMemoria productos = new RepositorioProductosEnMemoria();
  private final RepositorioProductosDeProveedorEnMemoria productosDeProveedor =
      new RepositorioProductosDeProveedorEnMemoria(productos);
  private final RepositorioInventarioEnMemoria inventario = new RepositorioInventarioEnMemoria();
  private final RepositorioAtributosFijo atributos = new RepositorioAtributosFijo();
  private final BorradoresEnMemoria borradores = new BorradoresEnMemoria();
  private final VinculosEnMemoria vinculos = new VinculosEnMemoria();
  private final ListasEnMemoria listas = new ListasEnMemoria();
  private final RelojFalso reloj = new RelojFalso(AHORA);
  private final ApoyoDeIngesta.AlmacenEnMemoria privado = new ApoyoDeIngesta.AlmacenEnMemoria();
  private final ApoyoDeCatalogoParaIngesta.AlmacenDeImagenesEnMemoria publico =
      new ApoyoDeCatalogoParaIngesta.AlmacenDeImagenesEnMemoria();

  private Proveedor proveedor;

  @BeforeEach
  void unProveedorDeTecnologia() {
    proveedor =
        Proveedor.crear(
            "Tecnología Medellín",
            LineaCatalogo.TECNOLOGIA,
            "+57 300 765 4321",
            "Tecno",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    proveedores.guardar(proveedor);
    atributos.atributos.add(Atributo.crear("RAM", TipoAtributo.TEXTO, List.of()));
    atributos.atributos.add(Atributo.crear("Almacenamiento", TipoAtributo.TEXTO, List.of()));
    atributos.atributos.add(Atributo.crear("SIM", TipoAtributo.TEXTO, List.of()));
  }

  // --- Lo que llega -------------------------------------------------------------------------

  private static final ModeloDeLista A17 =
      new ModeloDeLista(
          "samsung-galaxy-a17-5g",
          "Samsung Galaxy A17 5G",
          "Samsung",
          "celulares",
          "El Galaxy A17 5G.",
          null,
          List.of("Negro", "Gris", "Azul"));

  private static ConfiguracionTecnologia config(String sku, long costo, String sim) {
    return ConfiguracionTecnologia.deLista(
        sku,
        "Samsung Galaxy A17 5G 8GB RAM 256GB " + sim,
        "8GB",
        "256GB",
        sim,
        Dinero.deCop(costo),
        Dinero.deCop(849_900),
        List.of());
  }

  private static ConfiguracionTecnologia conColores(
      String sku, long costo, String sim, String... colores) {
    return ConfiguracionTecnologia.deLista(
        sku,
        "Samsung Galaxy A17 5G 8GB RAM 256GB " + sim,
        "8GB",
        "256GB",
        sim,
        Dinero.deCop(costo),
        Dinero.deCop(849_900),
        List.of(colores));
  }

  private static final String UNA_SIM = "a17-256-1-sim";
  private static final String DUAL = "a17-256-dual-sim";

  /** El inventario de la lista: renueva y retira, y no propone nada. */
  private ImportarListaDeTecnologiaComando lista(
      LocalDate fecha,
      List<ConfiguracionTecnologia> configuraciones,
      List<String> desaparecidas,
      List<String> modelosDesaparecidos) {
    return new ImportarListaDeTecnologiaComando(
        proveedor.id(),
        fecha,
        Modo.INVENTARIO,
        delA17(configuraciones),
        desaparecidas,
        modelosDesaparecidos);
  }

  /** Los borradores de la misma lista: proponen, y no tocan costo ni existencia. */
  private ImportarListaDeTecnologiaComando deBorradores(
      LocalDate fecha, List<ConfiguracionTecnologia> configuraciones) {
    return new ImportarListaDeTecnologiaComando(
        proveedor.id(), fecha, Modo.BORRADORES, delA17(configuraciones), List.of(), List.of());
  }

  /**
   * Los borradores de una lista van después de su inventario (ADR-0079): importa el inventario de
   * esa misma lista si todavía no entró, y después los borradores.
   */
  private ImportarListaDeTecnologia.Resultado proponer(
      LocalDate fecha, List<ConfiguracionTecnologia> configuraciones) {
    ImportarListaDeTecnologiaComando inventarioDeLaLista =
        lista(fecha, configuraciones, List.of(), List.of());
    if (!listas.yaEntro(proveedor.id(), inventarioDeLaLista.huella())) {
      importar(inventarioDeLaLista);
    }
    return importar(deBorradores(fecha, configuraciones));
  }

  private static List<ImportarListaDeTecnologiaComando.Modelo> delA17(
      List<ConfiguracionTecnologia> configuraciones) {
    return configuraciones.isEmpty()
        ? List.of()
        : List.of(new ImportarListaDeTecnologiaComando.Modelo(A17, configuraciones));
  }

  private ImportarListaDeTecnologia.Resultado importar(ImportarListaDeTecnologiaComando comando) {
    return new ImportarListaDeTecnologia(
            proveedores,
            productosDeProveedor,
            productos,
            borradores,
            vinculos,
            inventario,
            listas,
            reloj,
            EXISTENCIA)
        .ejecutar(comando);
  }

  private Producto aprobar(UUID borradorId) {
    return new AprobarBorradorTecnologia(
            borradores,
            vinculos,
            productos,
            productosDeProveedor,
            listas,
            new RepositorioMarcasFijo(),
            new RepositorioCategoriasFijo(),
            atributos,
            new AgregarVariante(
                productos, atributos, inventario, reloj, false, TarifasDePrueba.WOMPI),
            privado,
            publico,
            transaccionDelBorrado,
            EXISTENCIA)
        .ejecutar(
            new AprobarBorradorTecnologiaComando(
                borradorId,
                ApoyoDeCatalogoParaIngesta.MARCA.id(),
                ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO.id()));
  }

  /**
   * Las cuatro fotos que exige un modelo nuevo, con sus archivos en el bucket privado: dos anchos
   * AVIF y la vista previa JPEG cada una.
   */
  private void conCuatroFotos(BorradorTecnologia borrador) {
    String prefijo =
        "proveedores/" + proveedor.id() + "/borradores-tecnologia/" + borrador.id() + "/";
    for (int i = 1; i <= BorradorTecnologia.MINIMO_DE_FOTOS; i++) {
      String mayor = prefijo + i + "-1200.avif";
      String menor = prefijo + i + "-480.avif";
      String vista = prefijo + i + ".jpg";
      privado.guardar(mayor, "image/avif", new byte[58]);
      privado.guardar(menor, "image/avif", new byte[12]);
      privado.guardar(vista, "image/jpeg", new byte[30]);
      borrador.agregarFoto(
          new FotoDeBorradorTecnologia(
              UUID.randomUUID(),
              List.of(
                  new FotoDeBorradorTecnologia.Variante(1200, mayor, 58),
                  new FotoDeBorradorTecnologia.Variante(480, menor, 12)),
              vista,
              1200,
              String.valueOf(i).repeat(64),
              "Galaxy A17 sobre fondo blanco",
              "Galaxy A17 on a white background",
              AHORA));
    }
    borradores.actualizar(borrador);
  }

  private BorradorTecnologia enRevision() {
    return borradores.buscarEnRevisionParaActualizar(proveedor.id(), A17.idModelo()).orElseThrow();
  }

  /** Importa el A17 con la de una SIM y la dual, y aprueba la de una SIM en negro y gris. */
  private Producto unA17QueYaSeVende() {
    proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM"), config(DUAL, 690_000, "Dual SIM")));
    BorradorTecnologia borrador = enRevision();
    new EditarBorradorTecnologia(borradores)
        .ejecutar(
            borrador.id(),
            List.of(new Eleccion(UNA_SIM, List.of("Negro", "Gris"), Dinero.deCop(849_900))));
    conCuatroFotos(enRevision());
    return aprobar(borrador.id());
  }

  private int disponible(UUID varianteId) {
    return inventario.porVariante.get(varianteId).saldoDisponible(AHORA);
  }

  private static String valor(Variante variante, String atributo) {
    return variante.atributos().stream()
        .filter(v -> v.atributo().nombre().equals(atributo))
        .map(ValorAtributo::valor)
        .findFirst()
        .orElse(null);
  }

  // --- Un modelo nuevo ----------------------------------------------------------------------

  @Test
  void unModeloQueNoSeVendeEntraComoBorradorConSusConfiguraciones() {
    ImportarListaDeTecnologia.Resultado resultado =
        proponer(
            LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM"), config(DUAL, 690_000, "Dual SIM")));

    assertEquals(1, resultado.borradoresNuevos());
    BorradorTecnologia borrador = enRevision();
    assertEquals(
        List.of(UNA_SIM, DUAL),
        borrador.configuraciones().stream().map(ConfiguracionTecnologia::sku).toList());
    assertTrue(borrador.productoId().isEmpty());
    assertTrue(productos.porId.isEmpty(), "la lista sola no crea catálogo");
  }

  @Test
  void otraListaActualizaElMismoBorradorYNoAbreOtro() {
    proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")));
    ImportarListaDeTecnologia.Resultado resultado =
        proponer(JUEVES, List.of(config(UNA_SIM, 660_000, "1 SIM")));

    assertEquals(1, resultado.borradoresActualizados());
    assertEquals(1, borradores.porId.size());
    assertEquals(Dinero.deCop(660_000), enRevision().configuraciones().getFirst().costoProveedor());
  }

  @Test
  void aprobarCreaUnaVariantePorColorConSusAtributosYLaExistenciaDeLaLista() {
    Producto producto = unA17QueYaSeVende();

    assertEquals(EstadoProducto.PUBLICADO, producto.estado(), "las fotos llegaron con el borrador");
    assertEquals("Samsung Galaxy A17 5G", producto.nombre());
    assertEquals(Dinero.deCop(675_000), producto.precioProveedor().orElseThrow());
    assertEquals(
        2, producto.variantes().size(), "negro y gris de la de una SIM; la dual no se vende");
    Variante negra =
        producto.variantes().stream()
            .filter(v -> "Negro".equals(valor(v, "Color")))
            .findFirst()
            .orElseThrow();
    assertEquals("8GB", valor(negra, "RAM"));
    assertEquals("256GB", valor(negra, "Almacenamiento"));
    assertEquals("1 SIM", valor(negra, "SIM"));
    // De contado el que se aprobó; publicado con la comisión: (849.900 + 833) / 0,968465 → 879.000.
    assertEquals(Dinero.deCop(849_900), negra.precioContado());
    assertEquals(Dinero.deCop(879_000), negra.precio());
    assertEquals(EXISTENCIA, disponible(negra.id()));
    VarianteDeProveedor vinculo = vinculos.porVariante.get(negra.id());
    assertEquals(UNA_SIM, vinculo.configuracion());
    assertEquals(Dinero.deCop(675_000), vinculo.costo());
    assertEquals(EstadoBorrador.APROBADO, borradores.porId.values().iterator().next().estado());
  }

  @Test
  void unModeloRechazadoNoSeVuelveAProponer() {
    proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")));
    new RechazarBorradorTecnologia(borradores).ejecutar(enRevision().id(), "No vendemos esta gama");

    ImportarListaDeTecnologia.Resultado resultado =
        proponer(JUEVES, List.of(config(UNA_SIM, 660_000, "1 SIM")));

    assertEquals(0, resultado.borradoresNuevos());
    assertEquals(List.of("Samsung Galaxy A17 5G"), resultado.modelosYaDecididos());
    assertTrue(borradores.buscarEnRevisionParaActualizar(proveedor.id(), A17.idModelo()).isEmpty());
  }

  // --- La lista en dos tiempos (ADR-0079) ---------------------------------------------------

  @Test
  void elInventarioNoProponeYDiceQueModelosEsperanBorrador() {
    ImportarListaDeTecnologia.Resultado resultado =
        importar(
            lista(
                LUNES,
                List.of(config(UNA_SIM, 675_000, "1 SIM"), config(DUAL, 690_000, "Dual SIM")),
                List.of(),
                List.of()));

    assertEquals(0, resultado.borradoresNuevos());
    assertTrue(borradores.porId.isEmpty(), "el borrador espera a las fotos");
    assertEquals(List.of(A17.idModelo()), resultado.modelosPorProponer());
    assertEquals(List.of(), resultado.configuracionesPorProponer());
  }

  @Test
  void elInventarioDiceQueConfiguracionesNuevasDeUnProductoEsperanBorrador() {
    unA17QueYaSeVende();
    String nueva = "a17-128-1-sim";

    ImportarListaDeTecnologia.Resultado resultado =
        importar(
            lista(
                JUEVES,
                List.of(config(UNA_SIM, 660_000, "1 SIM"), config(nueva, 600_000, "1 SIM")),
                List.of(),
                List.of()));

    assertEquals(1, resultado.productosRenovados());
    assertEquals(List.of(nueva), resultado.configuracionesPorProponer());
    assertEquals(List.of(), resultado.modelosPorProponer(), "el modelo ya se vende: no pide fotos");
    assertTrue(borradores.buscarEnRevisionParaActualizar(proveedor.id(), A17.idModelo()).isEmpty());
  }

  @Test
  void losBorradoresNoTocanCostoNiExistencia() {
    Producto producto = unA17QueYaSeVende();
    Variante negra = producto.variantes().getFirst();
    importar(lista(JUEVES, List.of(config(UNA_SIM, 660_000, "1 SIM")), List.of(), List.of()));
    Inventario libro = inventario.porVariante.get(negra.id());
    libro.reservar(2, null, AHORA); // se vendieron las dos
    inventario.guardar(libro);

    ImportarListaDeTecnologia.Resultado resultado =
        importar(deBorradores(JUEVES, List.of(config(UNA_SIM, 600_000, "1 SIM"))));

    assertEquals(0, resultado.productosRenovados());
    assertEquals(0, resultado.variantesRepuestas());
    assertEquals(0, disponible(negra.id()), "lo vendido no se repone");
    assertEquals(Dinero.deCop(660_000), vinculos.porVariante.get(negra.id()).costo());
  }

  @Test
  void losBorradoresDeUnaListaCuyoInventarioNoEntroNoEntran() {
    assertThrows(
        InventarioDeLaListaPendienteException.class,
        () -> importar(deBorradores(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")))));

    importar(lista(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")), List.of(), List.of()));
    importar(lista(JUEVES, List.of(), List.of(), List.of()));
    assertThrows(
        ListaDeTecnologiaDesactualizadaException.class,
        () -> importar(deBorradores(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")))),
        "ya entró una más nueva");
    assertTrue(borradores.porId.isEmpty());
  }

  @Test
  void elInventarioRefrescaElBorradorQueYaEstaEnRevision() {
    proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM"), config(DUAL, 690_000, "Dual SIM")));

    ImportarListaDeTecnologia.Resultado resultado =
        importar(lista(JUEVES, List.of(config(UNA_SIM, 660_000, "1 SIM")), List.of(), List.of()));

    assertEquals(1, resultado.borradoresActualizados());
    assertEquals(0, resultado.borradoresNuevos(), "refrescar no es proponer");
    BorradorTecnologia borrador = enRevision();
    assertEquals(
        List.of(UNA_SIM),
        borrador.configuraciones().stream().map(ConfiguracionTecnologia::sku).toList(),
        "la dual ya no vino: sale del borrador");
    assertEquals(Dinero.deCop(660_000), borrador.configuraciones().getFirst().costoProveedor());
  }

  @Test
  void laHuellaNoDependeDelModoYEsLaDeAntes() {
    ImportarListaDeTecnologiaComando inventario =
        lista(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")), List.of(), List.of());
    ImportarListaDeTecnologiaComando borradoresDeLaMisma =
        deBorradores(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")));
    assertEquals(inventario.huella(), borradoresDeLaMisma.huella());

    // El texto con que se calculaban las huellas ya registradas, antes de que existiera el modo:
    // si cambia, la lista del día del despliegue vuelve a entrar y repone lo vendido.
    String deAntes =
        "ImportarListaDeTecnologiaComando[proveedorId="
            + proveedor.id()
            + ", fechaLista="
            + LUNES
            + ", modelos="
            + inventario.modelos()
            + ", configuracionesDesaparecidas=[], modelosDesaparecidos=[]]";
    assertEquals(sha256(deAntes), inventario.huella());
  }

  private static String sha256(String texto) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void losBorradoresDeLaMismaListaEntranDosVeces() {
    importar(lista(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")), List.of(), List.of()));
    ImportarListaDeTecnologiaComando lunes =
        deBorradores(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")));

    importar(lunes);
    ImportarListaDeTecnologia.Resultado otraVez = importar(lunes);

    assertEquals(1, otraVez.borradoresActualizados(), "las fotos de otro modelo llegaron después");
    assertEquals(1, borradores.porId.size());
  }

  @Test
  void losBorradoresDeUnaListaViejaNoEntran() {
    importar(lista(JUEVES, List.of(), List.of(), List.of()));

    assertThrows(
        ListaDeTecnologiaDesactualizadaException.class,
        () -> importar(deBorradores(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")))));
    assertTrue(borradores.porId.isEmpty());
  }

  @Test
  void unaImportacionDeBorradoresNoRetiraNada() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ImportarListaDeTecnologiaComando(
                proveedor.id(), LUNES, Modo.BORRADORES, List.of(), List.of(UNA_SIM), List.of()));
  }

  // --- Las fotos del borrador pasan al producto (ADR-0079) -----------------------------------

  @Test
  void aprobarCopiaLasFotosAlBucketPublicoConLaPrimeraDePrincipal() {
    Producto producto = unA17QueYaSeVende();

    assertTrue(producto.imagenPrincipal().isPresent());
    assertEquals(BorradorTecnologia.MINIMO_DE_FOTOS - 1, producto.galeria().size());
    var principal = producto.imagenPrincipal().orElseThrow();
    assertEquals("1".repeat(64), principal.hash().valor());
    assertEquals(List.of(480, 1200), principal.variantes().stream().map(v -> v.ancho()).toList());
    String url = principal.variantes().getLast().url();
    assertTrue(
        url.startsWith("https://publico.local/productos/" + producto.id() + "/principal-"), url);
    assertTrue(url.endsWith(".avif"), url);
    assertTrue(principal.urlVistaPrevia().orElseThrow().endsWith(".jpg"));
    assertTrue(principal.enLaFicha(), "la principal también abre la galería de la ficha");
    assertTrue(
        producto.galeria().getFirst().variantes().getFirst().url().contains("/galeria-"),
        "las demás van a la galería");
    // 4 fotos × (2 anchos + vista previa)
    assertEquals(12, publico.objetos.size());
  }

  // --- Lo que quien revisa edita del borrador (23/10/2026) --------------------------------------

  /** Importa el A17, elige la de una SIM en negro y le pone sus cuatro fotos, sin aprobar. */
  private BorradorTecnologia unA17ListoParaAprobar() {
    proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")));
    BorradorTecnologia borrador = enRevision();
    new EditarBorradorTecnologia(borradores)
        .ejecutar(
            borrador.id(), List.of(new Eleccion(UNA_SIM, List.of("Negro"), Dinero.deCop(849_900))));
    conCuatroFotos(enRevision());
    return enRevision();
  }

  @Test
  void aprobarPublicaLaFichaEditadaYNoLaDeLaSkill() {
    BorradorTecnologia borrador = unA17ListoParaAprobar();

    new EditarFichaBorradorTecnologia(borradores)
        .ejecutar(borrador.id(), "El Galaxy A17 5G, sin la nota que no queremos publicar.");
    Producto producto = aprobar(borrador.id());

    assertEquals("El Galaxy A17 5G, sin la nota que no queremos publicar.", producto.descripcion());
  }

  @Test
  void otraListaNoPisaLaFichaEditada() {
    BorradorTecnologia borrador = unA17ListoParaAprobar();
    new EditarFichaBorradorTecnologia(borradores).ejecutar(borrador.id(), "Editada a mano.");

    proponer(JUEVES, List.of(config(UNA_SIM, 660_000, "1 SIM")));

    assertEquals("Editada a mano.", enRevision().descripcion());
  }

  @Test
  void laPrincipalQueSeEligeEnElPanelEsLaPrincipalDelProducto() {
    BorradorTecnologia borrador = unA17ListoParaAprobar();
    List<UUID> orden = new ArrayList<>(borrador.fotos().stream().map(f -> f.id()).toList());
    Collections.reverse(orden);

    new ReordenarFotosBorradorTecnologia(borradores).ejecutar(borrador.id(), orden);
    Producto producto = aprobar(borrador.id());

    assertEquals(
        String.valueOf(BorradorTecnologia.MINIMO_DE_FOTOS).repeat(64),
        producto.imagenPrincipal().orElseThrow().hash().valor(),
        "la última que entró quedó primera");
  }

  @Test
  void unBorradorRechazadoNoSeEditaNiSeReordena() {
    BorradorTecnologia borrador = unA17ListoParaAprobar();
    List<UUID> orden = borrador.fotos().stream().map(f -> f.id()).toList();
    new RechazarBorradorTecnologia(borradores).ejecutar(borrador.id(), "No vendemos esta gama");

    assertThrows(
        BorradorNoEditableException.class,
        () -> new EditarFichaBorradorTecnologia(borradores).ejecutar(borrador.id(), "Otra."));
    assertThrows(
        BorradorNoEditableException.class,
        () -> new ReordenarFotosBorradorTecnologia(borradores).ejecutar(borrador.id(), orden));
  }

  @Test
  void unBorradorQueNoExisteNoSeEdita() {
    assertThrows(
        BorradorNoEncontradoException.class,
        () -> new EditarFichaBorradorTecnologia(borradores).ejecutar(UUID.randomUUID(), "Otra."));
  }

  // --- Borrar borradores (24/10/2026) -------------------------------------------------------

  /**
   * La transacción propia del borrado, que anota cuántos objetos tenía el bucket al confirmar: los
   * objetos se borran después, nunca dentro.
   */
  private final class TransaccionQueMiraElBucket implements EnTransaccionPropia {
    int veces;
    final List<Integer> objetosAlConfirmar = new ArrayList<>();

    @Override
    public <T> T ejecutar(java.util.function.Supplier<T> trabajo) {
      T resultado = trabajo.get();
      veces++;
      objetosAlConfirmar.add(privado.objetos.size());
      return resultado;
    }
  }

  private final TransaccionQueMiraElBucket transaccionDelBorrado = new TransaccionQueMiraElBucket();

  private EliminarBorradorTecnologia eliminar() {
    return new EliminarBorradorTecnologia(borradores, privado, transaccionDelBorrado);
  }

  /**
   * Revisión adversarial del 27 de octubre de 2026: el borrado se llevaba solo lo que nombraban las
   * fotos del borrador. Una subida firmada que el panel no confirmó deja un objeto bajo su prefijo
   * que ninguna fila nombra, y quedaba huérfano para siempre.
   */
  @Test
  void borrarUnoSeLlevaTodoSuPrefijoAunqueNingunaFotoLoNombre() {
    BorradorTecnologia borrador = unA17ListoParaAprobar();
    String huerfano =
        "proveedores/" + proveedor.id() + "/borradores-tecnologia/" + borrador.id() + "/x.avif";
    privado.guardar(huerfano, "image/avif", new byte[3]);
    String delVecino = "proveedores/" + proveedor.id() + "/borradores-tecnologia/otro/1.avif";
    privado.guardar(delVecino, "image/avif", new byte[3]);

    eliminar().ejecutar(borrador.id());

    assertFalse(privado.objetos.containsKey(huerfano));
    assertTrue(privado.objetos.containsKey(delVecino), "lo de otro borrador se queda");
  }

  /**
   * Y al aprobar, los originales del bucket privado se quedaban para siempre: el producto ya tiene
   * sus copias en el público. Se borran después de confirmar la aprobación, nunca antes.
   */
  @Test
  void aprobarBorraLosOriginalesDelBucketPrivadoDespuesDeConfirmar() {
    BorradorTecnologia borrador = unA17ListoParaAprobar();
    String prefijo =
        "proveedores/" + proveedor.id() + "/borradores-tecnologia/" + borrador.id() + "/";
    long antes = privado.objetos.keySet().stream().filter(k -> k.startsWith(prefijo)).count();

    aprobar(borrador.id());

    assertEquals(
        List.of((int) privado.objetos.size() + (int) antes),
        transaccionDelBorrado.objetosAlConfirmar,
        "al confirmar seguían todos");
    assertTrue(privado.objetos.keySet().stream().noneMatch(k -> k.startsWith(prefijo)));
    assertFalse(publico.objetos.isEmpty(), "el producto tiene sus copias en el público");
  }

  /**
   * Revisión adversarial del 27 de octubre de 2026: las fotos se borraban del bucket antes que la
   * fila y dentro de la transacción. Un fallo después las dejaba borradas con la fila revertida.
   */
  @Test
  void borrarUnoConfirmaLaFilaAntesDeTocarElBucket() {
    BorradorTecnologia borrador = unA17ListoParaAprobar();
    int antes = privado.objetos.size();

    int borrados = eliminar().ejecutar(borrador.id());

    assertEquals(1, transaccionDelBorrado.veces);
    assertEquals(List.of(antes), transaccionDelBorrado.objetosAlConfirmar, "nada antes del commit");
    assertEquals(antes - borrados, privado.objetos.size(), "y todo después");
  }

  @Test
  void borrarUnoEnRevisionSeLlevaSusFotosDelBucket() {
    BorradorTecnologia borrador = unA17ListoParaAprobar();
    int antes = privado.objetos.size();

    int borrados = eliminar().ejecutar(borrador.id());

    assertEquals(BorradorTecnologia.MINIMO_DE_FOTOS * 3, borrados, "dos anchos y la vista previa");
    assertEquals(antes - borrados, privado.objetos.size());
    assertTrue(borradores.porId.isEmpty());
  }

  @Test
  void unAprobadoNoSeBorraNiPierdeSusFotos() {
    BorradorTecnologia borrador = unA17ListoParaAprobar();
    aprobar(borrador.id());
    int antes = privado.objetos.size();

    assertThrows(
        BorradorTecnologiaNoEliminableException.class, () -> eliminar().ejecutar(borrador.id()));
    assertEquals(antes, privado.objetos.size());
    assertTrue(borradores.porId.containsKey(borrador.id()));
  }

  @Test
  void borrarUnRechazadoDejaQueLaListaLoVuelvaAProponer() {
    proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")));
    BorradorTecnologia borrador = enRevision();
    new RechazarBorradorTecnologia(borradores).ejecutar(borrador.id(), "No vendemos esta gama");
    assertEquals(0, proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM"))).borradoresNuevos());

    eliminar().ejecutar(borrador.id());

    assertEquals(1, proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM"))).borradoresNuevos());
  }

  @Test
  void unoQueNoExisteNoSeBorra() {
    assertThrows(BorradorNoEncontradoException.class, () -> eliminar().ejecutar(UUID.randomUUID()));
  }

  @Test
  void elBorradoEnBloqueVaPorTandasYDejaLosAprobados() {
    BorradorTecnologia aprobado = unA17ListoParaAprobar();
    aprobar(aprobado.id());
    for (int i = 0; i < 3; i++) {
      BorradorTecnologia otro =
          BorradorTecnologia.nuevo(
              proveedor.id(),
              new ModeloDeLista(
                  "modelo-" + i, "Modelo " + i, null, null, "Ficha " + i, null, List.of()),
              List.of(config("sku-" + i, 600_000, "1 SIM")),
              null,
              AHORA,
              AHORA);
      borradores.guardar(otro);
    }
    EliminarBorradoresTecnologiaSinAprobar enBloque =
        new EliminarBorradoresTecnologiaSinAprobar(borradores, eliminar());
    assertEquals(3, enBloque.contar());
    int transaccionesAntes = transaccionDelBorrado.veces;

    BorradoresEliminados primera = enBloque.ejecutar(2);
    BorradoresEliminados segunda = enBloque.ejecutar(2);

    assertEquals(2, primera.eliminados());
    assertEquals(1, primera.quedan());
    assertEquals(1, segunda.eliminados());
    assertEquals(0, segunda.quedan());
    assertEquals(1, borradores.porId.size(), "el aprobado se queda");
    assertTrue(borradores.porId.containsKey(aprobado.id()));
    assertEquals(
        3,
        transaccionDelBorrado.veces - transaccionesAntes,
        "una transacción por borrador, no por tanda");
  }

  @Test
  void unBorradorQueNoVinoEnLaUltimaListaNoSeAprueba() {
    proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")));
    BorradorTecnologia borrador = enRevision();
    new EditarBorradorTecnologia(borradores)
        .ejecutar(
            borrador.id(), List.of(new Eleccion(UNA_SIM, List.of("Negro"), Dinero.deCop(849_900))));
    conCuatroFotos(enRevision());
    // El jueves el modelo ya no vino: el inventario no tiene con qué refrescar su borrador.
    importar(lista(JUEVES, List.of(), List.of(), List.of()));

    ExcepcionDeDominio error = assertThrows(ExcepcionDeDominio.class, () -> aprobar(borrador.id()));
    assertTrue(error.getMessage().contains("no vino en ella"), error.getMessage());
    assertTrue(productos.porId.isEmpty());
  }

  @Test
  void alAprobarSoloTieneExistenciaElColorQueLaListaTrae() {
    proponer(LUNES, List.of(conColores(UNA_SIM, 675_000, "1 SIM", "Negro")));
    BorradorTecnologia borrador = enRevision();
    new EditarBorradorTecnologia(borradores)
        .ejecutar(
            borrador.id(),
            List.of(new Eleccion(UNA_SIM, List.of("Negro", "Gris"), Dinero.deCop(849_900))));
    conCuatroFotos(enRevision());

    Producto producto = aprobar(borrador.id());

    assertEquals(EXISTENCIA, disponible(variante(producto, "Negro").id()));
    assertEquals(0, disponible(variante(producto, "Gris").id()), "la lista no dice gris");
  }

  @Test
  void unModeloNuevoSinSusCuatroFotosNoSeAprueba() {
    proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")));
    BorradorTecnologia borrador = enRevision();
    new EditarBorradorTecnologia(borradores)
        .ejecutar(
            borrador.id(), List.of(new Eleccion(UNA_SIM, List.of("Negro"), Dinero.deCop(849_900))));

    ExcepcionDeDominio error = assertThrows(ExcepcionDeDominio.class, () -> aprobar(borrador.id()));
    assertTrue(error.getMessage().contains("4 fotos"), error.getMessage());
    assertTrue(productos.porId.isEmpty(), "nada se creó");
  }

  @Test
  void siUnArchivoFaltaSeBorraLoQueYaSeSubioAlPublico() {
    proponer(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")));
    BorradorTecnologia borrador = enRevision();
    new EditarBorradorTecnologia(borradores)
        .ejecutar(
            borrador.id(), List.of(new Eleccion(UNA_SIM, List.of("Negro"), Dinero.deCop(849_900))));
    conCuatroFotos(enRevision());
    privado.borrar(enRevision().fotos().getLast().referenciaVistaPrevia());

    assertThrows(ObjetoDeImagenNoEncontradoException.class, () -> aprobar(borrador.id()));
    assertTrue(publico.objetos.isEmpty(), "ninguna foto queda huérfana en el bucket público");
  }

  // --- Un modelo que ya se vende ------------------------------------------------------------

  @Test
  void laListaRenuevaElCostoYReponeLaExistenciaSinTocarElPrecio() {
    Producto producto = unA17QueYaSeVende();
    Variante negra = producto.variantes().getFirst();
    Inventario libro = inventario.porVariante.get(negra.id());
    libro.reservar(1, null, AHORA); // un pedido contraentrega en curso
    inventario.guardar(libro);
    assertEquals(1, disponible(negra.id()));

    ImportarListaDeTecnologia.Resultado resultado =
        importar(
            lista(
                JUEVES,
                List.of(config(UNA_SIM, 660_000, "1 SIM"), config(DUAL, 690_000, "Dual SIM")),
                List.of(),
                List.of()));

    assertEquals(1, resultado.productosRenovados());
    assertEquals(2, resultado.variantesRepuestas());
    assertEquals(EXISTENCIA, disponible(negra.id()), "vuelve a 2 libres, con la reservada aparte");
    assertEquals(3, inventario.porVariante.get(negra.id()).saldoTotal());
    assertEquals(Dinero.deCop(660_000), vinculos.porVariante.get(negra.id()).costo());
    assertEquals(
        Dinero.deCop(849_900),
        productos.buscarPorId(producto.id()).orElseThrow().variantes().getFirst().precioContado());
    assertEquals(0, resultado.borradoresNuevos(), "la dual quedó sin colores al aprobar: decidida");
  }

  @Test
  void unaConfiguracionNuevaDeUnModeloQueSeVendeVaAUnBorradorDeEseProducto() {
    Producto producto = unA17QueYaSeVende();
    String nueva = "a17-128-1-sim";

    proponer(JUEVES, List.of(config(UNA_SIM, 660_000, "1 SIM"), config(nueva, 600_000, "1 SIM")));

    BorradorTecnologia borrador = enRevision();
    assertEquals(producto.id(), borrador.productoId().orElseThrow());
    assertEquals(
        List.of(nueva),
        borrador.configuraciones().stream().map(ConfiguracionTecnologia::sku).toList());

    new EditarBorradorTecnologia(borradores)
        .ejecutar(
            borrador.id(), List.of(new Eleccion(nueva, List.of("Azul"), Dinero.deCop(749_900))));
    Producto completado = aprobar(borrador.id());

    assertEquals(producto.id(), completado.id());
    assertEquals(3, completado.variantes().size());
    assertEquals(1, productos.porId.size(), "no nace otro producto");
  }

  @Test
  void unaConfiguracionDesaparecidaPierdeLoLibreYConservaLoReservado() {
    Producto producto = unA17QueYaSeVende();
    Variante negra = producto.variantes().getFirst();
    Inventario libro = inventario.porVariante.get(negra.id());
    libro.reservar(1, null, AHORA);
    inventario.guardar(libro);

    ImportarListaDeTecnologia.Resultado resultado =
        importar(lista(JUEVES, List.of(), List.of(UNA_SIM), List.of()));

    assertEquals(2, resultado.variantesRetiradas());
    assertEquals(0, disponible(negra.id()));
    assertEquals(1, inventario.porVariante.get(negra.id()).saldoTotal(), "la del pedido se queda");
  }

  @Test
  void unModeloDesaparecidoQuedaAgotadoPorElProveedor() {
    Producto producto = unA17QueYaSeVende();

    ImportarListaDeTecnologia.Resultado resultado =
        importar(lista(JUEVES, List.of(), List.of(), List.of(A17.idModelo())));

    assertEquals(1, resultado.modelosAgotados());
    assertEquals(
        EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR,
        productos.buscarPorId(producto.id()).orElseThrow().estadoDisponibilidad());

    importar(
        lista(
            JUEVES.plusDays(1), List.of(config(UNA_SIM, 660_000, "1 SIM")), List.of(), List.of()));
    assertEquals(
        EstadoDisponibilidad.DISPONIBLE,
        productos.buscarPorId(producto.id()).orElseThrow().estadoDisponibilidad(),
        "vuelve con la lista que lo trae");
  }

  @Test
  void avisaCuandoElCostoAlcanzaElPrecioDeVenta() {
    unA17QueYaSeVende();

    ImportarListaDeTecnologia.Resultado resultado =
        importar(lista(JUEVES, List.of(config(UNA_SIM, 849_900, "1 SIM")), List.of(), List.of()));

    assertEquals(2, resultado.sinMargen().size());
    assertTrue(
        resultado.sinMargen().getFirst().startsWith("Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM"));
  }

  /**
   * Revisión adversarial del 27 de octubre de 2026: un costo que deja menos del 10 % sobre el
   * contado reponía la existencia y solo dejaba una nota. Ahora la variante queda agotada por el
   * proveedor, sin existencia libre y en el aviso del panel, y vuelve con la lista que le devuelve
   * el margen.
   */
  @Test
  void unCostoQueQuitaElMargenMinimoAgotaLaVarianteHastaQueVuelva() {
    Producto producto = unA17QueYaSeVende();
    List<UUID> variantes = producto.variantes().stream().map(Variante::id).toList();

    // Contado 849.900 sobre 780.000: un 9 %.
    ImportarListaDeTecnologia.Resultado sinMargen =
        importar(lista(JUEVES, List.of(config(UNA_SIM, 780_000, "1 SIM")), List.of(), List.of()));

    assertEquals(2, sinMargen.sinMargen().size());
    for (UUID id : variantes) {
      assertTrue(productosDeProveedor.retiradas.contains(id), "agotada por el proveedor");
      assertEquals(0, disponible(id), "no se repone");
    }
    assertEquals(2, productosDeProveedor.variantesSinMargen().size(), "el panel lo avisa");

    importar(
        lista(
            JUEVES.plusDays(1), List.of(config(UNA_SIM, 760_000, "1 SIM")), List.of(), List.of()));

    for (UUID id : variantes) {
      assertFalse(productosDeProveedor.retiradas.contains(id), "el margen volvió");
      assertEquals(EXISTENCIA, disponible(id));
    }
    assertTrue(productosDeProveedor.variantesSinMargen().isEmpty());
  }

  /**
   * Revisión adversarial del 27 de octubre de 2026: la configuración que no viene en la lista y la
   * skill no reporta como desaparecida —`conocidos.json` la había visto «ausente»— no se retiraba
   * nunca. Si su modelo viene en la lista, lo que no trae deja de tener existencia libre.
   */
  @Test
  void unaConfiguracionQueNoVieneConSuModeloSeRetiraAunqueNadieLaDeclareDesaparecida() {
    Producto producto = unA17QueYaSeVende();
    List<UUID> variantes = producto.variantes().stream().map(Variante::id).toList();
    variantes.forEach(id -> assertEquals(EXISTENCIA, disponible(id)));

    importar(lista(JUEVES, List.of(config(DUAL, 690_000, "Dual SIM")), List.of(), List.of()));

    variantes.forEach(id -> assertEquals(0, disponible(id), "la de una SIM ya no viene"));
  }

  @Test
  void unProveedorDeBolsosNoRecibeListas() {
    Proveedor deBolsos = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(deBolsos);
    assertThrows(
        ProveedorSinListasException.class,
        () ->
            importar(
                new ImportarListaDeTecnologiaComando(
                    deBolsos.id(), LUNES, Modo.INVENTARIO, List.of(), List.of(), List.of())));
  }

  // --- Lo que la lista no puede hacer dos veces ----------------------------------------------

  @Test
  void laMismaListaDosVecesNoReponeLoVendidoEntreLasDos() {
    Producto producto = unA17QueYaSeVende();
    Variante negra = producto.variantes().getFirst();
    ImportarListaDeTecnologiaComando jueves =
        lista(JUEVES, List.of(config(UNA_SIM, 660_000, "1 SIM")), List.of(), List.of());
    importar(jueves);
    Inventario libro = inventario.porVariante.get(negra.id());
    libro.reservar(2, null, AHORA); // se vendieron las dos
    inventario.guardar(libro);

    assertThrows(ListaDeTecnologiaYaImportadaException.class, () -> importar(jueves));
    assertEquals(0, disponible(negra.id()));
  }

  @Test
  void unaListaMasViejaQueLaUltimaNoEntra() {
    Producto producto = unA17QueYaSeVende();
    importar(lista(JUEVES, List.of(), List.of(UNA_SIM), List.of()));
    Variante negra = producto.variantes().getFirst();

    assertThrows(
        ListaDeTecnologiaDesactualizadaException.class,
        () ->
            importar(
                lista(
                    LUNES.plusDays(1),
                    List.of(config(UNA_SIM, 660_000, "1 SIM")),
                    List.of(),
                    List.of())));
    assertEquals(0, disponible(negra.id()), "lo retirado el jueves sigue retirado");
  }

  @Test
  void unaListaCorregidaElMismoDiaSiEntra() {
    unA17QueYaSeVende();
    importar(lista(JUEVES, List.of(config(UNA_SIM, 660_000, "1 SIM")), List.of(), List.of()));

    importar(lista(JUEVES, List.of(config(UNA_SIM, 650_000, "1 SIM")), List.of(), List.of()));

    assertEquals(Dinero.deCop(650_000), vinculos.porVariante.values().iterator().next().costo());
  }

  // --- Los colores de hoy -------------------------------------------------------------------

  @Test
  void cuandoLaListaDiceColoresSoloSeReponenEsos() {
    Producto producto = unA17QueYaSeVende();
    Variante negra = variante(producto, "Negro");
    Variante gris = variante(producto, "Gris");

    ImportarListaDeTecnologia.Resultado resultado =
        importar(
            lista(
                JUEVES,
                List.of(conColores(UNA_SIM, 660_000, "1 SIM", "Negro", "Azul")),
                List.of(),
                List.of()));

    assertEquals(EXISTENCIA, disponible(negra.id()));
    assertEquals(0, disponible(gris.id()), "hoy no trae gris: no se ofrece");
    assertEquals(
        List.of("Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM · Azul"),
        resultado.coloresSinVariante());
  }

  // --- El color que se confirma con el proveedor (ADR-0086) ----------------------------------

  private boolean porConfirmar(Variante variante) {
    return productosDeProveedor.disponibilidadPorConfirmar.get(variante.id());
  }

  @Test
  void aprobarDejaLaPaletaEnElProductoYLoQueLaListaNoMarcoPorConfirmar() {
    Producto producto = unA17QueYaSeVende();

    assertEquals(List.of("Negro", "Gris", "Azul"), producto.paleta());
    assertTrue(porConfirmar(variante(producto, "Negro")));
    assertTrue(porConfirmar(variante(producto, "Gris")));
  }

  @Test
  void elColorQueLaListaMarcaSeVendeSinConfirmarYElQueNoSeConfirma() {
    Producto producto = unA17QueYaSeVende();

    importar(
        lista(
            JUEVES, List.of(conColores(UNA_SIM, 660_000, "1 SIM", "Negro")), List.of(), List.of()));

    assertEquals(false, porConfirmar(variante(producto, "Negro")));
    assertTrue(porConfirmar(variante(producto, "Gris")));
  }

  @Test
  void unosSugeridosQueCubrenLaPaletaNoConfirmanNada() {
    Producto producto = unA17QueYaSeVende();

    importar(
        lista(
            JUEVES,
            List.of(conColores(UNA_SIM, 660_000, "1 SIM", "Negro", "Gris", "Azul")),
            List.of(),
            List.of()));

    assertTrue(porConfirmar(variante(producto, "Negro")));
    assertTrue(porConfirmar(variante(producto, "Gris")));
  }

  @Test
  void losColoresMarcadosQueLaExportacionMandaAparteMandanSobreLosSugeridos() {
    Producto producto = unA17QueYaSeVende();

    importar(
        new ImportarListaDeTecnologiaComando(
            proveedor.id(),
            JUEVES,
            Modo.INVENTARIO,
            delA17(List.of(conColores(UNA_SIM, 660_000, "1 SIM", "Negro", "Gris", "Azul"))),
            List.of(),
            List.of(),
            Map.of(UNA_SIM, List.of("Gris"))));

    assertTrue(porConfirmar(variante(producto, "Negro")));
    assertEquals(false, porConfirmar(variante(producto, "Gris")));
  }

  @Test
  void laPaletaDelPanelCompletaLosColoresDeCadaConfiguracionQueSeVende() {
    Producto producto = unA17QueYaSeVende();
    // Wompi subió la variable al 3 % después de publicar las hermanas a 879.000.
    ComisionDePasarela nueva =
        new ComisionDePasarela(new BigDecimal("0.03"), Dinero.deCop(700), new BigDecimal("0.19"));

    int nacidas =
        new DefinirPaletaDeTecnologia(
                productos,
                productosDeProveedor,
                vinculos,
                new AgregarVariante(productos, atributos, inventario, reloj, false, nueva))
            .ejecutar(producto.id(), List.of("Negro", "Gris", "Azul", "Verde"));

    assertEquals(2, nacidas, "azul y verde de la de una SIM; la dual no se vende");
    Producto completo = productos.buscarPorId(producto.id()).orElseThrow();
    assertEquals(List.of("Negro", "Gris", "Azul", "Verde"), completo.paleta());
    Variante verde = variante(completo, "Verde");
    // El contado de la hermana y el publicado por la comisión vigente, no el 879.000 de ella:
    // (849.900 + 833) / (1 − 0,03 × 1,19) = 882.228,6 → 883.000.
    assertEquals(Dinero.deCop(849_900), verde.precioContado());
    assertEquals(Dinero.deCop(883_000), verde.precio());
    assertEquals("1 SIM", valor(verde, "SIM"));
    assertEquals(0, disponible(verde.id()), "sin existencia hasta que el proveedor lo confirme");
    assertTrue(porConfirmar(verde));
    VarianteDeProveedor vinculo = vinculos.porVariante.get(verde.id());
    assertEquals(UNA_SIM, vinculo.configuracion());
    assertEquals(Dinero.deCop(675_000), vinculo.costo());
  }

  @Test
  void unaPaletaDeUnProductoQueNoVieneDeUnaListaNoSeDefine() {
    Producto producto = unA17QueYaSeVende();
    vinculos.porVariante.clear();

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new DefinirPaletaDeTecnologia(
                    productos,
                    productosDeProveedor,
                    vinculos,
                    new AgregarVariante(
                        productos, atributos, inventario, reloj, false, TarifasDePrueba.WOMPI))
                .ejecutar(producto.id(), List.of("Negro")));
  }

  private static Variante variante(Producto producto, String color) {
    return producto.variantes().stream()
        .filter(v -> color.equals(valor(v, "Color")))
        .findFirst()
        .orElseThrow();
  }

  // --- Dobles -------------------------------------------------------------------------------

  static final class ListasEnMemoria implements RepositorioListasDeTecnologia {
    final Map<String, LocalDate> porHuella = new LinkedHashMap<>();

    @Override
    public Optional<LocalDate> fechaDeLaUltima(UUID proveedorId) {
      return porHuella.values().stream().max(LocalDate::compareTo);
    }

    @Override
    public boolean yaEntro(UUID proveedorId, String huella) {
      return porHuella.containsKey(huella);
    }

    @Override
    public void registrar(
        UUID proveedorId, LocalDate fechaLista, String huella, Instant importadaEn) {
      porHuella.put(huella, fechaLista);
    }
  }

  static final class BorradoresEnMemoria implements RepositorioBorradoresTecnologia {
    final Map<UUID, BorradorTecnologia> porId = new LinkedHashMap<>();

    @Override
    public void guardar(BorradorTecnologia borrador) {
      porId.put(borrador.id(), borrador);
    }

    @Override
    public void actualizar(BorradorTecnologia borrador) {
      porId.put(borrador.id(), borrador);
    }

    @Override
    public Optional<BorradorTecnologia> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public Optional<BorradorTecnologia> buscarPorIdParaActualizar(UUID id) {
      return buscarPorId(id);
    }

    @Override
    public Optional<BorradorTecnologia> buscarEnRevisionParaActualizar(
        UUID proveedorId, String idModelo) {
      return delModelo(proveedorId, idModelo).stream()
          .filter(b -> b.estado() == EstadoBorrador.EN_REVISION)
          .findFirst();
    }

    @Override
    public List<BorradorTecnologia> listarResueltos(UUID proveedorId, String idModelo) {
      return delModelo(proveedorId, idModelo).stream()
          .filter(b -> b.estado() != EstadoBorrador.EN_REVISION)
          .toList();
    }

    @Override
    public List<BorradorTecnologia> listarPorEstado(EstadoBorrador estado) {
      return porId.values().stream()
          .filter(b -> b.estado() == estado)
          .sorted(Comparator.comparing(BorradorTecnologia::creadoEn).reversed())
          .toList();
    }

    @Override
    public void eliminar(UUID id) {
      porId.remove(id);
    }

    @Override
    public long contarEnEstados(Set<EstadoBorrador> estados) {
      return porId.values().stream().filter(b -> estados.contains(b.estado())).count();
    }

    @Override
    public List<UUID> idsEnEstados(Set<EstadoBorrador> estados, int limite) {
      return porId.values().stream()
          .filter(b -> estados.contains(b.estado()))
          .sorted(Comparator.comparing(BorradorTecnologia::creadoEn))
          .limit(limite)
          .map(BorradorTecnologia::id)
          .toList();
    }

    private List<BorradorTecnologia> delModelo(UUID proveedorId, String idModelo) {
      return porId.values().stream()
          .filter(b -> b.proveedorId().equals(proveedorId))
          .filter(b -> b.modelo().idModelo().equals(idModelo))
          .toList();
    }
  }

  static final class VinculosEnMemoria implements RepositorioVariantesDeProveedor {
    final Map<UUID, VarianteDeProveedor> porVariante = new LinkedHashMap<>();

    @Override
    public void guardar(VarianteDeProveedor vinculo) {
      porVariante.put(vinculo.varianteId(), vinculo);
    }

    @Override
    public List<VarianteDeProveedor> deProducto(UUID productoId) {
      return porVariante.values().stream().filter(v -> v.productoId().equals(productoId)).toList();
    }

    @Override
    public List<VarianteDeProveedor> deConfiguracion(UUID proveedorId, String configuracion) {
      return porVariante.values().stream()
          .filter(v -> v.proveedorId().equals(proveedorId))
          .filter(v -> v.configuracion().equals(configuracion))
          .collect(Collectors.toCollection(ArrayList::new));
    }
  }
}
