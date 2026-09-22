package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.RepositorioSetsRotacion;
import co.tecnosport.api.domain.catalogo.EstadoSetRotacion;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.SetRotacionJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteImagenJpaEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final VarianteImagenJpaRepository varianteImagenJpaRepository;
  private final MapeadorCatalogo mapeador;

  public RepositorioSetsRotacionJpa(
      SetRotacionJpaRepository setRotacionJpaRepository,
      ImagenProductoJpaRepository imagenProductoJpaRepository,
      VarianteImagenJpaRepository varianteImagenJpaRepository,
      MapeadorCatalogo mapeador) {
    this.setRotacionJpaRepository = setRotacionJpaRepository;
    this.imagenProductoJpaRepository = imagenProductoJpaRepository;
    this.varianteImagenJpaRepository = varianteImagenJpaRepository;
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
      guardarVariantes(set);
    }
  }

  /**
   * Las variantes de los fotogramas, reusando el id de la fila que ya exista para ese ancho.
   *
   * <p>Aquí no se puede insertar y ya, como en las imágenes de producto: este método vuelve a
   * guardar fotogramas que ya estaban, y una fila nueva para el mismo ancho chocaría contra el
   * único de la V60. Reusar el id convierte el segundo guardado en un `update` con los mismos
   * valores, que es lo que el método dice que hace.
   */
  private void guardarVariantes(SetRotacion set) {
    record Clave(UUID imagen, int ancho) {}

    List<UUID> imagenIds = set.fotogramas().stream().map(ImagenProducto::id).toList();
    Map<Clave, UUID> existentes =
        varianteImagenJpaRepository.findByImagenIdIn(imagenIds).stream()
            .collect(
                Collectors.toMap(
                    v -> new Clave(v.getImagenId(), v.getAncho()), VarianteImagenJpaEntity::getId));

    List<VarianteImagenJpaEntity> filas = new ArrayList<>();
    for (ImagenProducto fotograma : set.fotogramas()) {
      for (VarianteDeImagen variante : fotograma.variantes()) {
        UUID id =
            existentes.getOrDefault(
                new Clave(fotograma.id(), variante.ancho()), GeneradorIdentificador.nuevo());
        filas.add(
            new VarianteImagenJpaEntity(
                id, fotograma.id(), variante.ancho(), variante.url(), variante.bytes()));
      }
    }
    varianteImagenJpaRepository.saveAll(filas);
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
        fotograma.urlVistaPrevia().orElse(null),
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
