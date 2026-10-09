package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de marcas. Implementación de producción: JPA con PostgreSQL. */
public interface RepositorioMarcas {

  /**
   * Todas, incluidas las que todavía no tienen ni un producto. Es lo que necesita el panel: sin
   * esto no se podría crear el primer producto de una marca nueva, porque el formulario no la
   * ofrecería nunca.
   */
  List<Marca> listarTodas();

  /**
   * Solo las que tienen al menos un producto {@code PUBLICADO}, que es el mismo criterio con el que
   * la vitrina arma su rejilla.
   *
   * <p>Tiene que ser el mismo y no uno parecido: un filtro que ofrezca una marca cuya rejilla sale
   * vacía es una promesa rota en dos clics, y la vitrina filtra por {@code p.estado = 'PUBLICADO'}
   * sin mirar variantes. Si algún día la rejilla exige además variante activa, este criterio se
   * mueve con ella o vuelve el mismo defecto.
   *
   * <p>Cada una con las líneas en las que tiene algo así, para que el filtro pueda acotarse cuando
   * la URL trae una ({@link MarcaConLineas}).
   */
  List<MarcaConLineas> listarConProductosPublicados();

  Optional<Marca> buscarPorId(UUID id);

  /**
   * <b>Sin distinguir mayúsculas</b>, igual que el índice único de la base ({@code V56}, sobre
   * {@code lower(nombre)}).
   *
   * <p>Las dos comprobaciones tienen que existir y no sobra ninguna: esta es la que deja dar un
   * {@code 409} que explica qué pasó, y el índice es el que de verdad protege — entre este {@code
   * existe} y el {@code guardar} hay una ventana en la que otra petición puede colarse.
   */
  boolean existeConNombre(String nombre);

  void guardar(Marca marca);

  /**
   * Como {@link #existeConNombre}, sin contar a la marca {@code excepto}: renombrar "xiaomi" a
   * "Xiaomi" no choca consigo misma, y el índice de {@code V56} tampoco la dejaría chocar.
   */
  boolean existeOtraConNombre(String nombre, UUID excepto);

  /**
   * Cambia el nombre. Si el índice único lo rechaza —otra petición se coló entre la lectura y la
   * escritura—, {@link MarcaYaExisteException}.
   */
  void actualizar(Marca marca);

  /**
   * Los productos que cuelgan de la marca, en cualquier estado: también borradores y archivados.
   */
  long contarProductos(UUID marcaId);

  /**
   * Borra la marca. Si la llave de {@code producto.marca_id} lo impide —se cargó un producto entre
   * la cuenta y el borrado—, {@link MarcaConProductosException}.
   */
  void eliminar(Marca marca);
}
