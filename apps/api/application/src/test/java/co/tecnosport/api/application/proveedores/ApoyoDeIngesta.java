package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.ChatDelZip;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Dobles en memoria de los puertos de la ingesta, escritos a mano. */
final class ApoyoDeIngesta {

  static final Instant AHORA = Instant.parse("2026-09-28T15:15:00Z");
  static final String REMITENTE = "Bolsos Centro";

  private ApoyoDeIngesta() {}

  static Proveedor proveedorDeBolsos() {
    return Proveedor.crear(
        "Bolsos del Centro",
        LineaCatalogo.BOLSOS,
        "+57 300 123 4567",
        REMITENTE,
        null,
        OrdenDePublicacion.FOTOS_PRIMERO);
  }

  static MensajeCrudo.Adjunto foto(String nombre) {
    return new MensajeCrudo.Adjunto(
        nombre, "image/jpeg", ("bytes de " + nombre).getBytes(StandardCharsets.UTF_8));
  }

  static final class RepositorioProveedoresEnMemoria implements RepositorioProveedores {

    private final Map<UUID, Proveedor> porId = new LinkedHashMap<>();
    final Map<UUID, DependenciasDeProveedor> dependencias = new HashMap<>();
    final List<UUID> eliminados = new ArrayList<>();

    @Override
    public void guardar(Proveedor proveedor) {
      porId.put(proveedor.id(), proveedor);
    }

    @Override
    public void actualizar(Proveedor proveedor) {
      if (!porId.containsKey(proveedor.id())) {
        throw new IllegalStateException("No se actualiza lo que no se guardó.");
      }
      porId.put(proveedor.id(), proveedor);
    }

    @Override
    public Optional<Proveedor> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<Proveedor> listar() {
      return porId.values().stream().sorted(Comparator.comparing(Proveedor::nombre)).toList();
    }

    @Override
    public DependenciasDeProveedor dependenciasDe(UUID id) {
      return dependencias.getOrDefault(id, new DependenciasDeProveedor(0, false, List.of()));
    }

    @Override
    public void eliminarConSuHistorial(UUID id) {
      porId.remove(id);
      eliminados.add(id);
    }
  }

  static final class RepositorioLotesEnMemoria implements RepositorioLotesIngesta {

    private final Map<UUID, LoteIngesta> porId = new LinkedHashMap<>();
    int actualizaciones;

    /**
     * Con {@code true}, cada lectura devuelve un objeto propio, como la base: quien se queda con
     * una copia vieja y la escribe pisa lo que otro guardó. Sin esto, el panel y el trabajador de
     * una prueba comparten la misma instancia y "releer antes de escribir" no se puede probar —
     * escribir sobre la copia vieja pasaría en verde—. Por omisión, la instancia compartida, que es
     * lo que esperan las pruebas que la crean y luego la miran.
     */
    boolean devolverCopias;

    @Override
    public void guardar(LoteIngesta lote) {
      porId.put(lote.id(), devolverCopias ? copia(lote) : lote);
    }

    @Override
    public void actualizar(LoteIngesta lote) {
      if (!porId.containsKey(lote.id())) {
        throw new IllegalStateException("No se actualiza lo que no se guardó.");
      }
      actualizaciones++;
      porId.put(lote.id(), devolverCopias ? copia(lote) : lote);
    }

    @Override
    public Optional<LoteIngesta> buscarPorId(UUID id) {
      LoteIngesta lote = porId.get(id);
      return Optional.ofNullable(lote == null || !devolverCopias ? lote : copia(lote));
    }

    private static LoteIngesta copia(LoteIngesta lote) {
      return new LoteIngesta(
          lote.id(),
          lote.origen(),
          lote.proveedorId(),
          lote.referenciaArchivo().orElse(null),
          lote.estado(),
          lote.resumen().orElse(null),
          lote.detalleError().orElse(null),
          lote.creadoEn(),
          lote.iniciadoEn().orElse(null),
          lote.terminadoEn().orElse(null));
    }

    /** En memoria no hay nada que bloquear; se cuenta para poder afirmar que se pidió. */
    int bloqueos;

    @Override
    public Optional<LoteIngesta> buscarPorIdParaActualizar(UUID id) {
      bloqueos++;
      return buscarPorId(id);
    }

