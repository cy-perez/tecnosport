package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
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
 */
public final class ArmarPublicaciones {

  private final RepositorioLotesIngesta repositorioLotes;
  private final RepositorioMensajesProveedor repositorioMensajes;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final AgrupadorDePublicaciones agrupador;

  public ArmarPublicaciones(
      RepositorioLotesIngesta repositorioLotes,
      RepositorioMensajesProveedor repositorioMensajes,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      AgrupadorDePublicaciones agrupador) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.repositorioMensajes = Objects.requireNonNull(repositorioMensajes);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.agrupador = Objects.requireNonNull(agrupador);
  }

  public AgrupadorDePublicaciones.Resultado ejecutar(UUID loteId) {
    LoteIngesta lote =
        repositorioLotes
            .buscarPorId(loteId)
            .orElseThrow(() -> new LoteNoEncontradoException(loteId));
    List<MensajeProveedor> mensajes = repositorioMensajes.listarDeLote(lote.id());
    AgrupadorDePublicaciones.Resultado resultado = agrupador.agrupar(mensajes);
    repositorioPublicaciones.guardarTodas(resultado.publicaciones());
    return resultado;
  }
}
