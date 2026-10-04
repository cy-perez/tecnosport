package co.tecnosport.api.presentation.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.ColaDeIngestasLlenaException;
import co.tecnosport.api.application.proveedores.DependenciasDeLote;
import co.tecnosport.api.application.proveedores.DependenciasDeProveedor;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import co.tecnosport.api.application.proveedores.EliminacionDeProductos;
import co.tecnosport.api.application.proveedores.EliminarLoteDeIngesta;
import co.tecnosport.api.application.proveedores.IniciarIngesta;
import co.tecnosport.api.application.proveedores.LotesPaginados;
import co.tecnosport.api.application.proveedores.RepositorioLotesIngesta;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.application.proveedores.SolicitarSubidaDeExportacion;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Con los casos de uso de verdad y puertos falsos, como {@code AdminDifusionControladorTest}: lo
 * que se prueba es la cadena entera hasta el código HTTP, y sobre todo que la cola reciba el lote
 * <b>después</b> de la transacción y no dentro.
 */
@WebMvcTest(AdminIngestaControlador.class)
@Import({AdminIngestaControladorTest.Configuracion.class, ManejadorDeErrores.class})
class AdminIngestaControladorTest {

  private static final Instant AHORA = Instant.parse("2026-09-28T15:15:00Z");
  private static final long MAXIMO = 1_000;

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProveedoresDoble proveedores;
  @Autowired private RepositorioLotesDoble lotes;
  @Autowired private AlmacenDoble almacen;
  @Autowired private EjecutorDoble ejecutor;
  @Autowired private TransaccionEspia transaccion;
  @Autowired private EliminacionDoble eliminacion;

  private Proveedor proveedor;
  private String key;

  @BeforeEach
  void unProveedorConSuExportacionSubida() {
    proveedores.porId.clear();
    lotes.porId.clear();
    almacen.objetos.clear();
    ejecutor.encolados.clear();
    lotes.dependencias.clear();
    lotes.eliminados.clear();
    eliminacion.publicados.clear();
    eliminacion.eliminados.clear();
    ejecutor.llena = false;
    transaccion.abiertas = 0;
    ejecutor.transaccionesAbiertasAlEncolar = -1;

    proveedor =
        Proveedor.crear(
            "Bolsos del Centro", LineaCatalogo.BOLSOS, "+57 300", "Bolsos Centro", null);
    proveedores.porId.put(proveedor.id(), proveedor);
    key = "proveedores/" + proveedor.id() + "/exportaciones/abc.zip";
    almacen.objetos.put(key, new byte[500]);
  }