    @Override
    public List<LoteIngesta> abiertos() {
      return porId.values().stream()
          .filter(LoteIngesta::estaAbierto)
          .sorted(Comparator.comparing(LoteIngesta::creadoEn))
          .toList();
    }

    @Override
    public LotesPaginados listar(UUID proveedorId, int pagina, int tamanoPagina) {
      List<LoteIngesta> todos =
          porId.values().stream()
              .filter(l -> proveedorId == null || l.proveedorId().equals(proveedorId))
              .sorted(Comparator.comparing(LoteIngesta::creadoEn).reversed())
              .toList();
      return new LotesPaginados(todos, 0, 1, todos.size());
    }

    final Map<UUID, DependenciasDeLote> dependencias = new HashMap<>();
    final List<UUID> eliminados = new ArrayList<>();

    @Override
    public DependenciasDeLote dependenciasDe(UUID loteId) {
      return dependencias.getOrDefault(loteId, new DependenciasDeLote(List.of(), List.of()));
    }

    @Override
    public void eliminarConSuHistorial(UUID loteId) {
      eliminados.add(loteId);
      porId.remove(loteId);
    }
  }

  static final class RepositorioMensajesEnMemoria implements RepositorioMensajesProveedor {

    final List<MensajeProveedor> guardados = new ArrayList<>();

    @Override
    public void guardarTodos(List<MensajeProveedor> mensajes) {
      for (MensajeProveedor mensaje : mensajes) {
        boolean repetido =
            guardados.stream()
                .anyMatch(
                    g ->
                        g.proveedorId().equals(mensaje.proveedorId())
                            && g.idExterno().equals(mensaje.idExterno()));
        if (repetido) {
          throw new IllegalStateException(
              "Id externo repetido: es lo que la restricción única de la tabla impediría.");
        }
        guardados.add(mensaje);
      }
    }

    @Override
    public Set<IdExternoDeMensaje> idsExternosExistentes(
        UUID proveedorId, Collection<IdExternoDeMensaje> candidatos) {
      return guardados.stream()
          .filter(g -> g.proveedorId().equals(proveedorId))
          .map(MensajeProveedor::idExterno)
          .filter(candidatos::contains)
          .collect(Collectors.toSet());
    }

    @Override
    public List<MensajeProveedor> listarDeLote(UUID loteId) {
      return guardados.stream()
          .filter(g -> g.loteId().equals(loteId))
          .sorted(Comparator.comparing(MensajeProveedor::enviadoEn))
          .toList();
    }

    @Override
    public void eliminarTodos(Collection<UUID> ids) {
      guardados.removeIf(m -> ids.contains(m.id()));
    }
  }

  static final class AlmacenEnMemoria implements AlmacenDeArchivosDeProveedor {

    final Map<String, byte[]> objetos = new HashMap<>();
    final Map<String, String> tipos = new HashMap<>();

    @Override
    public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
      return new UrlFirmada("https://firmada.local/subir/" + objectKey + "?tipo=" + contentType);
    }

    @Override
    public Optional<Long> tamanoBytes(String objectKey) {
      return Optional.ofNullable(objetos.get(objectKey)).map(b -> (long) b.length);
    }

