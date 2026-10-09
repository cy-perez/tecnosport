package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.AgregarVariante;
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
import co.tecnosport.api.application.proveedores.tecnologia.EditarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologiaComando;
import co.tecnosport.api.application.proveedores.tecnologia.ListaDeTecnologiaDesactualizadaException;
import co.tecnosport.api.application.proveedores.tecnologia.ListaDeTecnologiaYaImportadaException;
import co.tecnosport.api.application.proveedores.tecnologia.ProveedorSinListasException;
import co.tecnosport.api.application.proveedores.tecnologia.RechazarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioBorradoresTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioListasDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioVariantesDeProveedor;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.EstadoDisponibilidad;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia.Eleccion;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.VarianteDeProveedor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  private ImportarListaDeTecnologiaComando lista(
      LocalDate fecha,
      List<ConfiguracionTecnologia> configuraciones,
      List<String> desaparecidas,
      List<String> modelosDesaparecidos) {
    List<ImportarListaDeTecnologiaComando.Modelo> modelos =
        configuraciones.isEmpty()
            ? List.of()
            : List.of(new ImportarListaDeTecnologiaComando.Modelo(A17, configuraciones));
    return new ImportarListaDeTecnologiaComando(
        proveedor.id(), fecha, modelos, desaparecidas, modelosDesaparecidos);
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
            new RepositorioMarcasFijo(),
            new RepositorioCategoriasFijo(),
            atributos,
            new AgregarVariante(productos, atributos, inventario, reloj, false),
            EXISTENCIA)
        .ejecutar(
            new AprobarBorradorTecnologiaComando(
                borradorId,
                ApoyoDeCatalogoParaIngesta.MARCA.id(),
                ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO.id()));
  }

  private BorradorTecnologia enRevision() {
    return borradores.buscarEnRevisionParaActualizar(proveedor.id(), A17.idModelo()).orElseThrow();
  }

  /** Importa el A17 con la de una SIM y la dual, y aprueba la de una SIM en negro y gris. */
  private Producto unA17QueYaSeVende() {
    importar(
        lista(
            LUNES,
            List.of(config(UNA_SIM, 675_000, "1 SIM"), config(DUAL, 690_000, "Dual SIM")),
            List.of(),
            List.of()));
    BorradorTecnologia borrador = enRevision();
    new EditarBorradorTecnologia(borradores)
        .ejecutar(
            borrador.id(),
            List.of(new Eleccion(UNA_SIM, List.of("Negro", "Gris"), Dinero.deCop(849_900))));
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
        importar(
            lista(
                LUNES,
                List.of(config(UNA_SIM, 675_000, "1 SIM"), config(DUAL, 690_000, "Dual SIM")),
                List.of(),
                List.of()));

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
    importar(lista(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")), List.of(), List.of()));
    ImportarListaDeTecnologia.Resultado resultado =
        importar(lista(JUEVES, List.of(config(UNA_SIM, 660_000, "1 SIM")), List.of(), List.of()));

    assertEquals(1, resultado.borradoresActualizados());
    assertEquals(1, borradores.porId.size());
    assertEquals(Dinero.deCop(660_000), enRevision().configuraciones().getFirst().costoProveedor());
  }

  @Test
  void aprobarCreaUnaVariantePorColorConSusAtributosYLaExistenciaDeLaLista() {
    Producto producto = unA17QueYaSeVende();

    assertEquals(EstadoProducto.BORRADOR, producto.estado(), "sin fotos no se publica");
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
    assertEquals(Dinero.deCop(849_900), negra.precio());
    assertEquals(EXISTENCIA, disponible(negra.id()));
    VarianteDeProveedor vinculo = vinculos.porVariante.get(negra.id());
    assertEquals(UNA_SIM, vinculo.configuracion());
    assertEquals(Dinero.deCop(675_000), vinculo.costo());
    assertEquals(EstadoBorrador.APROBADO, borradores.porId.values().iterator().next().estado());
  }

  @Test
  void unModeloRechazadoNoSeVuelveAProponer() {
    importar(lista(LUNES, List.of(config(UNA_SIM, 675_000, "1 SIM")), List.of(), List.of()));
    new RechazarBorradorTecnologia(borradores).ejecutar(enRevision().id(), "No vendemos esta gama");

    ImportarListaDeTecnologia.Resultado resultado =
        importar(lista(JUEVES, List.of(config(UNA_SIM, 660_000, "1 SIM")), List.of(), List.of()));

    assertEquals(0, resultado.borradoresNuevos());
    assertEquals(List.of("Samsung Galaxy A17 5G"), resultado.modelosYaDecididos());
    assertTrue(borradores.buscarEnRevisionParaActualizar(proveedor.id(), A17.idModelo()).isEmpty());
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
        productos.buscarPorId(producto.id()).orElseThrow().variantes().getFirst().precio());
    assertEquals(0, resultado.borradoresNuevos(), "la dual quedó sin colores al aprobar: decidida");
  }

  @Test
  void unaConfiguracionNuevaDeUnModeloQueSeVendeVaAUnBorradorDeEseProducto() {
    Producto producto = unA17QueYaSeVende();
    String nueva = "a17-128-1-sim";

    importar(
        lista(
            JUEVES,
            List.of(config(UNA_SIM, 660_000, "1 SIM"), config(nueva, 600_000, "1 SIM")),
            List.of(),
            List.of()));

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

  @Test
  void unProveedorDeBolsosNoRecibeListas() {
    Proveedor deBolsos = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(deBolsos);
    assertThrows(
        ProveedorSinListasException.class,
        () ->
            importar(
                new ImportarListaDeTecnologiaComando(
                    deBolsos.id(), LUNES, List.of(), List.of(), List.of())));
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
