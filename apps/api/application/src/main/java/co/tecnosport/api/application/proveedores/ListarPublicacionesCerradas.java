package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.CierreDePublicacion;
import co.tecnosport.api.domain.proveedores.EstadoPublicacionProveedor;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.MotivoDePublicacion;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Las publicaciones de un lote que no dejaron borrador —descartadas y fallidas— con su motivo, en
 * el orden del chat. Es lo que el resumen cuenta y hasta el 11 de octubre de 2026 no se podía
 * mirar: el panel decía «23 descartadas» y la razón estaba en una columna que solo leía una
 * consulta a mano.
 *
 * <p>Sin paginar: un lote trae decenas de publicaciones, no miles, y las cerradas son una parte.
 */
public final class ListarPublicacionesCerradas {

  /** Lo que cabe en una fila del panel sin volverla un párrafo. */
  static final int LARGO_DEL_EXTRACTO = 160;

  private final RepositorioLotesIngesta repositorioLotes;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final RepositorioMensajesProveedor repositorioMensajes;

  public ListarPublicacionesCerradas(
      RepositorioLotesIngesta repositorioLotes,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      RepositorioMensajesProveedor repositorioMensajes) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.repositorioMensajes = Objects.requireNonNull(repositorioMensajes);
  }

  /**
   * @throws LoteNoEncontradoException si el lote no existe
   */
  public List<PublicacionCerrada> ejecutar(UUID loteId) {
    Objects.requireNonNull(loteId, "El id no puede ser nulo.");
    if (repositorioLotes.buscarPorId(loteId).isEmpty()) {
      throw new LoteNoEncontradoException(loteId);
    }
    List<PublicacionProveedor> cerradas =
        repositorioPublicaciones.listarDeLote(loteId).stream()
            .filter(
                p ->
                    p.estado() == EstadoPublicacionProveedor.DESCARTADA
                        || p.estado() == EstadoPublicacionProveedor.ERROR)
            .sorted(Comparator.comparing(PublicacionProveedor::fecha))
            .toList();
    if (cerradas.isEmpty()) {
      return List.of();
    }
    Map<UUID, MensajeProveedor> mensajes =
        repositorioMensajes.listarDeLote(loteId).stream()
            .collect(Collectors.toMap(MensajeProveedor::id, Function.identity()));
    return cerradas.stream()
        .map(
            p ->
                new PublicacionCerrada(
                    p.id(),
                    p.fecha(),
                    p.estado(),
                    p.cierre().orElse(CierreDePublicacion.de(MotivoDePublicacion.DESCONOCIDO)),
                    extracto(Optional.ofNullable(mensajes.get(p.mensajePrincipalId()))),
                    p.medios().size()))
        .toList();
  }

  static String extracto(Optional<MensajeProveedor> principal) {
    return principal
        .flatMap(MensajeProveedor::textoLegible)
        .map(t -> t.replaceAll("\\s+", " ").strip())
        .filter(t -> !t.isEmpty())
        .map(ListarPublicacionesCerradas::recortar)
        .orElse(null);
  }

  /**
   * Por puntos de código y no por caracteres: los anuncios vienen llenos de emojis, y cortar uno
   * por la mitad deja un sustituto suelto que el JSON de la respuesta no sabe escribir.
   */
  private static String recortar(String texto) {
    if (texto.codePointCount(0, texto.length()) <= LARGO_DEL_EXTRACTO) {
      return texto;
    }
    return texto.substring(0, texto.offsetByCodePoints(0, LARGO_DEL_EXTRACTO)).stripTrailing()
        + "…";
  }
}
