// Sin exigir productoId en APROBADO ni en RENOVACION_APLICADA: nacen con él, pero el producto
// se puede borrar del catálogo después y la constancia tiene que poder releerse sin él.
package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que el extractor sacó de una publicación, esperando a que una persona lo apruebe.
 *
 * <p>Guarda el JSON completo que devolvió el extractor además de los campos normalizados: los
 * campos son lo que el panel edita, y el JSON es lo que permite ver qué dijo el modelo antes de que
 * nadie lo tocara. Cuando el prompt cambie, es lo que deja comparar.
 *
 * <h2>Un borrador con alertas nunca se publica solo</h2>
 *
 * <p>{@link #esPublicableAutomaticamente()} es falso con cualquier alerta, y hoy es falso siempre
 * porque la publicación automática no está encendida para ningún proveedor. Existe para que el día
 * que se encienda, la regla ya esté escrita aquí y no en el caso de uso.
 */
public final class BorradorProducto {

  private final UUID id;
  private final UUID publicacionId;
  private final UUID proveedorId;
  private final String extraccionCruda;
  private String titulo;
  private LineaCatalogo linea;
  private TipoProductoProveedor tipo;
  private Dinero precioProveedor;
  private Dinero precioVentaSugerido;
  private Tallas tallas;
  private Integer cantidadTonos;
  private List<String> tonosNombrados;
  private String material;
  private String descripcion;
  private String altEn;
  private final HuellaProveedor huella;
  private PHash pHash;
  private final Set<AlertaBorrador> alertas;
  private final Set<UUID> fotosDescartadas;
  private EstadoBorrador estado;
  private UUID productoId;
  private String motivoRechazo;
  private final Instant creadoEn;

  public BorradorProducto(
      UUID id,
      UUID publicacionId,
      UUID proveedorId,
      String extraccionCruda,
      String titulo,
      LineaCatalogo linea,
      TipoProductoProveedor tipo,
      Dinero precioProveedor,
      Dinero precioVentaSugerido,
      Tallas tallas,
      Integer cantidadTonos,
      List<String> tonosNombrados,
      String material,
      String descripcion,
      String altEn,
      HuellaProveedor huella,
      PHash pHash,
      Set<AlertaBorrador> alertas,
      Set<UUID> fotosDescartadas,
      EstadoBorrador estado,
      UUID productoId,
      String motivoRechazo,
      Instant creadoEn) {
    this.id = Objects.requireNonNull(id, "El id del borrador no puede ser nulo.");
    this.publicacionId =
        Objects.requireNonNull(publicacionId, "Un borrador sale de una publicación.");
    this.proveedorId = Objects.requireNonNull(proveedorId, "Un borrador es de un proveedor.");
    this.extraccionCruda =
        Objects.requireNonNull(extraccionCruda, "El borrador guarda lo que devolvió el extractor.");
    this.titulo = enBlancoEsNulo(titulo);
    this.linea = linea;
    this.tipo = tipo == null ? TipoProductoProveedor.OTRO : tipo;
    this.precioProveedor = precioProveedor;
    this.precioVentaSugerido = precioVentaSugerido;
    this.tallas = tallas == null ? Tallas.desconocida() : tallas;
    this.cantidadTonos = cantidadTonos;
    this.tonosNombrados = tonosNombrados == null ? List.of() : List.copyOf(tonosNombrados);
    this.material = enBlancoEsNulo(material);
    this.descripcion = enBlancoEsNulo(descripcion);
    this.altEn = enBlancoEsNulo(altEn);
    this.huella = huella;
    this.pHash = pHash;
    // EnumSet.copyOf revienta con una colección vacía que no sea EnumSet; se copia a mano.
    this.alertas = EnumSet.noneOf(AlertaBorrador.class);
    if (alertas != null) {
      this.alertas.addAll(alertas);
    }
    this.fotosDescartadas = new LinkedHashSet<>();
    if (fotosDescartadas != null) {
      this.fotosDescartadas.addAll(fotosDescartadas);
    }
    this.estado = Objects.requireNonNull(estado, "El estado del borrador no puede ser nulo.");
    this.productoId = productoId;
    this.motivoRechazo = enBlancoEsNulo(motivoRechazo);
    this.creadoEn = Objects.requireNonNull(creadoEn, "El borrador tiene fecha.");

    if ((estado == EstadoBorrador.APROBADO || estado == EstadoBorrador.RENOVACION_APLICADA)
        && productoId == null) {
      throw new ExcepcionDeDominio("Un borrador " + estado + " apunta a un producto.");
    }
    if (estado == EstadoBorrador.RECHAZADO && this.motivoRechazo == null) {
      throw new ExcepcionDeDominio("Un borrador rechazado dice por qué.");
    }
    if (cantidadTonos != null && cantidadTonos < 0) {
      throw new ExcepcionDeDominio("La cantidad de tonos no puede ser negativa.");
    }
  }

  /** Un producto nuevo, a la espera de revisión. */
  public static BorradorProducto nuevo(
      UUID publicacionId,
      UUID proveedorId,
      ProductoExtraido extraido,
      String extraccionCruda,
      Dinero precioProveedor,
      Dinero precioVentaSugerido,
      HuellaProveedor huella,
      PHash pHash,
      Set<AlertaBorrador> alertas,
      Instant ahora) {
    Objects.requireNonNull(extraido, "El borrador nace de una extracción.");
    return new BorradorProducto(
        GeneradorIdentificador.nuevo(),
        publicacionId,
        proveedorId,
        extraccionCruda,
        extraido.titulo(),
        extraido.linea(),
        extraido.tipo(),
        precioProveedor,
        precioVentaSugerido,
        extraido.tallas(),
        extraido.cantidadTonos(),
        extraido.tonosNombrados(),
        extraido.material(),
        extraido.descripcion(),
        extraido.altEn(),
        huella,
        pHash,
        alertas,
        Set.of(),
        EstadoBorrador.EN_REVISION,
        null,
        null,
        ahora);
  }

  /**
   * La constancia de una renovación: el mensaje reconoció un producto que ya existía. No pasa por
   * revisión, pero deja escrito qué dijo el proveedor y con qué alertas —{@code PRECIO_CAMBIO}, si
   * cambió el precio— para que el panel las enseñe.
   */
  public static BorradorProducto renovacionAplicada(
      UUID publicacionId,
      UUID proveedorId,
      UUID productoId,
      ProductoExtraido extraido,
      String extraccionCruda,
      Dinero precioProveedor,
      HuellaProveedor huella,
      PHash pHash,
      Set<AlertaBorrador> alertas,
      Instant ahora) {
    Objects.requireNonNull(extraido, "La renovación nace de una extracción.");
    return new BorradorProducto(
        GeneradorIdentificador.nuevo(),
        publicacionId,
        proveedorId,
        extraccionCruda,
        extraido.titulo(),
        extraido.linea(),
        extraido.tipo(),
        precioProveedor,
        null,
        extraido.tallas(),
        extraido.cantidadTonos(),
        extraido.tonosNombrados(),
        extraido.material(),
        extraido.descripcion(),
        extraido.altEn(),
        huella,
        pHash,
        alertas,
        Set.of(),
        EstadoBorrador.RENOVACION_APLICADA,
        productoId,
        null,
        ahora);
  }

  /** Lo que el panel deja cambiar antes de aprobar. Lo que llegue nulo se queda como estaba. */
  public void editar(
      String titulo,
      TipoProductoProveedor tipo,
      Dinero precioVentaSugerido,
      Tallas tallas,
      Integer cantidadTonos,
      List<String> tonosNombrados,
      String material,
      String descripcion,
      String altEn) {
    exigirEnRevision("editar");
    if (titulo != null) {
      this.titulo = enBlancoEsNulo(titulo);
    }
    if (tipo != null) {
      this.tipo = tipo;
    }
    if (precioVentaSugerido != null) {
      this.precioVentaSugerido = precioVentaSugerido;
    }
    if (tallas != null) {
      this.tallas = tallas;
    }
    if (cantidadTonos != null) {
      if (cantidadTonos < 0) {
        throw new ExcepcionDeDominio("La cantidad de tonos no puede ser negativa.");
      }
      this.cantidadTonos = cantidadTonos;
    }
    if (tonosNombrados != null) {
      this.tonosNombrados = List.copyOf(tonosNombrados);
    }
    if (material != null) {
      this.material = enBlancoEsNulo(material);
    }
    if (descripcion != null) {
      this.descripcion = enBlancoEsNulo(descripcion);
    }
    if (altEn != null) {
      this.altEn = enBlancoEsNulo(altEn);
    }
  }

  /**
   * Quien revisa saca una foto que no es de este producto. No se borra el archivo: la foto es de la
   * publicación, y otro borrador del mismo mensaje puede usarla; solo deja de ofrecerse aquí.
   */
  public void descartarFoto(UUID mensajeId) {
    exigirEnRevision("descartar fotos de");
    fotosDescartadas.add(Objects.requireNonNull(mensajeId, "La foto no puede ser nula."));
  }

  /**
   * @param pHashDeLaPrincipal el de la foto que quien aprueba eligió como principal, o nulo si no
   *     se pudo calcular. Solo se usa cuando el borrador no trae uno: el de un mensaje con varios
   *     productos nace sin huella visual, porque la primera foto podía ser de cualquiera de ellos,
   *     y es aquí donde una persona dice cuál es de este.
   */
  public void aprobar(UUID productoId, PHash pHashDeLaPrincipal) {
    exigirEnRevision("aprobar");
    this.productoId = Objects.requireNonNull(productoId, "Aprobar es crear un producto.");
    this.estado = EstadoBorrador.APROBADO;
    if (this.pHash == null) {
      this.pHash = pHashDeLaPrincipal;
    }
  }

  public void rechazar(String motivo) {
    exigirEnRevision("rechazar");
    String porQue = enBlancoEsNulo(motivo);
    if (porQue == null) {
      throw new ExcepcionDeDominio("Rechazar un borrador exige el motivo.");
    }
    this.estado = EstadoBorrador.RECHAZADO;
    this.motivoRechazo = porQue;
  }

  public boolean tieneAlertas() {
    return !alertas.isEmpty();
  }

  /** Nunca con alertas. Y hoy nunca: la publicación automática no está encendida para nadie. */
  public boolean esPublicableAutomaticamente() {
    return estado == EstadoBorrador.EN_REVISION && alertas.isEmpty();
  }

  private void exigirEnRevision(String accion) {
    if (estado != EstadoBorrador.EN_REVISION) {
      throw new ExcepcionDeDominio(
          "Solo se puede " + accion + " un borrador en revisión; este está " + estado + ".");
    }
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }

  public UUID id() {
    return id;
  }

  public UUID publicacionId() {
    return publicacionId;
  }

  public UUID proveedorId() {
    return proveedorId;
  }

  public String extraccionCruda() {
    return extraccionCruda;
  }

  public Optional<String> titulo() {
    return Optional.ofNullable(titulo);
  }

  public Optional<LineaCatalogo> linea() {
    return Optional.ofNullable(linea);
  }

  public TipoProductoProveedor tipo() {
    return tipo;
  }

  public Optional<Dinero> precioProveedor() {
    return Optional.ofNullable(precioProveedor);
  }

  public Optional<Dinero> precioVentaSugerido() {
    return Optional.ofNullable(precioVentaSugerido);
  }

  public Tallas tallas() {
    return tallas;
  }

  public Optional<Integer> cantidadTonos() {
    return Optional.ofNullable(cantidadTonos);
  }

  public List<String> tonosNombrados() {
    return tonosNombrados;
  }

  public Optional<String> material() {
    return Optional.ofNullable(material);
  }

  /** Lo que la ficha va a decir del producto. Sin ella el borrador no se aprueba. */
  public Optional<String> descripcion() {
    return Optional.ofNullable(descripcion);
  }

  /** El título en inglés, que el panel propone como texto alternativo de las fotos. */
  public Optional<String> altEn() {
    return Optional.ofNullable(altEn);
  }

  public Optional<HuellaProveedor> huella() {
    return Optional.ofNullable(huella);
  }

  public Optional<PHash> pHash() {
    return Optional.ofNullable(pHash);
  }

  public Set<AlertaBorrador> alertas() {
    return Set.copyOf(alertas);
  }

  /** Las fotos de la publicación que quien revisa sacó de este borrador, por mensaje. */
  public Set<UUID> fotosDescartadas() {
    return Set.copyOf(fotosDescartadas);
  }

  public EstadoBorrador estado() {
    return estado;
  }

  public Optional<UUID> productoId() {
    return Optional.ofNullable(productoId);
  }

  public Optional<String> motivoRechazo() {
    return Optional.ofNullable(motivoRechazo);
  }

  public Instant creadoEn() {
    return creadoEn;
  }
}
