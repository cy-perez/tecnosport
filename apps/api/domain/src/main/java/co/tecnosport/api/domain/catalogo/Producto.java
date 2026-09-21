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

  /**
   * Cuántas imágenes caben en la galería, sin contar la principal.
   *
   * <p>No es un dato de negocio ni una regla que nadie pidió: es una barandilla. Las imágenes se
   * suben una por una desde el panel y desde un script, y sin tope un bucle equivocado llena la
   * ficha y el bucket sin que nada chille — el precio de eso es espacio pagado todos los meses y
   * una ficha que nadie puede recorrer. Ocho es holgado para las cuatro tomas del estándar de
   * estudio y para un producto que llegue con más. Si un día quedan cortas, se sube el número: es
   * reversible y no hay ningún dato que se pierda.
   */
  public static final int TOPE_DE_GALERIA = 8;

  private final UUID id;
  private String nombre;
  private final Slug slug;
  private String descripcion;
  private Marca marca;
  private Categoria categoria;
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
    for (ImagenProducto imagen : this.galeria) {
      if (imagen.tipo() != TipoImagen.GALERIA) {
        throw new ImagenProductoInvalidaException(
            "Una imagen de la galería no puede ser de tipo " + imagen.tipo() + ".");
      }
    }
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

  /**
   * Edita los datos descriptivos desde el panel admin. El {@code slug} no cambia: es el
   * identificador de URL estable del producto, no se regenera aunque cambie el nombre.
   */
  public void actualizarDatosBasicos(
      String nombre, String descripcion, Marca marca, Categoria categoria) {
    if (nombre == null || nombre.isBlank()) {
      throw new ExcepcionDeDominio("El nombre del producto no puede estar vacío.");
    }
    this.nombre = nombre.trim();
    this.descripcion = descripcion == null ? "" : descripcion.trim();
    this.marca = Objects.requireNonNull(marca, "El producto necesita una marca.");
    this.categoria = Objects.requireNonNull(categoria, "El producto necesita una categoría.");
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

  /**
   * Graba el paquete de una de sus variantes y devuelve la variante ya medida.
   *
   * <p>Pasa por el agregado y no por la variante suelta para que exista un sitio donde se compruebe
   * que <b>esa variante es de este producto</b>. Es la única regla que aquí hay que proteger, y es
   * real: un caso de uso que cargue el producto por un lado y aplique la medida por otro escribiría
   * el peso de un parlante en un celular sin que nada se queje, porque las dos cosas son enteros
   * positivos.
   */
  public Variante medirVariante(UUID varianteId, Paquete paquete) {
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    int posicion = -1;
    for (int i = 0; i < variantes.size(); i++) {
      if (variantes.get(i).id().equals(varianteId)) {
        posicion = i;
        break;
      }
    }
    if (posicion < 0) {
      throw new ExcepcionDeDominio(
          "La variante '" + varianteId + "' no pertenece al producto '" + nombre + "'.");
    }
    Variante medida = variantes.get(posicion).medida(paquete);
    variantes.set(posicion, medida);
    return medida;
  }

  public void asignarImagenPrincipal(ImagenProducto imagen) {
    if (imagen != null && imagen.tipo() != TipoImagen.PRINCIPAL) {
      throw new ImagenProductoInvalidaException("La imagen principal debe ser de tipo PRINCIPAL.");
    }
    this.imagenPrincipal = imagen;
  }

  /**
   * El orden que le toca a la siguiente imagen de la galería: uno más que el mayor que haya.
   *
   * <p><b>Uno más que el mayor, y no el tamaño de la lista</b>, porque quitar una imagen deja
   * huecos a propósito —{@code 0, 2, 3} se pinta igual que {@code 0, 1, 2}, la ficha ordena y no
   * cuenta—. Con el tamaño, borrar la última y subir otra repetiría un orden que ya existe.
   *
   * <p>Vive aquí y no en el caso de uso porque es la regla de cómo se ordena una galería, y el caso
   * de uso necesita el número <em>antes</em> de construir la {@link ImagenProducto}, que es
   * inmutable.
   */
  public int siguienteOrdenDeGaleria() {
    return galeria.stream().mapToInt(ImagenProducto::orden).max().orElse(-1) + 1;
  }

  /**
   * Suma una imagen a la galería. La principal va por {@link #asignarImagenPrincipal}: son dos
   * cosas distintas y la ficha las pinta en sitios distintos.
   *
   * <p>Rechaza subir dos veces el mismo archivo comparando el hash del contenido, que es justo para
   * lo que {@code docs/02} lo puso. Cuatro tomas que se suben una por una desde un formulario son
   * el sitio natural para repetir una sin darse cuenta, y una galería con la misma foto dos veces
   * no se ve como un error del sistema: se ve como descuido del que vende.
   */
  public void agregarImagenGaleria(ImagenProducto imagen) {
    Objects.requireNonNull(imagen, "La imagen no puede ser nula.");
    if (imagen.tipo() != TipoImagen.GALERIA) {
      throw new ImagenProductoInvalidaException(
          "Una imagen de la galería debe ser de tipo GALERIA, y esta es " + imagen.tipo() + ".");
    }
    if (galeria.size() >= TOPE_DE_GALERIA) {
      throw new GaleriaLlenaException(
          "La galería de '"
              + nombre
              + "' ya tiene el máximo de "
              + TOPE_DE_GALERIA
              + " imágenes. Quita una antes de agregar otra.");
    }
    boolean mismoContenido = galeria.stream().anyMatch(i -> i.hash().equals(imagen.hash()));
    if (mismoContenido) {
      throw new ImagenDeGaleriaDuplicadaException(
          "Esa misma imagen ya está en la galería de '" + nombre + "'.");
    }
    boolean ordenOcupado = galeria.stream().anyMatch(i -> i.orden() == imagen.orden());
    if (ordenOcupado) {
      throw new ImagenProductoInvalidaException(
          "La galería de '" + nombre + "' ya tiene una imagen en el orden " + imagen.orden() + ".");
    }
    galeria.add(imagen);
  }

  /**
   * Saca una imagen de la galería y la devuelve, porque quien llama necesita su URL para borrar el
   * objeto del bucket: sin eso quedaría pagando un archivo que ya nadie sirve.
   *
   * <p>No renumera las que quedan. Renumerar obligaría a reescribir filas que nadie tocó para
   * arreglar un problema que no existe — ver {@link #siguienteOrdenDeGaleria()}.
   */
  public ImagenProducto quitarImagenGaleria(UUID imagenId) {
    Objects.requireNonNull(imagenId, "El id de la imagen no puede ser nulo.");
    for (int i = 0; i < galeria.size(); i++) {
      if (galeria.get(i).id().equals(imagenId)) {
        return galeria.remove(i);
      }
    }
    throw new ImagenDeGaleriaNoEncontradaException(
        "La imagen '" + imagenId + "' no está en la galería de '" + nombre + "'.");
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

  /**
   * Lo saca de la vitrina y lo devuelve a {@code BORRADOR}. Sin invariante que lo impida: retirar
   * algo de la venta es precisamente lo que hay que poder hacer cuando resulta estar mal —una foto
   * que no era, un precio equivocado, un producto que el proveedor ya no tiene—, y una regla que lo
   * bloqueara obligaría a arreglarlo por la base.
   *
   * <p><b>No toca nada más, y eso es la decisión</b>: los pedidos ya creados siguen su curso
   * —llevan sus líneas congeladas y ningún paso posterior vuelve a mirar el estado del producto— y
   * las reservas de inventario se quedan donde están. Retirar de la vitrina no es cancelar lo
   * vendido.
   *
   * <p>Idempotente como {@link #publicar()}: despublicar un borrador deja un borrador.
   */
  public void despublicar() {
    this.estado = EstadoProducto.BORRADOR;
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
