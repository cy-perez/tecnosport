package co.tecnosport.api.presentation.proveedores;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.DependenciasDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.application.proveedores.tecnologia.AgregarFotoABorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.AprobarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.EditarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.FotosFirmadasDeBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ListarBorradoresTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.QuitarFotoDeBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RechazarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioBorradoresTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioListasDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioVariantesDeProveedor;
import co.tecnosport.api.application.proveedores.tecnologia.SolicitarSubidaDeFotoDeBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.VerBorradorTecnologia;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoDeBorradorTecnologia;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import co.tecnosport.api.presentation.catalogo.MapeadorRespuestasCatalogo;
import co.tecnosport.api.presentation.catalogo.MapeadorRespuestasProductoAdmin;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Los casos de uso de verdad sobre dobles. El camino feliz de la aprobación y lo que hace una lista
 * con lo que ya se vende lo prueba {@code TecnologiaPorListasTest} en {@code application}; aquí se
 * prueba lo que el controlador añade: rutas, el cuerpo que escribe la skill, los códigos y el
 * formato de error.
 */
@WebMvcTest(AdminTecnologiaControlador.class)
@Import({
  AdminTecnologiaControladorTest.Configuracion.class,
  ManejadorDeErrores.class,
  MapeadorRespuestasProductoAdmin.class,
  MapeadorRespuestasCatalogo.class
})
class AdminTecnologiaControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ProveedoresDoble proveedores;
  @Autowired private BorradoresDoble borradores;
  @Autowired private AlmacenDoble almacen;

  private Proveedor tecnologia;

  /** Lo que escribe {@code exportar_lista.py}, con un modelo de dos configuraciones. */
  private static final String LISTA =
      """
      {
        "fechaLista": "2026-10-08",
        "modo": "BORRADORES",
        "bloques": ["ANDROID"],
        "modelos": [{
          "idModelo": "samsung-galaxy-a17-5g",
          "titulo": "Samsung Galaxy A17 5G",
          "marca": "Samsung",
          "categoria": "celulares",
          "descripcion": "El Galaxy A17 5G.",
          "metaDescripcion": "El Galaxy A17 5G.",
          "paleta": ["Negro", "Gris", "Azul"],
          "configuraciones": [
            {"sku": "a17-1-sim", "titulo": "Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM",
             "ram": "8GB", "almacenamiento": "256GB", "sim": "1 SIM",
             "costoProveedor": 675000, "precioMercado": 849900, "coloresSugeridos": ["Negro"]},
            {"sku": "a17-dual-sim", "titulo": "Samsung Galaxy A17 5G 8GB RAM 256GB Dual SIM",
             "ram": "8GB", "almacenamiento": "256GB", "sim": "Dual SIM",
             "costoProveedor": 690000, "precioMercado": null, "coloresSugeridos": []}
          ]
        }],
        "configuracionesDesaparecidas": [],
        "modelosDesaparecidos": []
      }
      """;

  /** La misma lista, en el tiempo del inventario. */
  private static final String INVENTARIO =
      LISTA.replace("\"modo\": \"BORRADORES\"", "\"modo\": \"INVENTARIO\"");

  @Autowired private ListasDoble listas;

  @BeforeEach
  void unProveedorDeTecnologia() {
    almacen.objetos.clear();
    proveedores.porId.clear();
    borradores.porId.clear();
    listas.porHuella.clear();
    tecnologia =
        Proveedor.crear(
            "Tecnología Medellín",
            LineaCatalogo.TECNOLOGIA,
            "+57 300",
            "Tecno",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    proveedores.porId.put(tecnologia.id(), tecnologia);
  }

  private UUID importarYTomarElBorrador() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", tecnologia.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(INVENTARIO))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", tecnologia.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(LISTA))
        .andExpect(status().isOk());
    return borradores.porId.keySet().iterator().next();
  }

  @Test
  void importarLaListaDeLaSkillAbreElBorradorDelModelo() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", tecnologia.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(INVENTARIO))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", tecnologia.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(LISTA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.borradoresNuevos").value(1))
        .andExpect(jsonPath("$.productosRenovados").value(0))
        .andExpect(jsonPath("$.sinMargen").isEmpty());

    mockMvc
        .perform(get("/api/v1/admin/borradores-tecnologia"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].titulo").value("Samsung Galaxy A17 5G"))
        .andExpect(jsonPath("$[0].estado").value("EN_REVISION"))
        .andExpect(jsonPath("$[0].paleta[2]").value("Azul"))
        .andExpect(jsonPath("$[0].configuraciones[0].costoProveedor").value(675000))
        .andExpect(jsonPath("$[0].configuraciones[0].precioMercado").value(849900))
        .andExpect(jsonPath("$[0].configuraciones[1].precioMercado").doesNotExist())
        .andExpect(jsonPath("$[0].configuraciones[0].coloresSugeridos[0]").value("Negro"));
  }

  @Test
  void elInventarioDiceQueModelosEsperanBorradorSinAbrirlo() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", tecnologia.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(INVENTARIO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.borradoresNuevos").value(0))
        .andExpect(jsonPath("$.modelosPorProponer[0]").value("samsung-galaxy-a17-5g"))
        .andExpect(jsonPath("$.configuracionesPorProponer").isEmpty());
    assertTrue(borradores.porId.isEmpty());
  }

  @Test
  void losBorradoresAntesDelInventarioDeSuListaSon409() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", tecnologia.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(LISTA))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("INVENTARIO_DE_LA_LISTA_PENDIENTE"));
  }

  @Test
  void unaListaSinModoEs422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", tecnologia.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(LISTA.replace("\"modo\": \"BORRADORES\",", "")))
        .andExpect(status().isUnprocessableContent());
    assertTrue(borradores.porId.isEmpty());
  }

  @Test
  void unaListaParaUnProveedorDeBolsosEs409() throws Exception {
    Proveedor bolsos =
        Proveedor.crear(
            "Bolsos",
            LineaCatalogo.BOLSOS,
            "+57 300",
            "Bolsos",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    proveedores.porId.put(bolsos.id(), bolsos);

    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", bolsos.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(LISTA))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PROVEEDOR_SIN_LISTAS"));
  }

  @Test
  void unModeloSinDescripcionEs422YNoEntraNada() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", tecnologia.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    LISTA.replace(
                        "\"descripcion\": \"El Galaxy A17 5G.\"", "\"descripcion\": \" \"")))
        .andExpect(status().isUnprocessableContent());
    assertTrue(borradores.porId.isEmpty());
  }

  @Test
  void elegirColoresYPrecioYDespuesRechazar() throws Exception {
    UUID id = importarYTomarElBorrador();

    mockMvc
        .perform(
            patch("/api/v1/admin/borradores-tecnologia/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"configuraciones": [
                      {"sku": "a17-1-sim", "colores": ["Negro", "Gris"], "precioVenta": 829900}
                    ]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configuraciones[0].coloresElegidos[1]").value("Gris"))
        .andExpect(jsonPath("$.configuraciones[0].precioVenta").value(829900))
        .andExpect(jsonPath("$.configuraciones[1].coloresElegidos").isEmpty());

    mockMvc
        .perform(
            post("/api/v1/admin/borradores-tecnologia/{id}/rechazar", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"No vendemos esta gama\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("RECHAZADO"));

    mockMvc
        .perform(
            patch("/api/v1/admin/borradores-tecnologia/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"configuraciones\": []}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("BORRADOR_NO_EDITABLE"));
  }

  @Test
  void unColorFueraDeLaPaletaEs422() throws Exception {
    UUID id = importarYTomarElBorrador();

    mockMvc
        .perform(
            patch("/api/v1/admin/borradores-tecnologia/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"configuraciones\": [{\"sku\": \"a17-1-sim\", \"colores\": [\"Rosado\"]}]}"))
        .andExpect(status().isUnprocessableContent());
  }

  /** Las cuatro que exige un modelo nuevo, puestas directo en el borrador del doble. */
  private void conCuatroFotos(UUID id) {
    for (int i = 1; i <= BorradorTecnologia.MINIMO_DE_FOTOS; i++) {
      borradores
          .porId
          .get(id)
          .agregarFoto(
              new FotoDeBorradorTecnologia(
                  UUID.randomUUID(),
                  List.of(new FotoDeBorradorTecnologia.Variante(1200, "f" + i + ".avif", 58)),
                  null,
                  1200,
                  String.valueOf(i).repeat(64),
                  "alt",
                  "alt",
                  Instant.parse("2026-10-08T15:00:00Z")));
    }
  }

  @Test
  void aprobarUnModeloNuevoSinSusFotosEs422() throws Exception {
    UUID id = importarYTomarElBorrador();

    mockMvc
        .perform(
            post("/api/v1/admin/borradores-tecnologia/{id}/aprobar", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("4 fotos")));
  }

  @Test
  void aprobarSinNadaQueVenderEs422AntesDeTocarElCatalogo() throws Exception {
    UUID id = importarYTomarElBorrador();
    conCuatroFotos(id);

    mockMvc
        .perform(
            post("/api/v1/admin/borradores-tecnologia/{id}/aprobar", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableContent());
  }

  /** El cuerpo trae marca y categoría opcionales: sin ellas, un modelo nuevo es 422 y no un 500. */
  @Test
  void aprobarUnModeloNuevoSinMarcaNiCategoriaEs422() throws Exception {
    UUID id = importarYTomarElBorrador();
    conCuatroFotos(id);
    mockMvc
        .perform(
            patch("/api/v1/admin/borradores-tecnologia/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"configuraciones\": [{\"sku\": \"a17-1-sim\", \"colores\": [\"Negro\"],"
                        + " \"precioVenta\": 829900}]}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/admin/borradores-tecnologia/{id}/aprobar", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void elMismoInventarioDosVecesEs409() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", tecnologia.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(INVENTARIO))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/listas-tecnologia", tecnologia.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(INVENTARIO))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("LISTA_DE_TECNOLOGIA_YA_IMPORTADA"));
  }

  // --- Las fotos del borrador (ADR-0079) ----------------------------------------------------

  /** Pide la URL, «sube» el archivo al doble y devuelve la clave. */
  private String subir(UUID borradorId, String contentType) throws Exception {
    String respuesta =
        mockMvc
            .perform(
                post("/api/v1/admin/borradores-tecnologia/{id}/fotos/url-subida", borradorId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"contentType\": \"" + contentType + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String clave = respuesta.replaceAll(".*\"objectKey\":\"([^\"]+)\".*", "$1");
    almacen.objetos.put(clave, new byte[1234]);
    return clave;
  }

  private String cuerpoDeFoto(String mayor, String menor, String vistaPrevia, String hash) {
    return """
        {"variantes": [{"ancho": 1200, "objectKey": "%s"}, {"ancho": 480, "objectKey": "%s"}],
         "objectKeyVistaPrevia": "%s", "alto": 1200, "hash": "%s",
         "altEs": "Galaxy A17 sobre fondo blanco", "altEn": "Galaxy A17 on a white background"}
        """
        .formatted(mayor, menor, vistaPrevia, hash);
  }

  @Test
  void unaFotoSeSubeAlBorradorYElDetalleLaMuestraFirmada() throws Exception {
    UUID id = importarYTomarElBorrador();
    String mayor = subir(id, "image/avif");
    String menor = subir(id, "image/avif");
    String vistaPrevia = subir(id, "image/jpeg");
    assertTrue(mayor.contains("/borradores-tecnologia/" + id + "/"), mayor);

    mockMvc
        .perform(
            post("/api/v1/admin/borradores-tecnologia/{id}/fotos", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeFoto(mayor, menor, vistaPrevia, "a".repeat(64))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cantidadDeFotos").value(1))
        .andExpect(jsonPath("$.fotos[0].url").value("https://firmada.local/leer/" + mayor))
        .andExpect(jsonPath("$.fotos[0].ancho").value(1200));

    mockMvc
        .perform(get("/api/v1/admin/borradores-tecnologia"))
        .andExpect(jsonPath("$[0].cantidadDeFotos").value(1))
        .andExpect(jsonPath("$[0].fotos").isEmpty());
  }

  @Test
  void unaClaveQueNoEsDelBorradorEs422() throws Exception {
    UUID id = importarYTomarElBorrador();
    String ajena = "proveedores/" + tecnologia.id() + "/borradores-tecnologia/otro/x.avif";
    almacen.objetos.put(ajena, new byte[10]);

    mockMvc
        .perform(
            post("/api/v1/admin/borradores-tecnologia/{id}/fotos", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeFoto(ajena, ajena, ajena, "b".repeat(64))))
        .andExpect(status().isUnprocessableContent());
    assertTrue(borradores.porId.get(id).fotos().isEmpty());
  }

  @Test
  void unArchivoQueNoLlegoAlBucketEs404() throws Exception {
    UUID id = importarYTomarElBorrador();
    String mayor = subir(id, "image/avif");
    String sinSubir =
        "proveedores/"
            + tecnologia.id()
            + "/borradores-tecnologia/"
            + id
            + "/"
            + UUID.randomUUID()
            + ".avif";

    mockMvc
        .perform(
            post("/api/v1/admin/borradores-tecnologia/{id}/fotos", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeFoto(mayor, sinSubir, mayor, "c".repeat(64))))
        .andExpect(status().isNotFound());
  }

  @Test
  void quitarUnaFotoBorraSusArchivos() throws Exception {
    UUID id = importarYTomarElBorrador();
    String mayor = subir(id, "image/avif");
    String menor = subir(id, "image/avif");
    String vistaPrevia = subir(id, "image/jpeg");
    mockMvc.perform(
        post("/api/v1/admin/borradores-tecnologia/{id}/fotos", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoDeFoto(mayor, menor, vistaPrevia, "d".repeat(64))));
    UUID fotoId = borradores.porId.get(id).fotos().getFirst().id();

    mockMvc
        .perform(delete("/api/v1/admin/borradores-tecnologia/{id}/fotos/{foto}", id, fotoId))
        .andExpect(status().isNoContent());

    assertTrue(borradores.porId.get(id).fotos().isEmpty());
    assertTrue(almacen.objetos.isEmpty(), "los tres archivos de la foto se fueron");
  }

  @Test
  void unTipoQueNoEsImagenWebEs422() throws Exception {
    UUID id = importarYTomarElBorrador();
    mockMvc
        .perform(
            post("/api/v1/admin/borradores-tecnologia/{id}/fotos/url-subida", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\": \"image/gif\"}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void unBorradorQueNoExisteEs404() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/borradores-tecnologia/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("BORRADOR_NO_ENCONTRADO"));
  }

  // --- Dobles -------------------------------------------------------------------------------

  /** Un puerto que este escenario no debe tocar: si alguien lo llama, la prueba lo dice. */
  @SuppressWarnings("unchecked")
  private static <T> T noUsado(Class<T> tipo) {
    return (T)
        Proxy.newProxyInstance(
            tipo.getClassLoader(),
            new Class<?>[] {tipo},
            (proxy, metodo, args) -> {
              if (metodo.getDeclaringClass() == Object.class) {
                return switch (metodo.getName()) {
                  case "toString" -> tipo.getSimpleName() + " no usado";
                  case "hashCode" -> System.identityHashCode(proxy);
                  default -> proxy == args[0];
                };
              }
              throw new UnsupportedOperationException(
                  tipo.getSimpleName() + "." + metodo.getName() + " no debía llamarse");
            });
  }

  static final class ProveedoresDoble implements RepositorioProveedores {
    final Map<UUID, Proveedor> porId = new LinkedHashMap<>();

    @Override
    public void guardar(Proveedor proveedor) {
      porId.put(proveedor.id(), proveedor);
    }

    @Override
    public void actualizar(Proveedor proveedor) {
      porId.put(proveedor.id(), proveedor);
    }

    @Override
    public Optional<Proveedor> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<Proveedor> listar() {
      return List.copyOf(porId.values());
    }

    @Override
    public DependenciasDeProveedor dependenciasDe(UUID id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void eliminarConSuHistorial(UUID id) {
      throw new UnsupportedOperationException();
    }
  }

  static final class BorradoresDoble implements RepositorioBorradoresTecnologia {
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
      return porId.values().stream()
          .filter(b -> b.proveedorId().equals(proveedorId))
          .filter(b -> b.modelo().idModelo().equals(idModelo))
          .filter(b -> b.estado() == EstadoBorrador.EN_REVISION)
          .findFirst();
    }

    @Override
    public List<BorradorTecnologia> listarResueltos(UUID proveedorId, String idModelo) {
      return porId.values().stream()
          .filter(b -> b.proveedorId().equals(proveedorId))
          .filter(b -> b.modelo().idModelo().equals(idModelo))
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
  }

  static final class ListasDoble implements RepositorioListasDeTecnologia {
    final Map<String, java.time.LocalDate> porHuella = new LinkedHashMap<>();

    @Override
    public Optional<java.time.LocalDate> fechaDeLaUltima(UUID proveedorId) {
      return porHuella.values().stream().max(java.time.LocalDate::compareTo);
    }

    @Override
    public boolean yaEntro(UUID proveedorId, String huella) {
      return porHuella.containsKey(huella);
    }

    @Override
    public void registrar(
        UUID proveedorId, java.time.LocalDate fechaLista, String huella, Instant importadaEn) {
      porHuella.put(huella, fechaLista);
    }
  }

  /** Sin productos: todo lo de la lista es nuevo. */
  static final class SinProductosDeProveedor implements RepositorioProductosDeProveedor {
    @Override
    public Optional<Producto> buscarPorHuella(UUID proveedorId, HuellaProveedor huella) {
      return Optional.empty();
    }

    @Override
    public List<Producto> disponiblesVistosAntesDe(Instant limite) {
      return List.of();
    }
  }

  /** El bucket privado del proveedor, en memoria. */
  static final class AlmacenDoble implements AlmacenDeArchivosDeProveedor {
    final Map<String, byte[]> objetos = new HashMap<>();

    @Override
    public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
      return new UrlFirmada("https://firmada.local/subir/" + objectKey);
    }

    @Override
    public Optional<Long> tamanoBytes(String objectKey) {
      return Optional.ofNullable(objetos.get(objectKey)).map(b -> (long) b.length);
    }

    @Override
    public void guardar(String objectKey, String contentType, byte[] bytes) {
      objetos.put(objectKey, bytes);
    }

    @Override
    public Optional<byte[]> leer(String objectKey) {
      return Optional.ofNullable(objetos.get(objectKey));
    }

    @Override
    public UrlFirmada urlDeLectura(String objectKey) {
      return new UrlFirmada("https://firmada.local/leer/" + objectKey);
    }

    @Override
    public void borrar(String objectKey) {
      objetos.remove(objectKey);
    }
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    ProveedoresDoble proveedores() {
      return new ProveedoresDoble();
    }

    @Bean
    BorradoresDoble borradores() {
      return new BorradoresDoble();
    }

    @Bean
    ListasDoble listas() {
      return new ListasDoble();
    }

    @Bean
    AlmacenDoble almacen() {
      return new AlmacenDoble();
    }

    @Bean
    SolicitarSubidaDeFotoDeBorradorTecnologia solicitarSubidaDeFoto(
        BorradoresDoble borradores, AlmacenDoble almacen) {
      return new SolicitarSubidaDeFotoDeBorradorTecnologia(borradores, almacen);
    }

    @Bean
    AgregarFotoABorradorTecnologia agregarFoto(BorradoresDoble borradores, AlmacenDoble almacen) {
      return new AgregarFotoABorradorTecnologia(
          borradores, almacen, () -> Instant.parse("2026-10-08T15:00:00Z"));
    }

    @Bean
    QuitarFotoDeBorradorTecnologia quitarFoto(BorradoresDoble borradores, AlmacenDoble almacen) {
      return new QuitarFotoDeBorradorTecnologia(borradores, almacen);
    }

    @Bean
    FotosFirmadasDeBorradorTecnologia fotosFirmadas(AlmacenDoble almacen) {
      return new FotosFirmadasDeBorradorTecnologia(almacen);
    }

    @Bean
    ImportarListaDeTecnologia importarListaDeTecnologia(
        ProveedoresDoble proveedores, BorradoresDoble borradores, ListasDoble listas) {
      Reloj reloj = () -> Instant.parse("2026-10-08T15:00:00Z");
      return new ImportarListaDeTecnologia(
          proveedores,
          new SinProductosDeProveedor(),
          noUsado(RepositorioProductos.class),
          borradores,
          noUsado(RepositorioVariantesDeProveedor.class),
          noUsado(RepositorioInventario.class),
          listas,
          reloj,
          2);
    }

    @Bean
    AprobarBorradorTecnologia aprobarBorradorTecnologia(
        BorradoresDoble borradores, AlmacenDoble almacen, ListasDoble listas) {
      Reloj reloj = () -> Instant.parse("2026-10-08T15:00:00Z");
      RepositorioProductos productos = noUsado(RepositorioProductos.class);
      return new AprobarBorradorTecnologia(
          borradores,
          noUsado(RepositorioVariantesDeProveedor.class),
          productos,
          new SinProductosDeProveedor(),
          listas,
          noUsado(RepositorioMarcas.class),
          noUsado(RepositorioCategorias.class),
          noUsado(RepositorioAtributos.class),
          new AgregarVariante(
              productos,
              noUsado(RepositorioAtributos.class),
              noUsado(RepositorioInventario.class),
              reloj,
              false),
          almacen,
          noUsado(AlmacenDeImagenes.class),
          2);
    }

    @Bean
    ListarBorradoresTecnologia listarBorradoresTecnologia(BorradoresDoble borradores) {
      return new ListarBorradoresTecnologia(borradores);
    }

    @Bean
    VerBorradorTecnologia verBorradorTecnologia(BorradoresDoble borradores) {
      return new VerBorradorTecnologia(borradores);
    }

    @Bean
    EditarBorradorTecnologia editarBorradorTecnologia(BorradoresDoble borradores) {
      return new EditarBorradorTecnologia(borradores);
    }

    @Bean
    RechazarBorradorTecnologia rechazarBorradorTecnologia(BorradoresDoble borradores) {
      return new RechazarBorradorTecnologia(borradores);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
          return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
      };
    }
  }
}
