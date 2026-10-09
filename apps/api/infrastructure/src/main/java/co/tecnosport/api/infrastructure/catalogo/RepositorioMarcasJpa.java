package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.MarcaConLineas;
import co.tecnosport.api.application.catalogo.MarcaConProductosException;
import co.tecnosport.api.application.catalogo.MarcaNoEncontradaException;
import co.tecnosport.api.application.catalogo.MarcaYaExisteException;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.domain.catalogo.EstadoDisponibilidad;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class RepositorioMarcasJpa implements RepositorioMarcas {

  private final MarcaJpaRepository marcaJpaRepository;

  public RepositorioMarcasJpa(MarcaJpaRepository marcaJpaRepository) {
    this.marcaJpaRepository = Objects.requireNonNull(marcaJpaRepository);
  }

  @Override
  public List<Marca> listarTodas() {
    return marcaJpaRepository.findAll(Sort.by("nombre")).stream().map(this::aMarca).toList();
  }

  /**
   * Las filas vienen una por marca y línea, ordenadas por nombre, y se pliegan aquí a una entrada
   * por marca.
   *
   * <p>{@code LinkedHashMap} y {@code LinkedHashSet} y no los normales: el orden por nombre lo pone
   * la consulta y plegarlo con un {@code HashMap} lo tiraría — el desplegable de marcas del filtro
   * quedaría en el orden arbitrario del hash. Y una marca sin ninguna línea no puede salir de aquí:
   * si tiene fila, tiene producto publicado, y ese producto cuelga de una categoría que tiene
   * línea.
   */
  @Override
  public List<MarcaConLineas> listarConProductosPublicados() {
    Map<UUID, String> nombrePorId = new LinkedHashMap<>();
    Map<UUID, Set<LineaCatalogo>> lineasPorId = new LinkedHashMap<>();

    for (MarcaJpaRepository.MarcaYLinea fila :
        marcaJpaRepository.findLineasConProductosEnEstado(
            EstadoProducto.PUBLICADO.name(), EstadoDisponibilidad.DISPONIBLE.name())) {
      nombrePorId.putIfAbsent(fila.getMarcaId(), fila.getNombre());
      lineasPorId
          .computeIfAbsent(fila.getMarcaId(), id -> new LinkedHashSet<>())
          .add(LineaCatalogo.valueOf(fila.getLinea()));
    }

    return nombrePorId.entrySet().stream()
        .map(
            entrada ->
                new MarcaConLineas(
                    new Marca(entrada.getKey(), entrada.getValue()),
                    lineasPorId.get(entrada.getKey())))
        .toList();
  }

  @Override
  public Optional<Marca> buscarPorId(UUID id) {
    return marcaJpaRepository.findById(id).map(this::aMarca);
  }

  @Override
  public boolean existeConNombre(String nombre) {
    return marcaJpaRepository.existsByNombreIgnoreCase(nombre);
  }

  /**
   * {@code creadoEn} lo pone la infraestructura y no el dominio: {@link Marca} no tiene fecha
   * porque ninguna regla de negocio la mira. Es la misma columna de auditoría que {@code
   * SembradorCatalogo} rellena con {@code Instant.now()}.
   *
   * <p>{@code saveAndFlush} y no {@code save}, y no es un detalle de estilo: con {@code save} el
   * {@code INSERT} se queda pendiente hasta que Hibernate vuelca al confirmar la transacción, que
   * es <b>fuera</b> de este {@code try}. El {@code catch} de abajo no atraparía nada y la violación
   * saldría del módulo como {@code 500}, que es justo lo que {@code apps/api/CLAUDE.md} prohíbe.
   *
   * <p>La traducción es la del índice único de {@code emision_de_guia} ({@code
   * RepositorioEmisionesJpa}): la lectura previa de {@code CrearMarca} atrapa el caso normal, y
   * esto atrapa lo que ella no puede ver — dos peticiones que leen "no existe" a la vez y la base
   * deja entrar una sola. No se vuelve a consultar para averiguar cuál ganó: la sesión ya está rota
   * después de un flush fallido, y tampoco importa cuál entró.
   *
   * <p>Aquí atribuir la violación al nombre es seguro, cosa que en {@code emision_de_guia} no lo
   * era: {@code marca} solo tiene dos restricciones, la llave primaria y este único. Y la llave es
   * un UUID v7 recién generado por el dominio.
   */
  @Override
  public void guardar(Marca marca) {
    try {
      marcaJpaRepository.saveAndFlush(
          new MarcaJpaEntity(marca.id(), marca.nombre(), Instant.now()));
    } catch (DataIntegrityViolationException e) {
      throw new MarcaYaExisteException(marca.nombre());
    }
  }

  @Override
  public boolean existeOtraConNombre(String nombre, UUID excepto) {
    return marcaJpaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, excepto);
  }

  /** {@code saveAndFlush} dentro del {@code try} por lo mismo que en {@link #guardar}. */
  @Override
  public void actualizar(Marca marca) {
    MarcaJpaEntity fila =
        marcaJpaRepository
            .findById(marca.id())
            .orElseThrow(() -> new MarcaNoEncontradaException(marca.id()));
    fila.renombrar(marca.nombre());
    try {
      marcaJpaRepository.saveAndFlush(fila);
    } catch (DataIntegrityViolationException e) {
      throw new MarcaYaExisteException(marca.nombre());
    }
  }

  @Override
  public long contarProductos(UUID marcaId) {
    return marcaJpaRepository.contarProductos(marcaId);
  }

  /**
   * Con {@code flush} dentro del {@code try}: la única restricción que puede saltar al borrar es la
   * llave de {@code producto.marca_id}, y sin volcar aquí saltaría al confirmar, fuera del {@code
   * catch}, como {@code 500}.
   */
  @Override
  public void eliminar(Marca marca) {
    try {
      marcaJpaRepository.deleteById(marca.id());
      marcaJpaRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new MarcaConProductosException(marca.nombre());
    }
  }

  private Marca aMarca(MarcaJpaEntity m) {
    return new Marca(m.getId(), m.getNombre());
  }
}
