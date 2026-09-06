package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.EstadoSetRotacion;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.domain.catalogo.SetRotacionIncompletoException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Segundo paso: una URL firmada por fotograma, todas de una. El asistente sube los N archivos
 * directo a Cloud Storage sin que los bytes pasen por el backend (docs/07-infra-gcp.md).
 *
 * <p><strong>Cuántas URL se emiten lo decide el set, no el cliente</strong>: son exactamente las
 * que se prometieron al abrirlo. La key de cada objeto la arma el servidor con el orden dentro del
 * set, así que el cliente tampoco elige dónde escribe.
 */
public final class SolicitarSubidasDeRotacion {

  private final RepositorioSetsRotacion repositorioSetsRotacion;
  private final AlmacenDeImagenes almacenDeImagenes;

  public SolicitarSubidasDeRotacion(
      RepositorioSetsRotacion repositorioSetsRotacion, AlmacenDeImagenes almacenDeImagenes) {
    this.repositorioSetsRotacion = Objects.requireNonNull(repositorioSetsRotacion);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
  }

  public List<SubidaDeFotograma> ejecutar(SolicitarSubidasDeRotacionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    SetRotacion set =
        repositorioSetsRotacion
            .buscarPorId(comando.setId())
            .orElseThrow(() -> new SetRotacionNoEncontradoException(comando.setId()));

    if (set.estado() != EstadoSetRotacion.BORRADOR) {
      throw new SetRotacionIncompletoException(
          "Solo un set en BORRADOR recibe subidas (estado actual: " + set.estado() + ").");
    }

    String extension = TiposDeImagen.extensionDe(comando.contentType());
    List<SubidaDeFotograma> subidas = new ArrayList<>();
    for (int orden = 0; orden < set.fotogramasPrometidos(); orden++) {
      String objectKey = ClavesDeRotacion.deFotograma(set, orden, extension);
      UrlFirmada urlFirmada =
          almacenDeImagenes.generarUrlDeSubida(objectKey, comando.contentType());
      subidas.add(new SubidaDeFotograma(orden, urlFirmada.url(), objectKey));
    }
    return subidas;
  }
}
