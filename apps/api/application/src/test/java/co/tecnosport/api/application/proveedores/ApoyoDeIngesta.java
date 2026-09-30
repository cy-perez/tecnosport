package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
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
        "Bolsos del Centro", LineaCatalogo.BOLSOS, "+57 300 123 4567", REMITENTE, null);
  }

  static MensajeCrudo.Adjunto foto(String nombre) {
    return new MensajeCrudo.Adjunto(
        nombre, "image/jpeg", ("bytes de " + nombre).getBytes(StandardCharsets.UTF_8));
  }

  static final class RepositorioProveedoresEnMemoria implements RepositorioProveedores {

    private final Map<UUID, Proveedor> porId = new LinkedHashMap<>();

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
  }

  static final class RepositorioLotesEnMemoria implements RepositorioLotesIngesta {

    private final Map<UUID, LoteIngesta> porId = new LinkedHashMap<>();
    int actualizaciones;

    @Override
    public void guardar(LoteIngesta lote) {
      porId.put(lote.id(), lote);
    }

    @Override
    public void actualizar(LoteIngesta lote) {
      if (!porId.containsKey(lote.id())) {
        throw new IllegalStateException("No se actualiza lo que no se guardó.");
      }
      actualizaciones++;
      porId.put(lote.id(), lote);
    }

    @Override
    public Optional<LoteIngesta> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
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
  }

  /** Devuelve siempre la misma lista, o revienta si se le dijo que reviente. */
  static final class FuenteFija implements FuenteDeMensajes {

    private final List<MensajeCrudo> mensajes;
    private final RuntimeException fallo;
    String ultimaReferencia;
    int lecturas;

    FuenteFija(List<MensajeCrudo> mensajes) {
      this.mensajes = mensajes;
      this.fallo = null;
    }

    FuenteFija(RuntimeException fallo) {
      this.mensajes = List.of();
      this.fallo = fallo;
    }

    @Override
    public List<MensajeCrudo> leer(String referenciaArchivo) {
      ultimaReferencia = referenciaArchivo;
      lecturas++;
      if (fallo != null) {
        throw fallo;
      }
      return mensajes;
    }
  }

  static final class RepositorioPublicacionesEnMemoria
      implements RepositorioPublicacionesProveedor {

    final Map<UUID, PublicacionProveedor> porId = new LinkedHashMap<>();

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
  }

  /** Devuelve siempre el mismo producto y recuerda qué texto le mandaron. */
  static final class ExtractorFalso implements ExtractorDeProductos {

    private final ProductoExtraido respuesta;
    private final RuntimeException fallo;
    TextoDePublicacion ultimoTexto;
    int llamadas;

    ExtractorFalso(ProductoExtraido respuesta) {
      this.respuesta = respuesta;
      this.fallo = null;
    }

    ExtractorFalso(RuntimeException fallo) {
      this.respuesta = null;
      this.fallo = fallo;
    }

    @Override
    public ResultadoExtraccion extraer(TextoDePublicacion texto) {
      ultimoTexto = texto;
      llamadas++;
      if (fallo != null) {
        throw fallo;
      }
      return new ResultadoExtraccion(
          respuesta, "{\"fixture\":true}", new UsoDelExtractor("falso", 10, 5, 1));
    }
  }
}
