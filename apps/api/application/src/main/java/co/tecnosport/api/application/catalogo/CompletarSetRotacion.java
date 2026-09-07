package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.domain.catalogo.SetRotacionIncompletoException;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import java.util.List;
import java.util.Objects;

/**
 * Tercer paso: el asistente reporta los fotogramas que subió y el set se cierra en COMPLETO.
 *
 * <p><strong>No se confía en que la subida terminó</strong>: de cada fotograma se verifica contra
 * el almacén real que el objeto existe y que no está vacío. Un set que no pasa la verificación se
 * queda en BORRADOR, entero — no se guarda a medias, porque medio set publicado es un visor roto.
 *
 * <p>Lo que <strong>no</strong> se verifica, y conviene tenerlo escrito: que el archivo sea de
 * verdad una imagen, y que sus dimensiones reales sean las declaradas. Comprobarlo exigiría
 * descargar y decodificar los bytes en el backend, que es justo lo que la subida directa evita
 * (docs/07-infra-gcp.md). Se contrasta lo declarado contra lo que un set de rotación exige
 * —cuadrado y de 1000 px— y el riesgo restante es el mismo que ya aceptó ADR-0016 mientras el panel
 * lo use solo el administrador.
 */
public final class CompletarSetRotacion {

  /** Lado de cada fotograma, en píxeles (docs/10-captura-360.md: 1000 x 1000, siempre 1:1). */
  public static final int LADO_ESPERADO_PX = 1000;

  private final RepositorioSetsRotacion repositorioSetsRotacion;
  private final AlmacenDeImagenes almacenDeImagenes;

  public CompletarSetRotacion(
      RepositorioSetsRotacion repositorioSetsRotacion, AlmacenDeImagenes almacenDeImagenes) {
    this.repositorioSetsRotacion = Objects.requireNonNull(repositorioSetsRotacion);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
  }

  public SetRotacion ejecutar(CompletarSetRotacionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    SetRotacion set =
        repositorioSetsRotacion
            .buscarPorId(comando.setId())
            .orElseThrow(() -> new SetRotacionNoEncontradoException(comando.setId()));

    List<FotogramaComando> fotogramas = Objects.requireNonNullElse(comando.fotogramas(), List.of());
    for (FotogramaComando fotograma : fotogramas) {
      set.agregarFotograma(verificar(set, fotograma));
    }
    set.completar();

    repositorioSetsRotacion.actualizar(set);
    return set;
  }

  private ImagenProducto verificar(SetRotacion set, FotogramaComando fotograma) {
    String prefijoEsperado = ClavesDeRotacion.prefijoDe(set);
    if (fotograma.objectKey() == null || !fotograma.objectKey().startsWith(prefijoEsperado)) {
      throw new SetRotacionIncompletoException(
          "El objeto '" + fotograma.objectKey() + "' no pertenece al set " + set.id() + ".");
    }

    if (fotograma.ancho() != fotograma.alto() || fotograma.ancho() != LADO_ESPERADO_PX) {
      throw new SetRotacionIncompletoException(
          "Cada fotograma del set es cuadrado y de "
              + LADO_ESPERADO_PX
              + " px; el "
              + fotograma.orden()
              + " se declaró de "
              + fotograma.ancho()
              + "x"
              + fotograma.alto()
              + ".");
    }

    long bytes =
        almacenDeImagenes
            .tamanoBytes(fotograma.objectKey())
            .orElseThrow(() -> new ObjetoDeImagenNoEncontradoException(fotograma.objectKey()));
    if (bytes <= 0) {
      throw new SetRotacionIncompletoException(
          "El fotograma " + fotograma.orden() + " llegó vacío: '" + fotograma.objectKey() + "'.");
    }

    String url = almacenDeImagenes.urlPublica(fotograma.objectKey());
    return ImagenProducto.crear(
        TipoImagen.ROTACION,
        fotograma.orden(),
        url,
        url,
        fotograma.ancho(),
        fotograma.alto(),
        bytes,
        // El SHA-256 lo calcula el asistente sobre los bytes que subió, igual que en la imagen
        // principal. El backend no puede verificarlo sin descargar el archivo (ADR-0016), pero sí
        // exige que sea un hash bien formado: HashContenido rechaza cualquier otra cosa.
        new HashContenido(fotograma.hash()),
        null,
        null);
  }
}
