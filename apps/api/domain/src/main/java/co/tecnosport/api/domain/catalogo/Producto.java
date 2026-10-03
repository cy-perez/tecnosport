package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
  private final OrigenProducto origen;
  private final UUID proveedorId;
  private Dinero precioProveedor;
  private HuellaProveedor huellaProveedor;
  private Instant vistoPorUltimaVez;
  private EstadoDisponibilidad estadoDisponibilidad;
  private String tallaSirveHasta;

  /** Un producto creado a mano o reconstruido sin datos de proveedor: origen {@code MANUAL}. */
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
    this(
        id,
        nombre,
        slug,
        descripcion,
        marca,
        categoria,
        estado,
        imagenPrincipal,
        galeria,
        setRotacion,
        variantes,
        OrigenProducto.MANUAL,
        null,
        null,
        null,
        null,
        EstadoDisponibilidad.DISPONIBLE);
  }

  /**
   * Reconstrucción completa, con lo que un producto de proveedor lleva además: de quién viene, a
   * cuánto lo vende el proveedor, su huella, cuándo se vio por última vez en sus mensajes y si
   * sigue disponible. Un {@code PROVEEDOR} trae proveedor, huella y última vista; un {@code MANUAL}
   * no trae nada de eso y el job de disponibilidad no lo toca.
   */
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
      List<Variante> variantes,
      OrigenProducto origen,
      UUID proveedorId,
      Dinero precioProveedor,
      HuellaProveedor huellaProveedor,
      Instant vistoPorUltimaVez,
      EstadoDisponibilidad estadoDisponibilidad) {
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
    this.origen = Objects.requireNonNull(origen, "El origen del producto no puede ser nulo.");
    this.estadoDisponibilidad =
        Objects.requireNonNull(estadoDisponibilidad, "La disponibilidad no puede ser nula.");
    if (origen == OrigenProducto.PROVEEDOR) {
      this.proveedorId =
          Objects.requireNonNull(proveedorId, "Un producto de proveedor dice de cuál viene.");
      this.huellaProveedor =
          Objects.requireNonNull(huellaProveedor, "Un producto de proveedor lleva huella.");
      this.vistoPorUltimaVez =
          Objects.requireNonNull(
              vistoPorUltimaVez, "Un producto de proveedor sabe cuándo se vio por última vez.");
      this.precioProveedor = precioProveedor;
    } else {
      if (proveedorId != null || huellaProveedor != null || precioProveedor != null) {
        throw new ExcepcionDeDominio("Un producto manual no lleva datos de proveedor.");
      }
      this.proveedorId = null;
      this.huellaProveedor = null;
      this.vistoPorUltimaVez = null;
      this.precioProveedor = null;
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
  /**
   * Un producto que viene de un proveedor. Nace en {@code BORRADOR} como cualquier otro —quien
   * aprueba lo publica en el mismo paso— y {@code DISPONIBLE}, visto por última vez en la fecha del
   * mensaje que lo trajo, no en la de la aprobación.
   */
  public static Producto crearDeProveedor(
      String nombre,
      Slug slug,
      String descripcion,
      Marca marca,
      Categoria categoria,
      UUID proveedorId,
      Dinero precioProveedor,
      HuellaProveedor huellaProveedor,
      Instant vistoPorUltimaVez) {
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
        List.of(),
        OrigenProducto.PROVEEDOR,
        proveedorId,
        precioProveedor,
        huellaProveedor,
        vistoPorUltimaVez,
        EstadoDisponibilidad.DISPONIBLE);
  }

  /**
   * El proveedor lo volvió a anunciar: se anota la fecha y, si estaba oculto por vencimiento o
   * agotado, vuelve a estar disponible. Solo para lo que viene de un proveedor; un manual no tiene
   * mensajes que lo renueven.
   */
  public void renovar(Instant vistoEn) {
    exigirDeProveedor("renovar");
    Objects.requireNonNull(vistoEn, "La fecha de la renovación no puede ser nula.");
    // Un aviso más viejo que el último visto no manda: dos lotes procesados fuera de orden no
    // pueden convertir un "agotado" de ayer en un "disponible" de anteayer.
    if (vistoEn.isBefore(vistoPorUltimaVez)) {
      return;
    }
    this.vistoPorUltimaVez = vistoEn;
    this.estadoDisponibilidad = EstadoDisponibilidad.DISPONIBLE;
  }

  /**
   * El proveedor cambió el precio. Se guarda el nuevo y <b>la huella se recalcula con él</b>: la
   * huella lleva el precio dentro, y si se quedara con el viejo, el siguiente anuncio del mismo
   * producto al precio nuevo ya no la encontraría y acabaría en un producto duplicado. El margen lo
   * revisa una persona.
   */
  public void actualizarPrecioProveedor(Dinero precio) {
    exigirDeProveedor("cambiar el precio de proveedor de");
    this.precioProveedor = Objects.requireNonNull(precio, "El precio no puede ser nulo.");
    this.huellaProveedor = HuellaProveedor.calcular(proveedorId, nombre, precio);
  }

  /**
   * Lleva demasiado sin aparecer en los mensajes. Se oculta, no se borra: los enlaces siguen
   * respondiendo y el siguiente mensaje que lo traiga lo reactiva. Idempotente.
   */
  public void ocultarPorVencimiento() {
    exigirDeProveedor("ocultar por vencimiento");
    if (estadoDisponibilidad == EstadoDisponibilidad.DISPONIBLE) {
      this.estadoDisponibilidad = EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO;
    }
  }

  /** El proveedor dijo que se acabó. De inmediato, y sin esperar ninguna ventana. */
  public void marcarAgotadoPorProveedor(Instant vistoEn) {
    exigirDeProveedor("marcar como agotado");
    Objects.requireNonNull(vistoEn, "La fecha del aviso no puede ser nula.");
    if (vistoEn.isBefore(vistoPorUltimaVez)) {
      return;
    }
    this.vistoPorUltimaVez = vistoEn;
    this.estadoDisponibilidad = EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR;
  }

  /** Lo que el catálogo público muestra: publicado y disponible, las dos cosas. */
  public boolean estaEnVitrina() {
    return estado == EstadoProducto.PUBLICADO
        && estadoDisponibilidad == EstadoDisponibilidad.DISPONIBLE;
  }

  public boolean esDeProveedor() {
    return origen == OrigenProducto.PROVEEDOR;
  }

  private void exigirDeProveedor(String accion) {
    if (origen != OrigenProducto.PROVEEDOR) {
      throw new ExcepcionDeDominio(
          "Solo se puede "
              + accion
              + " un producto que viene de un proveedor; '"
              + nombre
              + "' es manual.");
    }
  }

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
   * Falla si la galería ya está llena.
   *
   * <p>Es público porque quien pide una URL firmada necesita preguntarlo <b>antes</b> de que el
   * navegador suba veinte megas a un bucket donde ese objeto se quedaría sin que nadie lo reclame.
   * No es una invariante duplicada: {@link #agregarImagenGaleria} lo vuelve a comprobar, que es
   * quien de verdad protege. Lo que se evita aquí es el trabajo tirado, y por eso el mensaje vive
   * en un solo sitio.
   */
  public void verificarQueCabeOtraImagenEnLaGaleria() {
    if (galeria.size() >= TOPE_DE_GALERIA) {
      throw new GaleriaLlenaException(
          "La galería de '"
              + nombre
              + "' ya tiene el máximo de "
              + TOPE_DE_GALERIA
              + " imágenes. Quita una antes de agregar otra.");
    }
  }

  /**
   * Suma una imagen a la galería. La principal va por {@link #asignarImagenPrincipal}: son dos
   * cosas distintas y la ficha las pinta en sitios distintos.
   *
   * <p>Rechaza subir dos veces el mismo archivo comparando el hash del contenido, que es justo para
   * lo que {@code docs/02} lo puso. Cuatro tomas que se suben una por una desde un formulario son
   * el sitio natural para repetir una sin darse cuenta, y una galería con la misma foto dos veces
   * no se ve como un error del sistema: se ve como descuido del que vende.
   *
   * <p>Ese rechazo no basta por sí solo: el hash lo calcula el cliente. Que dos imágenes no apunten
   * al mismo objeto del bucket lo comprueba {@code AgregarImagenDeGaleria}, que es quien conoce las
   * keys.
   */
  public void agregarImagenGaleria(ImagenProducto imagen) {
    Objects.requireNonNull(imagen, "La imagen no puede ser nula.");
    if (imagen.tipo() != TipoImagen.GALERIA) {
      throw new ImagenProductoInvalidaException(
          "Una imagen de la galería debe ser de tipo GALERIA, y esta es " + imagen.tipo() + ".");
    }
    verificarQueCabeOtraImagenEnLaGaleria();
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

  /**
   * Deja la galería en el orden pedido, renumerando de 0 a n-1.
   *
   * <p><b>La lista tiene que ser exactamente la galería</b>: los mismos ids, ni uno más ni uno
   * menos y ninguno repetido. Se podría haber aceptado una lista parcial —"sube esta"— y completar
   * con el resto, y sería peor: dos pantallas abiertas sobre el mismo producto mandarían órdenes
   * parciales que se pisan y el resultado dependería de cuál llegó antes. Con la lista entera, la
   * segunda en llegar manda sobre una galería que ya no es la que vio, y eso se nota aquí en vez de
   * quedar en un orden que nadie pidió.
   *
   * <p><b>Renumera de cero y de corrido</b>, que es lo único que distingue este método de los otros
   * dos: agregar y quitar dejan huecos a propósito —{@link #siguienteOrdenDeGaleria()} explica por
   * qué—, y reordenar es justo el momento en que reescribir todas las filas deja de ser trabajo
   * tirado, porque ya se están reescribiendo. Los huecos se cierran de paso; no se ve nada distinto
   * en la ficha, que ordena y no cuenta.
   *
   * <p>Pedir el orden que la galería ya tiene no falla: vuelve a dejar lo mismo. Un botón que se
   * pulsa dos veces no es un error.
   */
  public void reordenarGaleria(List<UUID> ordenDeseado) {
    Objects.requireNonNull(ordenDeseado, "El orden pedido no puede ser nulo.");
    Set<UUID> pedidos = new LinkedHashSet<>(ordenDeseado);
    if (pedidos.size() != ordenDeseado.size()) {
      throw new ImagenProductoInvalidaException(
          "El orden pedido para la galería de '" + nombre + "' repite alguna imagen.");
    }
    Map<UUID, ImagenProducto> porId = new LinkedHashMap<>();
    for (ImagenProducto imagen : galeria) {
      porId.put(imagen.id(), imagen);
    }
    // `ImagenProductoInvalidaException` y no `ImagenDeGaleriaNoEncontradaException`, que da 404:
    // aquí el recurso del PUT —la galería del producto— sí existe, y lo que pasa es que la lista
    // que mandaron ya no lo describe. Es la carrera de las dos pestañas que adr/0053 resuelve con
    // un 422 "y no se graba nada", y el 404 la partía en dos respuestas distintas según si a la
    // lista le faltaba o le sobraba una imagen. El 404 sigue siendo el correcto al quitar una,
    // donde el subrecurso del DELETE de verdad no existe.
    for (UUID id : pedidos) {
      if (!porId.containsKey(id)) {
        throw new ImagenProductoInvalidaException(
            "El orden pedido para la galería de '"
                + nombre
                + "' nombra la imagen '"
                + id
                + "', que ya no está: la galería cambió mientras tanto.");
      }
    }
    if (pedidos.size() != porId.size()) {
      throw new ImagenProductoInvalidaException(
          "El orden pedido para la galería de '"
              + nombre
              + "' nombra "
              + pedidos.size()
              + " imágenes y la galería tiene "
              + porId.size()
              + ". Tienen que ser todas.");
    }

    List<ImagenProducto> reordenada = new ArrayList<>(pedidos.size());
    int orden = 0;
    for (UUID id : pedidos) {
      reordenada.add(porId.get(id).conOrden(orden++));
    }
    galeria.clear();
    galeria.addAll(reordenada);
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

  public OrigenProducto origen() {
    return origen;
  }

  public Optional<UUID> proveedorId() {
    return Optional.ofNullable(proveedorId);
  }

  public Optional<Dinero> precioProveedor() {
    return Optional.ofNullable(precioProveedor);
  }

  public Optional<HuellaProveedor> huellaProveedor() {
    return Optional.ofNullable(huellaProveedor);
  }

  public Optional<Instant> vistoPorUltimaVez() {
    return Optional.ofNullable(vistoPorUltimaVez);
  }

  public EstadoDisponibilidad estadoDisponibilidad() {
    return estadoDisponibilidad;
  }

  /**
   * Hasta qué talla le sirve una prenda de talla única, si el proveedor lo dijo: «sirve hasta la
   * L». Lo dicen la tarjeta y la ficha al lado de «Talla única», porque con la marca, el nombre y
   * el precio quien compra no sabe de qué talla es.
   */
  public Optional<String> tallaSirveHasta() {
    return Optional.ofNullable(tallaSirveHasta);
  }

  /**
   * @param talla la talla límite, o nulo o en blanco para quitarla
   */
  public void definirTallaSirveHasta(String talla) {
    String limpia = talla == null || talla.isBlank() ? null : talla.strip();
    if (limpia != null && limpia.length() > 20) {
      throw new ExcepcionDeDominio(
          "«Sirve hasta» es una talla, no una frase: hasta 20 caracteres.");
    }
    this.tallaSirveHasta = limpia;
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
