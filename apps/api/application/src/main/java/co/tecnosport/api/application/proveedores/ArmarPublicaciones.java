package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * De los mensajes de un lote, las publicaciones. La regla vive en {@link AgrupadorDePublicaciones};
 * aquí solo se leen los mensajes del lote y se guardan las publicaciones que salieron.
 *
 * <p>Solo los mensajes <em>de este lote</em>: los que ya estaban registrados de un lote anterior no
 * vuelven a agruparse, que es lo que hace que subir dos veces la misma exportación no cree
 * publicaciones nuevas.
 *
 * <p>El proveedor del lote se lee para pasarle al agrupador su {@link OrdenDePublicacion}: es lo
 * que decide de quién es una foto que empata entre dos precios.
 */
public final class ArmarPublicaciones {

  private final RepositorioLotesIngesta repositorioLotes;
  private final RepositorioProveedores repositorioProveedores;
  private final RepositorioMensajesProveedor repositorioMensajes;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final AgrupadorDePublicaciones agrupador;

  public ArmarPublicaciones(
      RepositorioLotesIngesta repositorioLotes,
      RepositorioProveedores repositorioProveedores,
      RepositorioMensajesProveedor repositorioMensajes,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      AgrupadorDePublicaciones agrupador) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
    this.repositorioMensajes = Objects.requireNonNull(repositorioMensajes);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.agrupador = Objects.requireNonNull(agrupador);
  }

  public AgrupadorDePublicaciones.Resultado ejecutar(UUID loteId) {
    LoteIngesta lote =
        repositorioLotes
            .buscarPorId(loteId)
            .orElseThrow(() -> new LoteNoEncontradoException(loteId));
    Proveedor proveedor =
        repositorioProveedores
            .buscarPorId(lote.proveedorId())
            .orElseThrow(() -> new ProveedorNoEncontradoException(lote.proveedorId()));
    OrdenDePublicacion orden = proveedor.ordenDePublicacion();
    List<MensajeProveedor> mensajes = repositorioMensajes.listarDeLote(lote.id());
    AgrupadorDePublicaciones.Resultado resultado = agrupador.agrupar(mensajes, orden);
    repositorioPublicaciones.guardarTodas(resultado.publicaciones());
    return resultado;
  }
}
