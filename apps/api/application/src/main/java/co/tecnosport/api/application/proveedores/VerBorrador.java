package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoSubida;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El borrador con lo que hace falta para revisarlo: el texto original del proveedor y sus fotos,
 * cada una con una URL firmada de lectura porque el bucket es privado. Las que quien revisa
 * descartó no vienen. Las que se subieron desde el panel van después de las del proveedor.
 *
 * <p>Cada foto trae los colores que la lectura de fotos vio en ella: el sugerido si mostraba uno
 * solo, y todos si mostraba varios —la foto que retrata la blusa en negro, cocoa y verde—. Esos son
 * los tonos que el panel propone como prendas sin foto propia (12 de octubre de 2026).
 *
 * <p>Y si la publicación es un álbum —La Riverah, un texto y un SKU impreso en cada foto—, trae las
 * prendas del álbum: una por SKU, con sus fotos y las tallas de su pie (13 de octubre de 2026). Es
 * lo que el panel propone al aprobar, y el SKU de cada una queda en sus variantes.
 */
public final class VerBorrador {

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final RepositorioMensajesProveedor repositorioMensajes;
  private final AlmacenDeArchivosDeProveedor almacen;
  private final InterpreteDeLecturaDeFotos interprete;

  public VerBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      RepositorioMensajesProveedor repositorioMensajes,
      AlmacenDeArchivosDeProveedor almacen,
      InterpreteDeLecturaDeFotos interprete) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.repositorioMensajes = Objects.requireNonNull(repositorioMensajes);
    this.almacen = Objects.requireNonNull(almacen);
    this.interprete = Objects.requireNonNull(interprete);
  }

  /** Sin lecturas que interpretar: ninguna foto trae los tonos de una foto de varios. */
  public VerBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      RepositorioMensajesProveedor repositorioMensajes,
      AlmacenDeArchivosDeProveedor almacen) {
    this(
        repositorioBorradores,
        repositorioPublicaciones,
        repositorioMensajes,
        almacen,
        InterpreteDeLecturaDeFotos.NINGUNO);
  }

  public DetalleDeBorrador ejecutar(UUID borradorId) {
    BorradorProducto borrador =
        repositorioBorradores
            .buscarPorId(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    PublicacionProveedor publicacion =
        repositorioPublicaciones
            .buscarPorId(borrador.publicacionId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "El borrador apunta a una publicación que no existe."));
    Map<UUID, MensajeProveedor> mensajes =
        repositorioMensajes.listarDeLote(publicacion.loteId()).stream()
            .collect(Collectors.toMap(MensajeProveedor::id, Function.identity()));

    List<String> textos = new ArrayList<>();
    MensajeProveedor principal = mensajes.get(publicacion.mensajePrincipalId());
    if (principal != null) {
      principal.textoLegible().ifPresent(textos::add);
    }
    for (UUID id : publicacion.textosAdicionales()) {
      MensajeProveedor mensaje = mensajes.get(id);
      if (mensaje != null) {
        mensaje.textoLegible().ifPresent(textos::add);
      }
    }
    List<BorradorProducto> deLaPublicacion =
        repositorioBorradores.listarDePublicacion(publicacion.id());
    Map<Integer, List<String>> tonosVistos =
        tonosDeFotosDeVarios(borrador, publicacion.medios(), deLaPublicacion);
    List<FotoDeBorrador> fotos = new ArrayList<>();
    List<UUID> medios = publicacion.medios();
    for (int i = 0; i < medios.size(); i++) {
      UUID id = medios.get(i);
      MensajeProveedor mensaje = mensajes.get(id);
      if (mensaje == null || borrador.fotosDescartadas().contains(id)) {
        continue;
      }
      fotos.add(
          new FotoDeBorrador(
              mensaje.id(),
              mensaje.referenciaArchivo().map(r -> almacen.urlDeLectura(r).url()).orElse(null),
              mensaje.pieDeFoto().orElse(null),
              OrigenDeFoto.PROVEEDOR,
              borrador.tonosSugeridos().get(id),
              tonosVistos.getOrDefault(i, List.of())));
    }
    // Las de otras publicaciones que se le sumaron: la misma referencia publicada otra vez, o una
    // foto que quien revisa movió aquí. Las que ya no existen —se borró su lote— no se pintan.
    for (MensajeProveedor agregada : repositorioMensajes.buscarPorIds(borrador.fotosAgregadas())) {
      fotos.add(
          new FotoDeBorrador(
              agregada.id(),
              agregada.referenciaArchivo().map(r -> almacen.urlDeLectura(r).url()).orElse(null),
              agregada.pieDeFoto().orElse(null),
              OrigenDeFoto.PROVEEDOR,
              borrador.tonosSugeridos().get(agregada.id())));
    }
    for (FotoSubida subida : borrador.fotosSubidas()) {
      fotos.add(
          new FotoDeBorrador(
              subida.id(),
              almacen.urlDeLectura(subida.referenciaArchivo()).url(),
              null,
              OrigenDeFoto.PANEL));
    }
    List<Hermano> hermanos =
        deLaPublicacion.stream()
            .filter(otro -> !otro.id().equals(borrador.id()))
            .filter(otro -> otro.estado() == EstadoBorrador.EN_REVISION)
            .map(
                otro ->
                    new Hermano(
                        otro.id(), otro.titulo().orElse(null), borrador.mismoDisenoQue(otro)))
            .toList();
    return new DetalleDeBorrador(
        borrador, publicacion, textos, fotos, hermanos, prendasDelAlbum(borrador, medios));
  }

  /**
   * Las prendas del álbum que este borrador conserva: un diseño cuyas fotos descartó todas ya no es
   * suyo.
   */
  private List<PrendaDelAlbum> prendasDelAlbum(BorradorProducto borrador, List<UUID> medios) {
    Optional<LecturaDeFotos> lectura = borrador.lecturaDeFotos().flatMap(interprete::interpretar);
    if (lectura.isEmpty()) {
      return List.of();
    }
    List<PrendaDelAlbum> prendas = new ArrayList<>();
    for (RepartoDeFotos.DisenoDeAlbum diseno :
        RepartoDeFotos.disenosDelAlbum(medios, lectura.get())) {
      List<UUID> suyas =
          diseno.fotos().stream().filter(f -> !borrador.fotosDescartadas().contains(f)).toList();
      if (!suyas.isEmpty()) {
        prendas.add(new PrendaDelAlbum(diseno.sku(), diseno.tallas(), suyas));
      }
    }
    return List.copyOf(prendas);
  }

  /**
   * Los tonos que la lectura vio en cada foto que muestra varios, por su posición en la
   * publicación. Solo las fotos que este borrador tiene para él solo: una foto que otro producto de
   * la publicación también conserva puede retratar los colores de ese otro, y proponerlos aquí
   * crearía variantes que este no tiene —es la misma cautela con que la extracción solo toma los
   * tonos de las fotos cuando son suyas—.
   */
  private Map<Integer, List<String>> tonosDeFotosDeVarios(
      BorradorProducto borrador, List<UUID> medios, List<BorradorProducto> deLaPublicacion) {
    Optional<LecturaDeFotos> lectura = borrador.lecturaDeFotos().flatMap(interprete::interpretar);
    if (lectura.isEmpty()) {
      return Map.of();
    }
    Map<Integer, List<String>> porPosicion = new HashMap<>();
    for (LecturaDeFotos.LecturaDeFoto foto : lectura.get().fotos()) {
      if (foto.colores().size() < 2 || foto.posicion() >= medios.size()) {
        continue;
      }
      UUID id = medios.get(foto.posicion());
      boolean deOtro =
          deLaPublicacion.stream()
              .filter(otro -> !otro.id().equals(borrador.id()))
              .filter(otro -> otro.estado() != EstadoBorrador.RECHAZADO)
              .anyMatch(otro -> !otro.fotosDescartadas().contains(id));
      if (!deOtro) {
        porPosicion.put(foto.posicion(), foto.colores());
      }
    }
    return porPosicion;
  }

  /**
   * @param hermanos los otros borradores en revisión de la misma publicación —el otro producto del
   *     conjunto, los otros diseños del álbum—: a donde el panel deja mover una foto
   */
  public record DetalleDeBorrador(
      BorradorProducto borrador,
      PublicacionProveedor publicacion,
      List<String> textos,
      List<FotoDeBorrador> fotos,
      List<Hermano> hermanos,
      List<PrendaDelAlbum> prendasDelAlbum) {

    public DetalleDeBorrador {
      prendasDelAlbum = prendasDelAlbum == null ? List.of() : List.copyOf(prendasDelAlbum);
    }

    public DetalleDeBorrador(
        BorradorProducto borrador,
        PublicacionProveedor publicacion,
        List<String> textos,
        List<FotoDeBorrador> fotos,
        List<Hermano> hermanos) {
      this(borrador, publicacion, textos, fotos, hermanos, List.of());
    }

    public DetalleDeBorrador(
        BorradorProducto borrador,
        PublicacionProveedor publicacion,
        List<String> textos,
        List<FotoDeBorrador> fotos) {
      this(borrador, publicacion, textos, fotos, List.of());
    }
  }

  /**
   * Otro borrador de la misma publicación, como lo nombra el panel.
   *
   * @param mismoDiseno la lectura de fotos vio en él el mismo diseño que en este: posiblemente es
   *     el mismo producto en otro color, y el panel ofrece unirlos ({@link UnirBorradores})
   */
  public record Hermano(UUID id, String titulo, boolean mismoDiseno) {
    public Hermano(UUID id, String titulo) {
      this(id, titulo, false);
    }
  }

  /**
   * Una prenda de un álbum, como la propone el panel.
   *
   * @param referencia el SKU del pie de sus fotos
   * @param tallas las de su pie; vacía si no las dice
   * @param fotos las suyas que el borrador conserva, por mensaje y en orden
   */
  public record PrendaDelAlbum(String referencia, List<String> tallas, List<UUID> fotos) {
    public PrendaDelAlbum {
      Objects.requireNonNull(referencia);
      tallas = List.copyOf(tallas);
      fotos = List.copyOf(fotos);
    }
  }

  /** De dónde salió la foto: del mensaje del proveedor o de quien revisa, desde el panel. */
  public enum OrigenDeFoto {
    PROVEEDOR,
    PANEL
  }

  /**
   * @param mensajeId el id de la foto: el del mensaje si la mandó el proveedor, el de la {@link
   *     FotoSubida} si se subió desde el panel. Se llama así por el contrato que ya usaba la
   *     aprobación; los dos son UUID generados aquí y no chocan.
   * @param url firmada y de vida corta; nula cuando la exportación omitió el archivo
   * @param tonoSugerido el color que la lectura de fotos vio en ella; nulo si no hay sugerencia
   * @param tonosVistos los colores que la lectura vio en ella cuando muestra varios —la foto con
   *     todos los tonos del producto—, como los nombró: «cocoa», no el de la paleta. Vacía si
   *     muestra uno solo o ninguno
   */
  public record FotoDeBorrador(
      UUID mensajeId,
      String url,
      String pieDeFoto,
      OrigenDeFoto origen,
      String tonoSugerido,
      List<String> tonosVistos) {

    public FotoDeBorrador {
      tonosVistos = tonosVistos == null ? List.of() : List.copyOf(tonosVistos);
    }

    public FotoDeBorrador(
        UUID mensajeId, String url, String pieDeFoto, OrigenDeFoto origen, String tonoSugerido) {
      this(mensajeId, url, pieDeFoto, origen, tonoSugerido, List.of());
    }

    public FotoDeBorrador(UUID mensajeId, String url, String pieDeFoto, OrigenDeFoto origen) {
      this(mensajeId, url, pieDeFoto, origen, null);
    }
  }
}
