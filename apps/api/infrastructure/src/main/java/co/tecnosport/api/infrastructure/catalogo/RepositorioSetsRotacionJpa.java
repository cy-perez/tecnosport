package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.RepositorioSetsRotacion;
import co.tecnosport.api.domain.catalogo.EstadoSetRotacion;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.SetRotacionJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto {@link RepositorioSetsRotacion}. El set vive en {@code set_rotacion} y sus
 * fotogramas en {@code imagen_producto} con {@code set_rotacion_id}, que es la misma tabla de la
 * imagen principal y la galería (docs/02-modelo-datos.md).
 *
 * <p>Solo maneja sets a nivel de producto: {@code variante_id} queda en nulo. El set por variante
 * lo contempla el esquema desde V1, pero todavía no hay caso de uso que lo escriba.
 */
@Repository
public class RepositorioSetsRotacionJpa implements RepositorioSetsRotacion {

  private final SetRotacionJpaRepository setRotacionJpaRepository;
  private final ImagenProductoJpaRepository imagenProductoJpaRepository;
  private final MapeadorCatalogo mapeador;

  public RepositorioSetsRotacionJpa(
      SetRotacionJpaRepository setRotacionJpaRepository,
      ImagenProductoJpaRepository imagenProductoJpaRepository,
      MapeadorCatalogo mapeador) {
    this.setRotacionJpaRepository = setRotacionJpaRepository;
    this.imagenProductoJpaRepository = imagenProductoJpaRepository;
    this.mapeador = mapeador;
  }

  @Override
  public void guardar(SetRotacion set) {
    setRotacionJpaRepository.save(aEntidad(set));
    guardarFotogramas(set);
  }

  @Override
  public Optional<SetRotacion> buscarPorId(UUID id) {
    return setRotacionJpaRepository.findById(id).map(this::aDominio);
  }

  @Override
  public Optional<SetRotacion> buscarPublicadoDeProducto(UUID productoId) {
    return setRotacionJpaRepository
        .findByProductoIdAndVarianteIdIsNullAndEstado(
            productoId, EstadoSetRotacion.PUBLICADO.name())
        .map(this::aDominio);
  }

  @Override
  public void actualizar(SetRotacion set) {
    setRotacionJpaRepository.save(aEntidad(set));
    guardarFotogramas(set);
  }

  @Override
  @Transactional
  public void eliminar(UUID id) {
    imagenProductoJpaRepository.deleteBySetRotacionId(id);
    setRotacionJpaRepository.deleteById(id);
  }

  /**
   * Los fotogramas se guardan por id: los que ya estaban se sobreescriben con lo mismo y los nuevos
   * se insertan. Nunca se borra ninguno desde aquí — un set solo recibe fotogramas mientras está en
   * BORRADOR, y de ahí solo sale hacia adelante.
   */
  private void guardarFotogramas(SetRotacion set) {
    List<ImagenProductoJpaEntity> entidades =
        set.fotogramas().stream().map(fotograma -> aEntidad(set, fotograma)).toList();
    if (!entidades.isEmpty()) {
      imagenProductoJpaRepository.saveAll(entidades);
    }
  }

  private SetRotacionJpaEntity aEntidad(SetRotacion set) {
    return new SetRotacionJpaEntity(
        set.id(),
        set.productoId(),
        null,
        set.fotogramasPrometidos(),
        set.estado().name(),
        set.capturadoPor(),
        set.capturadoEn(),
        set.dispositivo(),
        set.versionAsistente());
  }

  private ImagenProductoJpaEntity aEntidad(SetRotacion set, ImagenProducto fotograma) {
    return new ImagenProductoJpaEntity(
        fotograma.id(),
        set.productoId(),
        null,
        set.id(),
        fotograma.tipo().name(),
        fotograma.orden(),
        fotograma.url(),
        fotograma.urlWebp(),
        fotograma.ancho(),
        fotograma.alto(),
        fotograma.bytes(),
        fotograma.hash().valor(),
        fotograma.altEs(),
        fotograma.altEn(),
        Instant.now());
  }

  private SetRotacion aDominio(SetRotacionJpaEntity entidad) {
    return mapeador.aSetRotacion(
        entidad, imagenProductoJpaRepository.findBySetRotacionId(entidad.getId()));
  }
}
