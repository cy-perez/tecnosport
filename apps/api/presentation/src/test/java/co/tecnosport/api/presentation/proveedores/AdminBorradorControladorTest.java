package co.tecnosport.api.presentation.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
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
import co.tecnosport.api.application.proveedores.AprobarBorrador;
import co.tecnosport.api.application.proveedores.BorradoresPaginados;
import co.tecnosport.api.application.proveedores.CalculadorDePHash;
import co.tecnosport.api.application.proveedores.DescartarFotoDeBorrador;
import co.tecnosport.api.application.proveedores.EditarBorrador;
import co.tecnosport.api.application.proveedores.EliminarBorrador;
import co.tecnosport.api.application.proveedores.HuellaVisual;
import co.tecnosport.api.application.proveedores.ProcesadorDeImagenes;
import co.tecnosport.api.application.proveedores.RechazarBorrador;
import co.tecnosport.api.application.proveedores.RepositorioBorradores;
import co.tecnosport.api.application.proveedores.RepositorioMensajesProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.application.proveedores.RepositorioPublicacionesProveedor;
import co.tecnosport.api.application.proveedores.VerBorrador;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import co.tecnosport.api.presentation.catalogo.MapeadorRespuestasCatalogo;
import co.tecnosport.api.presentation.catalogo.MapeadorRespuestasProductoAdmin;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Los casos de uso de verdad sobre dobles. El camino feliz de la aprobación —variantes, fotos,
 * inventario— lo prueba {@code AprobarBorradorTest} en {@code application}; aquí se prueba lo que
 * el controlador añade: rutas, cuerpos, códigos y el formato de error de cada rechazo.
 */
