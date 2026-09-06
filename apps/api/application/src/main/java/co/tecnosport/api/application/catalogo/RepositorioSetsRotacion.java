package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.SetRotacion;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto del set de rotación. Agregado propio, con su propio repositorio: su ciclo de vida lo
 * maneja el asistente de captura y no la edición del producto, igual que {@code Envio} frente a
 * {@code Pedido} (ADR-0013, ADR-0018). Implementación de producción: JPA con PostgreSQL.
 */
public interface RepositorioSetsRotacion {

  /** Inserta un set recién abierto, con los fotogramas que traiga (al abrir, ninguno). */
  void guardar(SetRotacion set);

  Optional<SetRotacion> buscarPorId(UUID id);

  /**
   * El set publicado de un producto, si tiene uno. Es el que la ficha pública muestra, y el que
   * hace que publicar un segundo set sobre el mismo producto tenga que rechazarse.
   */
  Optional<SetRotacion> buscarPublicadoDeProducto(UUID productoId);

  /**
   * Guarda el estado del set y los fotogramas que hayan llegado. A diferencia de {@link #guardar},
   * no es una inserción del set: es el paso de BORRADOR a COMPLETO o a PUBLICADO.
   */
  void actualizar(SetRotacion set);

  /**
   * Borra el set y sus fotogramas. Los objetos del bucket no se tocan — mismo criterio que al
   * reemplazar la imagen principal: el bucket tiene versionado (docs/07-infra-gcp.md).
   */
  void eliminar(UUID id);
}