  @Test
  void laUrlDeSubidaCuelgaDelProveedor() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/ingestas/url-subida", proveedor.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"application/zip\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").exists())
        .andExpect(
            jsonPath("$.objectKey")
                .value(
                    org.hamcrest.Matchers.startsWith(
                        "proveedores/" + proveedor.id() + "/exportaciones/")));
  }

  @Test
  void unTipoQueNoEsZipEs422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/ingestas/url-subida", proveedor.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"text/plain\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("TIPO_DE_EXPORTACION_NO_ADMITIDO"));
  }

  @Test
  void iniciarResponde202ConElLoteYLoEncolaDespuesDeConfirmar() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/ingestas", proveedor.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectKey\":\"" + key + "\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.estado").value("RECIBIDO"))
        .andExpect(jsonPath("$.proveedorId").value(proveedor.id().toString()))
        .andExpect(jsonPath("$.resumen").doesNotExist());

    assertThat(ejecutor.encolados).hasSize(1);
    assertThat(lotes.porId).containsKey(ejecutor.encolados.get(0));
    assertThat(ejecutor.transaccionesAbiertasAlEncolar)
        .as("la cola recibe el lote con la transacción ya cerrada")
        .isZero();
  }

  @Test
  void unaKeyAjenaEs422YNoEncolaNada() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/ingestas", proveedor.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectKey\":\"proveedores/otro/exportaciones/abc.zip\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("EXPORTACION_NO_ENCONTRADA"));

    assertThat(ejecutor.encolados).isEmpty();
    assertThat(lotes.porId).isEmpty();
  }

  @Test
  void unaExportacionPorEncimaDelTopeEs413() throws Exception {
    almacen.objetos.put(key, new byte[(int) MAXIMO + 1]);

    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/ingestas", proveedor.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectKey\":\"" + key + "\"}"))
        .andExpect(status().isContentTooLarge())
        .andExpect(jsonPath("$.codigo").value("EXPORTACION_DEMASIADO_GRANDE"));
  }

  @Test
  void unProveedorInactivoEs409() throws Exception {
    proveedor.editar(
        proveedor.nombre(), LineaCatalogo.BOLSOS, "+57 300", "Bolsos Centro", false, false, null);

    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/ingestas", proveedor.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectKey\":\"" + key + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PROVEEDOR_INACTIVO"));
  }

  /**
   * La cola vive en memoria: un lote que no entró no lo toma nadie nunca. Queda escrito, sí, pero
   * en ERROR con su motivo, no en RECIBIDO esperando a un trabajador que no existe.
   */
  @Test
  void laColaLlenaEs503YElLoteQuedaEnErrorConSuMotivo() throws Exception {
    ejecutor.llena = true;

    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/ingestas", proveedor.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectKey\":\"" + key + "\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.codigo").value("COLA_DE_INGESTAS_LLENA"));

    LoteIngesta lote = lotes.porId.values().iterator().next();
    assertThat(lote.estado()).isEqualTo(EstadoLote.ERROR);
    assertThat(lote.detalleError()).hasValueSatisfying(m -> assertThat(m).contains("cola"));
  }

  @Test
  void unProveedorDesconocidoEs404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores/{id}/ingestas", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectKey\":\"" + key + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_ENCONTRADO"));
  }

  @Test
  void eliminarUnaIngestaBorraSusProductosNoPublicadosYDiceCuantosQuedan() throws Exception {
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), key, AHORA);
    lote.iniciar(AHORA.plusSeconds(1));
    lote.terminar(new ResumenIngesta(40, 5, 35, 9, 9, 0, 0, 0, 2), AHORA.plusSeconds(60));
    lotes.porId.put(lote.id(), lote);
    UUID borrador = UUID.randomUUID();
    UUID publicado = UUID.randomUUID();
    eliminacion.publicados.add(publicado);
    lotes.dependencias.put(
        lote.id(), new DependenciasDeLote(false, List.of(borrador, publicado), List.of(key)));

    mockMvc
        .perform(delete("/api/v1/admin/ingestas/{id}", lote.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productosEliminados").value(1))
        .andExpect(jsonPath("$.productosConservados").value(1));

    assertThat(lotes.eliminados).containsExactly(lote.id());
    assertThat(eliminacion.eliminados).containsExactly(borrador);
    assertThat(almacen.objetos).doesNotContainKey(key);
    assertThat(transaccion.abiertas).as("la transacción se cerró").isZero();
  }

  @Test
  void eliminarUnaIngestaEnCursoEs409YNoBorraNada() throws Exception {
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), key, AHORA);
    lotes.porId.put(lote.id(), lote);
    lotes.dependencias.put(lote.id(), new DependenciasDeLote(true, List.of(), List.of(key)));

    mockMvc
        .perform(delete("/api/v1/admin/ingestas/{id}", lote.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("LOTE_EN_CURSO"));

    assertThat(lotes.eliminados).isEmpty();
    assertThat(almacen.objetos).containsKey(key);
  }

  @Test
  void eliminarUnaIngestaQueNoExisteEs404() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/ingestas/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void verYListarDevuelvenElResumenCuandoLoHay() throws Exception {
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), key, AHORA);
    lote.iniciar(AHORA.plusSeconds(1));
    lote.terminar(new ResumenIngesta(40, 5, 35, 9, 9, 0, 0, 0, 2), AHORA.plusSeconds(60));
    lotes.porId.put(lote.id(), lote);

    mockMvc
        .perform(get("/api/v1/admin/ingestas/{id}", lote.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("TERMINADO"))
        .andExpect(jsonPath("$.resumen.borradoresNuevos").value(9))
        .andExpect(jsonPath("$.terminadoEn").value("2026-09-28T15:16:00Z"));

    mockMvc
        .perform(get("/api/v1/admin/ingestas").param("proveedorId", proveedor.id().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalLotes").value(1))
        .andExpect(jsonPath("$.items[0].id").value(lote.id().toString()));

    mockMvc
        .perform(get("/api/v1/admin/ingestas/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("LOTE_NO_ENCONTRADO"));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProveedoresDoble repositorioProveedores() {
      return new RepositorioProveedoresDoble();
    }

    @Bean
    RepositorioLotesDoble repositorioLotes() {
      return new RepositorioLotesDoble();
    }

    @Bean
    AlmacenDoble almacen() {
      return new AlmacenDoble();
    }

    @Bean
    TransaccionEspia transactionManager() {
      return new TransaccionEspia();
    }

    @Bean
    EjecutorDoble ejecutor(TransaccionEspia transaccion) {
      return new EjecutorDoble(transaccion);
    }

    @Bean
    EliminacionDoble eliminacionDeProductos() {
      return new EliminacionDoble();
    }

    @Bean
    EliminarLoteDeIngesta eliminarLote(
        RepositorioLotesDoble lotes, EliminacionDoble eliminacion, AlmacenDoble almacen) {
      return new EliminarLoteDeIngesta(lotes, eliminacion, almacen);
    }

    @Bean
    Reloj reloj() {
      return () -> AHORA;
    }

    @Bean
    SolicitarSubidaDeExportacion solicitarSubida(
        RepositorioProveedoresDoble proveedores, AlmacenDoble almacen) {
      return new SolicitarSubidaDeExportacion(proveedores, almacen);
    }

    @Bean
    IniciarIngesta iniciarIngesta(
        RepositorioProveedoresDoble proveedores,
        RepositorioLotesDoble lotes,
        AlmacenDoble almacen) {
      return new IniciarIngesta(proveedores, lotes, almacen, (Reloj) () -> AHORA, MAXIMO);
    }
  }

  static final class RepositorioProveedoresDoble implements RepositorioProveedores {
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
      return new ArrayList<>(porId.values());
    }

    @Override
    public DependenciasDeProveedor dependenciasDe(UUID id) {
      throw new UnsupportedOperationException("La ingesta no elimina proveedores.");
    }

    @Override
    public void eliminarConSuHistorial(UUID id) {
      throw new UnsupportedOperationException("La ingesta no elimina proveedores.");
    }
  }

  static final class RepositorioLotesDoble implements RepositorioLotesIngesta {
    final Map<UUID, LoteIngesta> porId = new LinkedHashMap<>();

    @Override
    public void guardar(LoteIngesta lote) {
      porId.put(lote.id(), lote);
    }

    @Override
    public void actualizar(LoteIngesta lote) {
      porId.put(lote.id(), lote);
    }

    @Override
    public Optional<LoteIngesta> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<LoteIngesta> abiertos() {
      return porId.values().stream().filter(LoteIngesta::estaAbierto).toList();
    }

    @Override
    public LotesPaginados listar(UUID proveedorId, int pagina, int tamanoPagina) {
      List<LoteIngesta> items =
          porId.values().stream()
              .filter(l -> proveedorId == null || l.proveedorId().equals(proveedorId))
              .sorted(Comparator.comparing(LoteIngesta::creadoEn).reversed())
              .toList();
      return new LotesPaginados(items, 0, 1, items.size());
    }

    final Map<UUID, DependenciasDeLote> dependencias = new HashMap<>();
    final List<UUID> eliminados = new ArrayList<>();

    @Override
    public DependenciasDeLote dependenciasDe(UUID loteId) {
      return dependencias.getOrDefault(loteId, new DependenciasDeLote(false, List.of(), List.of()));
    }

    @Override
    public void eliminarConSuHistorial(UUID loteId) {
      eliminados.add(loteId);
      porId.remove(loteId);
    }
  }

  /** Los productos de esta lista se quedan, como si estuvieran publicados. */
  static final class EliminacionDoble implements EliminacionDeProductos {
    final java.util.Set<UUID> publicados = new java.util.HashSet<>();
    final List<UUID> eliminados = new ArrayList<>();

    @Override
    public boolean eliminarSiSePuede(UUID productoId) {
      if (publicados.contains(productoId)) {
        return false;
      }
      eliminados.add(productoId);
      return true;
    }
  }

  static final class AlmacenDoble implements AlmacenDeArchivosDeProveedor {
    final Map<String, byte[]> objetos = new HashMap<>();

    @Override
    public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
      return new UrlFirmada("https://firmada.local/" + objectKey);
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

  /** Cuenta cuántas transacciones hay abiertas, para saber desde dónde se encoló. */
  static final class TransaccionEspia extends AbstractPlatformTransactionManager {
    int abiertas;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(
        Object transaction, org.springframework.transaction.TransactionDefinition d) {
      abiertas++;
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      abiertas--;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      abiertas--;
    }
  }

  static final class EjecutorDoble implements EjecutorDeIngestas {
    private final TransaccionEspia transaccion;
    final List<UUID> encolados = new ArrayList<>();
    boolean llena;
    int transaccionesAbiertasAlEncolar = -1;

    EjecutorDoble(TransaccionEspia transaccion) {
      this.transaccion = transaccion;
    }

    @Override
    public void encolar(UUID loteId) {
      if (llena) {
        throw new ColaDeIngestasLlenaException();
      }
      transaccionesAbiertasAlEncolar = transaccion.abiertas;
      encolados.add(loteId);
    }
  }
}
