package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Raíz del catálogo. No se publica sin imagen principal (docs/00-producto.md). */
public final class Producto {

  private final UUID id;
  private final String nombre;
  private final Slug slug;
  private final String descripcion;
  private final Marca marca;
  private final Categoria categoria;
  private EstadoProducto estado;
  private ImagenProducto imagenPrincipal;
  private final List<ImagenProducto> galeria;
  private SetRotacion setRotacion;
  private final List<Variante> variantes;

  public Producto(
      UUID id,
      String nombre,
      Slug slug,
      String descripcion,
      Marca marca,
      Categoria categoria,
      EstadoProducto estado,
      ImagenProducto imagenPrincipal,
      List<ImagenProducto> galeria,
      SetRotacion setRotacion,
      List<Variante> variantes) {
    this.id = Objects.requireNonNull(id, "El id del producto no puede ser nulo.");
    if (nombre == null || nombre.isBlank()) {
      throw new ExcepcionDeDominio("El nombre del producto no puede estar vacío.");
    }
    this.nombre = nombre.trim();
    this.slug = Objects.requireNonNull(slug, "El slug del producto no puede ser nulo.");
    this.descripcion = descripcion == null ? "" : descripcion.trim();
    this.marca = Objects.requireNonNull(marca, "El producto necesita una marca.");
    this.categoria = Objects.requireNonNull(categoria, "El producto necesita una categoría.");
    this.estado = Objects.requireNonNull(estado, "El estado del producto no puede ser nulo.");
    if (imagenPrincipal != null && imagenPrincipal.tipo() != TipoImagen.PRINCIPAL) {
      throw new ImagenProductoInvalidaException("La imagen principal debe ser de tipo PRINCIPAL.");
    }
    this.imagenPrincipal = imagenPrincipal;
    this.galeria = new ArrayList<>(Objects.requireNonNullElse(galeria, List.of()));
    this.setRotacion = setRotacion;
    this.variantes = new ArrayList<>();
    for (Variante variante : Objects.requireNonNullElse(variantes, List.<Variante>of())) {
      agregarVariante(variante);
    }
  }

  public static Producto crear(
      String nombre, Slug slug, String descripcion, Marca marca, Categoria categoria) {
    return new Producto(
        GeneradorIdentificador.nuevo(),
        nombre,
        slug,
        descripcion,
        marca,
        categoria,
        EstadoProducto.BORRADOR,
        null,
        List.of(),
        null,
        List.of());
  }

  public void agregarVariante(Variante variante) {
    Objects.requireNonNull(variante, "La variante no puede ser nula.");
    boolean skuDuplicado = variantes.stream().anyMatch(v -> v.sku().equals(variante.sku()));
    if (skuDuplicado) {
      throw new SkuDuplicadoException(
          "El SKU '" + variante.sku().valor() + "' ya existe en este producto.");
    }
    variantes.add(variante);
  }

  public void asignarImagenPrincipal(ImagenProducto imagen) {
    if (imagen != null && imagen.tipo() != TipoImagen.PRINCIPAL) {
      throw new ImagenProductoInvalidaException("La imagen principal debe ser de tipo PRINCIPAL.");
    }
    this.imagenPrincipal = imagen;
  }

  public void asignarSetRotacion(SetRotacion setRotacion) {
    this.setRotacion = setRotacion;
  }

  public void publicar() {
    if (imagenPrincipal == null) {
      throw new ProductoSinImagenPrincipalException(
          "El producto '" + nombre + "' no se puede publicar sin imagen principal.");
    }
    this.estado = EstadoProducto.PUBLICADO;
  }

  public UUID id() {
    return id;
  }

  public String nombre() {
    return nombre;
  }

  public Slug slug() {
    return slug;
  }

  public String descripcion() {
    return descripcion;
  }

  public Marca marca() {
    return marca;
  }

  public Categoria categoria() {
    return categoria;
  }

  public EstadoProducto estado() {
    return estado;
  }

  public Optional<ImagenProducto> imagenPrincipal() {
    return Optional.ofNullable(imagenPrincipal);
  }

  public List<ImagenProducto> galeria() {
    return List.copyOf(galeria);
  }

  public Optional<SetRotacion> setRotacion() {
    return Optional.ofNullable(setRotacion);
  }

  public List<Variante> variantes() {
    return List.copyOf(variantes);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Producto otro && id.equals(otro.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
