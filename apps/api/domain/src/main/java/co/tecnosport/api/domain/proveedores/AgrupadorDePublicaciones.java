package co.tecnosport.api.domain.proveedores;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Convierte la secuencia de mensajes de un proveedor en publicaciones, de forma determinista.
 *
 * <p>La regla es una sola y se lee en el chat: <b>un mensaje con precio abre un producto</b>. Lo
 * que llega después del mismo remitente y dentro de la ventana —fotos, un texto sin precio como
 * «este viene con la tira en cuero»— es de ese producto. Otro mensaje con precio cierra el anterior
 * y abre el siguiente. Un pie de foto con precio abre también, y esa foto es su primer medio.
 *
 * <p>La ventana se mide desde el último mensaje anexado y no desde el principal: el proveedor manda
 * las cuatro fotos de una en una y la última puede llegar bastante después de la primera. Lo que la
 * ventana corta es el silencio, no la duración del envío.
 *
 * <p>Lo que queda fuera —un texto sin precio que no sigue a nada, una foto huérfana— no es de
 * ninguna publicación. Se cuenta, y no se inventa un producto para darle sitio.
 */
public final class AgrupadorDePublicaciones {

  private final Duration ventana;

  public AgrupadorDePublicaciones(Duration ventana) {
    Objects.requireNonNull(ventana, "La ventana de agrupación no puede ser nula.");
    if (ventana.isNegative() || ventana.isZero()) {
      throw new IllegalArgumentException("La ventana de agrupación tiene que ser positiva.");
    }
    this.ventana = ventana;
  }

  public Resultado agrupar(List<MensajeProveedor> mensajes) {
    List<MensajeProveedor> enOrden =
        mensajes.stream().sorted(Comparator.comparing(MensajeProveedor::enviadoEn)).toList();

    List<PublicacionProveedor> publicaciones = new ArrayList<>();
    PublicacionProveedor abierta = null;
    Instant ultimoAnexado = null;
    int sueltos = 0;

    for (MensajeProveedor mensaje : enOrden) {
      boolean conPrecio = mensaje.textoLegible().map(PatronDePrecio::tienePrecio).orElse(false);
      if (conPrecio) {
        abierta = PublicacionProveedor.abrir(mensaje);
        publicaciones.add(abierta);
        ultimoAnexado = mensaje.enviadoEn();
        continue;
      }
      boolean dentroDeLaVentana =
          abierta != null
              && !mensaje.enviadoEn().isAfter(ultimoAnexado.plus(ventana))
              && mensaje.tipo() != TipoMensaje.OTRO;
      if (dentroDeLaVentana) {
        abierta.anexar(mensaje);
        ultimoAnexado = mensaje.enviadoEn();
      } else {
        sueltos++;
      }
    }
    return new Resultado(publicaciones, sueltos);
  }

  /**
   * @param sueltos los mensajes que no cupieron en ninguna publicación
   */
  public record Resultado(List<PublicacionProveedor> publicaciones, int sueltos) {
    public Resultado {
      publicaciones = List.copyOf(publicaciones);
    }
  }
}
