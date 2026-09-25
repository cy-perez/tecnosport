package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto del catálogo. Implementación de producción: JPA con PostgreSQL. */
public interface RepositorioProductos {

  /**
   * Búsqueda paginada para la vitrina pública. La implementación debe filtrar por {@code estado ==
   * PUBLICADO} en la propia consulta: no se puede paginar correctamente trayendo todo y filtrando
   * después.
   */
  ResultadoPaginado<Producto> buscar(
      FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina);

  /**
   * Búsqueda simple por slug, sin filtrar por estado: devuelve el producto exista o no esté
   * publicado. La visibilidad pública la decide quien llama (ver {@code VerFichaDeProducto}), no
   * este puerto.
   */
  Optional<Producto> buscarPorSlug(Slug slug);

  /**
   * El producto dueño de una variante, para revalidar precio y existencia al crear un pedido
   * (docs/03-api.md): el cliente solo envía el id de la variante, nunca su precio. Sin filtrar por
   * estado, igual que {@link #buscarPorSlug}.
   */
  Optional<Producto> buscarPorVarianteId(UUID varianteId);

  /**
   * Listado del panel admin: todos los estados (a diferencia de {@link #buscar}, que solo trae
   * {@code PUBLICADO}), paginado por página y ordenado por más reciente primero.
   */
  ProductosPaginados buscarParaAdmin(int pagina, int tamanoPagina);

  /**
   * Inserta un producto nuevo. Sin variantes ni imágenes todavía — eso es de un caso de uso propio.
   */
  void guardar(Producto producto);

  /**
   * Búsqueda por id para el panel admin, sin filtrar por estado — igual criterio que {@link
   * #buscarPorSlug}, ve productos en cualquier estado.
   */
  Optional<Producto> buscarPorId(UUID id);

  /** Actualiza un producto existente. A diferencia de {@link #guardar}, no es una inserción. */
  void actualizar(Producto producto);

  /**
   * Borra el producto y todo lo que cuelga de él: sus variantes con sus atributos y su inventario,
   * sus imágenes y sus sets de rotación. Quien llama ya comprobó que se puede borrar — ver {@code
   * EliminarProducto}, que es el único sitio donde esa decisión está escrita.
   *
   * <p><b>En cascada explícita y no confiando en la base</b>, porque la base no la tiene: las
   * foráneas de {@code variante}, {@code inventario} e {@code imagen_producto} se declararon sin
   * {@code on delete cascade} a propósito (V1 y V2), que es lo que hasta hoy impedía que un borrado
   * accidental se llevara medio catálogo por delante. La cascada vive aquí, escrita, donde se puede
   * leer.
   *
   * <p>Lo que <b>no</b> se toca es {@code linea_carrito}, aunque apunte a las variantes borradas:
   * no tiene foránea justo porque agregar al carrito no valida que la variante exista (V3), y el
   * checkout ya sabe rechazar una línea que no se puede reservar.
   */
  void eliminar(UUID productoId);

  /**
   * Persiste una variante nueva (con sus atributos) para un producto ya existente. Quien llama debe
   * haber validado antes que el producto existe y que el SKU no está en uso.
   */
  void agregarVariante(UUID productoId, Variante variante);

  /**
   * El SKU es único en todo el catálogo, no solo dentro del producto que se está armando en memoria
   * — {@code Producto.agregarVariante} no puede ver esto por sí solo.
   */
  boolean existeVarianteConSku(Sku sku);

  /**
   * Todas las variantes {@code ACTIVA} del catálogo con la medida de su paquete, que puede faltar.
   * Sin paginar, mismo criterio que {@link #variantesActivas()}: el panel necesita la lista
   * completa para medir y para corregir una medida ya tomada.
   *
   * <p>Fue {@code variantesSinMedir()}, con el filtro en el {@code where}. Quién está sin medir lo
   * decide ahora {@code ListarVariantesSinMedir}, que es una clase con pruebas.
   */
  List<MedidaDeVariante> medidasDeVariantes();

  /**
   * Graba el paquete de una variante existente, la tuviera o no. El puerto no opina sobre cuál de
   * los dos casos es: la diferencia entre medir por primera vez y corregir una medida equivocada la
   * decide {@code MedirVariante}, que es quien la puede explicar.
   */
  void actualizarPaquete(UUID varianteId, Paquete paquete);

  /**
   * Las variantes {@code ACTIVA} de todo el catálogo, sin paginar — la lista que el panel necesita
   * para contar (adr/0049). Los saldos no salen de aquí: los pone el libro de movimientos
   * (adr/0050).
   */
  List<VarianteActiva> variantesActivas();

  /**
   * Reemplaza la imagen principal del producto (a lo sumo una por producto, constraint única en BD)
   * — si había una anterior, esta la sustituye.
   */
  void guardarImagenPrincipal(UUID productoId, ImagenProducto imagen);

  /**
   * Suma una imagen a la galería del producto. Inserta siempre: a diferencia de la principal, aquí
   * no hay nada que reemplazar — quien decide cuántas caben y en qué orden es el agregado.
   */
  void guardarImagenDeGaleria(UUID productoId, ImagenProducto imagen);

  /**
   * Borra la fila de una imagen de la galería. Recibe también el producto porque es la única forma
   * de que un id de imagen suelto no pueda borrar la imagen de otro producto.
   *
   * @return {@code true} si borró una fila; {@code false} si no había ninguna que borrar, que es lo
   *     que pasa cuando dos peticiones quitan la misma imagen a la vez o cuando el agregado que
   *     tiene quien llama ya está obsoleto. Era {@code void}, y el adaptador descartaba el conteo
   *     que su propio javadoc decía que servía justo para distinguir esos dos casos.
   */
  boolean eliminarImagenDeGaleria(UUID productoId, UUID imagenId);

  /**
   * Graba el orden que el agregado acaba de decidir para la galería entera.
   *
   * <p>La galería completa y no una imagen suelta, porque reordenar no es cambiar una fila: es
   * dejar el conjunto como quedó. Quien llama ya renumeró de 0 a n-1 —{@code
   * Producto.reordenarGaleria}—, así que aquí no se calcula nada: se escribe.
   */
  void guardarOrdenDeGaleria(UUID productoId, List<ImagenProducto> galeria);
}