@WebMvcTest(AdminBorradorControlador.class)
@Import({
  AdminBorradorControladorTest.Configuracion.class,
  ManejadorDeErrores.class,
  MapeadorRespuestasProductoAdmin.class,
  MapeadorRespuestasCatalogo.class
})
class AdminBorradorControladorTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioBorradoresDoble borradores;
  @Autowired private RepositorioPublicacionesDoble publicaciones;
  @Autowired private RepositorioMensajesDoble mensajes;
  @Autowired private ObjetosBorrados objetosBorrados;

  private BorradorProducto borrador;
  private MensajeProveedor foto;

  @BeforeEach
  void unBorradorEnRevision() {
    borradores.porId.clear();
    objetosBorrados.claves.clear();
    UUID proveedorId = UUID.randomUUID();
    UUID loteId = UUID.randomUUID();
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedorId, loteId, new IdExternoDeMensaje("p"), T, "Bolso 💰 53.000");
    foto =
        MensajeProveedor.imagen(
            proveedorId,
            loteId,
            new IdExternoDeMensaje("f"),
            T.plusSeconds(10),
            "el vino",
            "p/f.jpg");
    mensajes.porLote.put(loteId, List.of(principal, foto));
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal);
    publicacion.anexar(foto);
    publicaciones.porId.put(publicacion.id(), publicacion);
    borrador =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedorId,
            new ProductoExtraido(
                true,
                false,
                "Bolso de dama mediano",
                LineaCatalogo.BOLSOS,
                TipoProductoProveedor.BOLSO,
                Dinero.deCop(53000),
                Tallas.desconocida(),
                4,
                List.of(),
                "importado",
                "incluye llavero.",
                null,
                false,
                new BigDecimal("0.9"),
                null),
            "{\"fixture\":true}",
            Dinero.deCop(53000),
            Dinero.deCop(71600),
            null,
            null,
            Set.of(AlertaBorrador.SIN_FOTOS),
            T);
    borradores.porId.put(borrador.id(), borrador);
  }

  @Test
  void listaConFiltrosYDevuelveLosDinerosComoEnteros() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/borradores").param("estado", "EN_REVISION"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalBorradores").value(1))
        .andExpect(jsonPath("$.items[0].titulo").value("Bolso de dama mediano"))
        .andExpect(jsonPath("$.items[0].precioProveedor").value(53000))
        .andExpect(jsonPath("$.items[0].precioVentaSugerido").value(71600))
        .andExpect(jsonPath("$.items[0].alertas[0]").value("SIN_FOTOS"))
        .andExpect(jsonPath("$.items[0].tallas.tipo").value("DESCONOCIDA"));

    mockMvc
        .perform(get("/api/v1/admin/borradores").param("estado", "RECHAZADO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalBorradores").value(0));
  }

  @Test
  void verTraeLosTextosYLasFotosConUrlFirmada() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/borradores/{id}", borrador.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.borrador.id").value(borrador.id().toString()))
        .andExpect(jsonPath("$.textos[0]").value("Bolso 💰 53.000"))
        .andExpect(jsonPath("$.fotos[0].mensajeId").value(foto.id().toString()))
        .andExpect(jsonPath("$.fotos[0].url").value("https://firmada.local/leer/p/f.jpg"))
        .andExpect(jsonPath("$.fotos[0].pieDeFoto").value("el vino"));

    mockMvc
        .perform(get("/api/v1/admin/borradores/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("BORRADOR_NO_ENCONTRADO"));
  }

  @Test
  void editarCambiaLoQueLlegaYDevuelveElBorrador() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/admin/borradores/{id}", borrador.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"titulo\":\"Bolso mediano ejecutivo\",\"precioVentaSugerido\":75000,"
                        + "\"tallas\":{\"tipo\":\"UNICA\",\"sirveHasta\":\"L\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.titulo").value("Bolso mediano ejecutivo"))
        .andExpect(jsonPath("$.precioVentaSugerido").value(75000))
        .andExpect(jsonPath("$.tallas.tipo").value("UNICA"))
        .andExpect(jsonPath("$.tallas.sirveHasta").value("L"))
        .andExpect(jsonPath("$.cantidadTonos").value(4));
  }

  @Test
  void rechazarExigeMotivoYDejaElBorradorRechazado() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/borradores/{id}/rechazar", borrador.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Es una promoción.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("RECHAZADO"))
        .andExpect(jsonPath("$.motivoRechazo").value("Es una promoción."));

    mockMvc
        .perform(
            patch("/api/v1/admin/borradores/{id}", borrador.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"x\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("BORRADOR_NO_EDITABLE"));
  }

  @Test
  void borrarEs204YSeLlevaLaFotoDelBucket() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/borradores/{id}", borrador.id()))
        .andExpect(status().isNoContent());

    assertThat(borradores.porId).doesNotContainKey(borrador.id());
    assertThat(publicaciones.porId).doesNotContainKey(borrador.publicacionId());
    assertThat(objetosBorrados.claves).containsExactly("p/f.jpg");

    mockMvc
        .perform(delete("/api/v1/admin/borradores/{id}", borrador.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("BORRADOR_NO_ENCONTRADO"));
  }

  /** La foto deja de verse en la revisión y el archivo se queda: es de la publicación. */
  @Test
  void descartarUnaFotoEs204YDejaDeVerse() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/borradores/{id}/fotos/{foto}", borrador.id(), foto.id()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/admin/borradores/{id}", borrador.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fotos").isEmpty());
    assertThat(objetosBorrados.claves).isEmpty();
  }

  @Test
  void descartarUnaFotoAjenaEs422() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/admin/borradores/{id}/fotos/{foto}", borrador.id(), UUID.randomUUID()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("FOTO_NO_ES_DEL_BORRADOR"));
  }

  @Test
  void borrarUnAprobadoEs409() throws Exception {
    borrador.aprobar(UUID.randomUUID(), null);

    mockMvc
        .perform(delete("/api/v1/admin/borradores/{id}", borrador.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("BORRADOR_NO_ELIMINABLE"));
    assertThat(borradores.porId).containsKey(borrador.id());
  }

  @Test
  void aprobarSinFotosEs409YSinCategoriaEs422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/borradores/{id}/aprobar", borrador.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"categoriaId\":\""
                        + UUID.randomUUID()
                        + "\",\"marcaId\":\""
                        + UUID.randomUUID()
                        + "\",\"precioVenta\":71600,\"existenciaInicial\":1,\"altEs\":\"Bolso\","
                        + "\"altEn\":\"Bag\",\"fotos\":[]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("BORRADOR_SIN_FOTOS"));

    mockMvc
        .perform(
            post("/api/v1/admin/borradores/{id}/aprobar", borrador.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"marcaId\":\""
                        + UUID.randomUUID()
                        + "\",\"precioVenta\":71600,\"existenciaInicial\":1,\"altEs\":\"Bolso\","
                        + "\"altEn\":\"Bag\",\"fotos\":[]}"))
        .andExpect(status().isUnprocessableContent());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioBorradoresDoble repositorioBorradores() {
      return new RepositorioBorradoresDoble();
    }

    @Bean
    RepositorioPublicacionesDoble repositorioPublicaciones() {
      return new RepositorioPublicacionesDoble();
    }

    @Bean
    RepositorioMensajesDoble repositorioMensajes() {
      return new RepositorioMensajesDoble();
    }

    @Bean
    ObjetosBorrados objetosBorrados() {
      return new ObjetosBorrados();
    }

    @Bean
    AlmacenDeArchivosDeProveedor almacen(ObjetosBorrados objetosBorrados) {
      return new AlmacenDeArchivosDeProveedor() {
        @Override
        public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Long> tamanoBytes(String objectKey) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void guardar(String objectKey, String contentType, byte[] bytes) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Optional<byte[]> leer(String objectKey) {
          return Optional.empty();
        }

        @Override
        public UrlFirmada urlDeLectura(String objectKey) {
          return new UrlFirmada("https://firmada.local/leer/" + objectKey);
        }

        @Override
        public void borrar(String objectKey) {
          objetosBorrados.claves.add(objectKey);
        }
      };
    }

    @Bean
    VerBorrador verBorrador(
        RepositorioBorradoresDoble borradores,
        RepositorioPublicacionesDoble publicaciones,
        RepositorioMensajesDoble mensajes,
        AlmacenDeArchivosDeProveedor almacen) {
      return new VerBorrador(borradores, publicaciones, mensajes, almacen);
    }

    @Bean
    EliminarBorrador eliminarBorrador(
        RepositorioBorradoresDoble borradores,
        RepositorioPublicacionesDoble publicaciones,
        RepositorioMensajesDoble mensajes,
        AlmacenDeArchivosDeProveedor almacen) {
      return new EliminarBorrador(borradores, publicaciones, mensajes, almacen);
    }

    @Bean
    DescartarFotoDeBorrador descartarFotoDeBorrador(
        RepositorioBorradoresDoble borradores, RepositorioPublicacionesDoble publicaciones) {
      return new DescartarFotoDeBorrador(borradores, publicaciones);
    }

    @Bean
    EditarBorrador editarBorrador(RepositorioBorradoresDoble borradores) {
      return new EditarBorrador(borradores);
    }

    @Bean
    RechazarBorrador rechazarBorrador(RepositorioBorradoresDoble borradores) {
      return new RechazarBorrador(borradores);
    }

    /** Con todo lo que no se ejercita aquí reventando: la aprobación se rechaza antes de llegar. */
    @Bean
    AprobarBorrador aprobarBorrador(
        RepositorioBorradoresDoble borradores,
        RepositorioPublicacionesDoble publicaciones,
        RepositorioMensajesDoble mensajes,
        AlmacenDeArchivosDeProveedor almacen) {
      RepositorioProductos productos = inerte(RepositorioProductos.class);
      return new AprobarBorrador(
          borradores,
          publicaciones,
          mensajes,
          inerte(RepositorioProveedores.class),
          productos,
          inerte(RepositorioProductosDeProveedor.class),
          inerte(RepositorioMarcas.class),
          inerte(RepositorioCategorias.class),
          inerte(RepositorioAtributos.class),
          new AgregarVariante(
              productos,
              inerte(RepositorioAtributos.class),
              inerte(RepositorioInventario.class),
              (Reloj) () -> T,
              false),
          almacen,
          inerte(AlmacenDeImagenes.class),
          inerte(ProcesadorDeImagenes.class),
          inerte(CalculadorDePHash.class),
          (Reloj) () -> T);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
          return new Object();
        }

        @Override
        protected void doBegin(
            Object transaction, org.springframework.transaction.TransactionDefinition d) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
      };
    }

    @SuppressWarnings("unchecked")
    private static <T> T inerte(Class<T> puerto) {
      return (T)
          Proxy.newProxyInstance(
              puerto.getClassLoader(),
              new Class<?>[] {puerto},
              (proxy, metodo, args) -> {
                throw new UnsupportedOperationException(
                    "Esta prueba no debía llegar a "
                        + puerto.getSimpleName()
                        + "."
                        + metodo.getName());
              });
    }
  }

  /** Lo que el almacén borró, en un bean propio: un {@code Set<String>} inyectado junta Strings. */
  static final class ObjetosBorrados {
    final Set<String> claves = new HashSet<>();
  }

  static final class RepositorioBorradoresDoble implements RepositorioBorradores {
    final Map<UUID, BorradorProducto> porId = new LinkedHashMap<>();

    @Override
    public void guardar(BorradorProducto borrador) {
      porId.put(borrador.id(), borrador);
    }

    @Override
    public void actualizar(BorradorProducto borrador) {
      porId.put(borrador.id(), borrador);
    }

    @Override
    public Optional<BorradorProducto> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public BorradoresPaginados listar(
        EstadoBorrador estado, UUID proveedorId, int pagina, int tamanoPagina) {
      List<BorradorProducto> items =
          porId.values().stream()
              .filter(b -> estado == null || b.estado() == estado)
              .filter(b -> proveedorId == null || b.proveedorId().equals(proveedorId))
              .sorted(Comparator.comparing(BorradorProducto::creadoEn).reversed())
              .toList();
      return new BorradoresPaginados(items, 0, 1, items.size());
    }

    @Override
    public List<HuellaVisual> huellasVisualesDelProveedor(UUID proveedorId) {
      return List.of();
    }

    @Override
    public boolean existeEnRevisionConHuella(UUID proveedorId, HuellaProveedor huella) {
      return false;
    }

    @Override
    public long contarDePublicacion(UUID publicacionId) {
      return porId.values().stream().filter(b -> b.publicacionId().equals(publicacionId)).count();
    }

    @Override
    public void eliminar(UUID id) {
      porId.remove(id);
    }
  }

  static final class RepositorioPublicacionesDoble implements RepositorioPublicacionesProveedor {
    final Map<UUID, PublicacionProveedor> porId = new LinkedHashMap<>();

    @Override
    public void guardarTodas(List<PublicacionProveedor> publicaciones) {
      publicaciones.forEach(p -> porId.put(p.id(), p));
    }

    @Override
    public void actualizar(PublicacionProveedor publicacion) {
      porId.put(publicacion.id(), publicacion);
    }

    @Override
    public Optional<PublicacionProveedor> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<PublicacionProveedor> listarDeLote(UUID loteId) {
      return porId.values().stream().filter(p -> p.loteId().equals(loteId)).toList();
    }

    @Override
    public void eliminar(UUID id) {
      porId.remove(id);
    }

    @Override
    public Set<UUID> mensajesUsadosPorOtras(
        UUID publicacionId, java.util.Collection<UUID> mensajeIds) {
      Set<UUID> usados = new java.util.HashSet<>();
      for (PublicacionProveedor otra : porId.values()) {
        if (otra.id().equals(publicacionId)) {
          continue;
        }
        List<UUID> deOtra = new java.util.ArrayList<>(otra.textosAdicionales());
        deOtra.addAll(otra.medios());
        deOtra.add(otra.mensajePrincipalId());
        deOtra.stream().filter(mensajeIds::contains).forEach(usados::add);
      }
      return usados;
    }
  }

  static final class RepositorioMensajesDoble implements RepositorioMensajesProveedor {
    final Map<UUID, List<MensajeProveedor>> porLote = new LinkedHashMap<>();

    @Override
    public void guardarTodos(List<MensajeProveedor> mensajes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<IdExternoDeMensaje> idsExternosExistentes(
        UUID proveedorId, java.util.Collection<IdExternoDeMensaje> candidatos) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<MensajeProveedor> listarDeLote(UUID loteId) {
      return porLote.getOrDefault(loteId, List.of());
    }

    @Override
    public void eliminarTodos(java.util.Collection<UUID> ids) {
      porLote.replaceAll((lote, l) -> l.stream().filter(m -> !ids.contains(m.id())).toList());
    }
  }
}
