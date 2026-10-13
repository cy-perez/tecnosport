package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.Objects;
import java.util.UUID;

/**
 * Une dos borradores de la misma publicación en uno: el mismo diseño en otro color, que el álbum
 * dio aparte porque el proveedor le puso otro SKU ({@link BorradorProducto#absorber}). Es lo
 * contrario de {@link PartirBorrador}.
 *
 * <p>El camino lo decidió el negocio el 11 de octubre de 2026: un producto por SKU se queda (10 de
 * octubre), y quien revisa une a mano los que son el mismo, con la sugerencia del panel —«posible
 * mismo diseño»— a la vista. Unir solo, por la etiqueta del lector, se descartó: la misma etiqueta
 * juntaba unas veces y otras no los jeans que se parecen.
 *
 * <p>El otro borrador se elimina —su fila, nada más: la publicación y sus fotos siguen, ahora en
 * este—. La transacción la abre quien llama: escribe uno y borra el otro, los dos o ninguno.
 */
public final class UnirBorradores {

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;

  public UnirBorradores(
      RepositorioBorradores repositorioBorradores,
      RepositorioPublicacionesProveedor repositorioPublicaciones) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
  }

  /**
   * @param destinoId el que se queda
   * @param otroId el que se une a él y desaparece
   * @return el que se quedó, con las fotos y los tonos de los dos
   */
  public BorradorProducto ejecutar(UUID destinoId, UUID otroId) {
    Objects.requireNonNull(otroId, "Unir dice con cuál.");
    if (Objects.equals(destinoId, otroId)) {
      throw new ExcepcionDeDominio("Un borrador no se une consigo mismo.");
    }
    // Se bloquean en el orden de sus ids: dos pestañas que unan A con B y B con A a la vez se
    // esperan en vez de bloquearse una a la otra.
    boolean destinoPrimero = destinoId.compareTo(otroId) < 0;
    BorradorProducto primero = bloquear(destinoPrimero ? destinoId : otroId);
    BorradorProducto segundo = bloquear(destinoPrimero ? otroId : destinoId);
    BorradorProducto destino = destinoPrimero ? primero : segundo;
    BorradorProducto otro = destinoPrimero ? segundo : primero;
    if (destino.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(destino.estado());
    }
    if (otro.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(otro.estado());
    }
    if (!otro.fotosSubidas().isEmpty()) {
      throw new BorradorConFotosSubidasException();
    }
    if (!destino.tallasDeCadaTonoSeSaben(otro)) {
      throw new TallasDeCadaTonoDesconocidasException();
    }
    if (!destino.ajusteDeTalla().equals(otro.ajusteDeTalla())) {
      throw new AjustesDeTallaDistintosException();
    }
    PublicacionProveedor publicacion =
        repositorioPublicaciones
            .buscarPorId(destino.publicacionId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "El borrador apunta a una publicación que no existe."));
    destino.absorber(otro, publicacion.medios());
    repositorioBorradores.actualizar(destino);
    repositorioBorradores.eliminar(otroId);
    return destino;
  }

  private BorradorProducto bloquear(UUID id) {
    return repositorioBorradores
        .buscarPorIdParaActualizar(id)
        .orElseThrow(() -> new BorradorNoEncontradoException(id));
  }
}
