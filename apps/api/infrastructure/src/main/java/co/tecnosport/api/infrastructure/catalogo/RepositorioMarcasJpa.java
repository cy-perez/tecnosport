package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.MarcaYaExisteException;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

  @Override
  public List<Marca> listarConProductosPublicados() {
    return marcaJpaRepository.findConProductosEnEstado(EstadoProducto.PUBLICADO.name()).stream()
        .map(this::aMarca)
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

  private Marca aMarca(MarcaJpaEntity m) {
    return new Marca(m.getId(), m.getNombre());
  }
}