    @Override
    public void guardar(String objectKey, String contentType, byte[] bytes) {
      objetos.put(objectKey, bytes);
      tipos.put(objectKey, contentType);
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

  /** Devuelve siempre la misma lista, o revienta si se le dijo que reviente. */
  static final class FuenteFija implements FuenteDeMensajes {

    private final List<MensajeCrudo> mensajes;
    private final RuntimeException fallo;
    private final String nombreDelChat;
    String ultimaReferencia;
    int lecturas;

    FuenteFija(List<MensajeCrudo> mensajes) {
      this(null, mensajes);
    }

    /** Con el nombre del chat, como lo trae la exportación. */
    FuenteFija(String nombreDelChat, List<MensajeCrudo> mensajes) {
      this.mensajes = mensajes;
      this.fallo = null;
      this.nombreDelChat = nombreDelChat;
    }

    FuenteFija(RuntimeException fallo) {
      this.mensajes = List.of();
      this.fallo = fallo;
      this.nombreDelChat = null;
    }

    /** El chat del zip que se le pidió la última vez; nulo si se leyó como un solo chat. */
    ChatDelZip ultimoChat;

    @Override
    public ChatExportado leer(String referenciaArchivo, ChatDelZip chat) {
      ultimaReferencia = referenciaArchivo;
      ultimoChat = chat;
      lecturas++;
      if (fallo != null) {
        throw fallo;
      }
      return new ChatExportado(nombreDelChat, mensajes);
    }
  }

  static final class RepositorioPublicacionesEnMemoria
      implements RepositorioPublicacionesProveedor {

    final Map<UUID, PublicacionProveedor> porId = new LinkedHashMap<>();

    /**
     * Los textos del chat de caballero de un proveedor. El adaptador real los saca de los lotes y
     * los mensajes; aquí, por omisión, ninguno, y la prueba que los necesita lo conecta.
     */
    java.util.function.Function<UUID, List<String>> textosDeCaballero = proveedorId -> List.of();

    @Override
    public List<String> textosDelChatDeCaballero(UUID proveedorId) {
      return textosDeCaballero.apply(proveedorId);
    }

    @Override
    public void guardarTodas(List<PublicacionProveedor> publicaciones) {
      for (PublicacionProveedor publicacion : publicaciones) {
        porId.put(publicacion.id(), publicacion);
      }
    }

    @Override
    public void actualizar(PublicacionProveedor publicacion) {
      if (!porId.containsKey(publicacion.id())) {
        throw new IllegalStateException("No se actualiza lo que no se guardó.");
      }
      porId.put(publicacion.id(), publicacion);
    }

    @Override
    public Optional<PublicacionProveedor> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<PublicacionProveedor> listarDeLote(UUID loteId) {
      return porId.values().stream()
          .filter(p -> p.loteId().equals(loteId))
          .sorted(Comparator.comparing(PublicacionProveedor::fecha))
          .toList();
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

  /**
   * Devuelve siempre los mismos productos —o los que salgan de una función del texto— y recuerda
   * qué le mandaron.
   */
  static final class ExtractorFalso implements ExtractorDeProductos {

    private final java.util.function.Function<TextoDePublicacion, List<ProductoExtraido>> respuesta;
    TextoDePublicacion ultimoTexto;
    int llamadas;

    ExtractorFalso(ProductoExtraido respuesta) {
      this.respuesta = texto -> List.of(respuesta);
    }

    ExtractorFalso(RuntimeException fallo) {
      this.respuesta =
          texto -> {
            throw fallo;
          };
    }

    static ExtractorFalso porTexto(
        java.util.function.Function<TextoDePublicacion, ProductoExtraido> respuesta) {
      return new ExtractorFalso(respuesta.andThen(List::of), true);
    }

    /** Un mensaje que anuncia varios productos, como los conjuntos de Violeta. */
    static ExtractorFalso varios(List<ProductoExtraido> productos) {
      return new ExtractorFalso(texto -> productos, true);
    }

    static ExtractorFalso variosPorTexto(
        java.util.function.Function<TextoDePublicacion, List<ProductoExtraido>> respuesta) {
      return new ExtractorFalso(respuesta, true);
    }

    private ExtractorFalso(
        java.util.function.Function<TextoDePublicacion, List<ProductoExtraido>> respuesta,
        boolean lista) {
      this.respuesta = respuesta;
    }

    @Override
    public ResultadoExtraccion extraer(TextoDePublicacion texto) {
      ultimoTexto = texto;
      llamadas++;
      return new ResultadoExtraccion(
          respuesta.apply(texto), "{\"fixture\":true}", new UsoDelExtractor("falso", 10, 5, 1));
    }
  }

  /**
   * Hace las veces del panel mientras el trabajador espera: cada vez que se le pide esperar, corre
   * la siguiente acción de la lista. Sin acciones pendientes revienta, porque la prueba se
   * colgaría.
   */
  static final class EsperaGuionada implements EsperaDeIngesta {

    private final java.util.ArrayDeque<Runnable> acciones = new java.util.ArrayDeque<>();
    int esperas;

    EsperaGuionada luego(Runnable accion) {
      acciones.add(accion);
      return this;
    }

    @Override
    public void esperar() {
      esperas++;
      Runnable accion = acciones.poll();
      if (accion == null) {
        throw new IllegalStateException("El trabajador esperaría para siempre.");
      }
      accion.run();
    }
  }
}
