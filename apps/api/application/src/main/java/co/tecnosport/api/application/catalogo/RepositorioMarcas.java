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
   */
  List<Marca> listarConProductosPublicados();

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
}
