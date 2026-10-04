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
 * <p>La regla se lee en el chat: <b>un mensaje con precio abre un producto</b>, y cada foto o texto
 * sin precio del mismo remitente es del precio <b>más cercano en el tiempo</b>, hacia atrás o hacia
 * adelante, mientras quede dentro de la ventana. Un pie de foto con precio abre también, y esa foto
 * es su primer medio.
 *
 * <p>Hacia adelante la ventana se mide desde el último mensaje anexado y no desde el principal: el
 * proveedor manda las cuatro fotos de una en una y la última puede llegar bastante después de la
 * primera. Lo que la ventana corta es el silencio, no la duración del envío. Hacia atrás se mide
 * desde el propio precio, y <b>existe porque así publican los proveedores de verdad</b>: en las
 * exportaciones reales de los dos chats (30 de septiembre de 2026) el álbum de fotos sale primero y
 * el texto con el precio después, al minuto. El chat de prueba traía el texto primero, y con esa
 * única forma todas las fotos reales habrían quedado sueltas y cada borrador habría salido con
 * {@code SIN_FOTOS}.
 *
 * <p>Cuando un mensaje cabe en los dos lados —el precio de antes todavía abierto y otro precio
 * pocos minutos después— gana el más cercano, y <b>en empate el de después</b>, por la misma razón
 * que existe la ventana hacia atrás: la foto sale antes que su texto. El empate no es raro, es lo
 * normal: Android exporta sin segundos, y cuando el proveedor manda en el mismo minuto el precio de
 * un producto, la foto del siguiente y el precio de ese siguiente, la foto queda a cero de los dos.
 * Con el empate hacia atrás, en la exportación de Imperio Wicho (3 de octubre de 2026) la foto de
 * la Superstar cayó en el anuncio de caballero y la Superstar salió sin fotos.
 *
 * <p>Lo que queda fuera —un texto sin precio lejos de todo, una foto huérfana, un audio— no es de
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

    // Los precios son las anclas; cada uno abre su publicación antes de repartir lo demás.
    List<Integer> anclas = new ArrayList<>();
    for (int i = 0; i < enOrden.size(); i++) {
      boolean conPrecio =
          enOrden.get(i).textoLegible().map(PatronDePrecio::tienePrecio).orElse(false);
      if (conPrecio) {
        anclas.add(i);
      }
    }
    List<PublicacionProveedor> publicaciones = new ArrayList<>(anclas.size());
    List<Instant> ultimoAnexado = new ArrayList<>(anclas.size());
    for (int ancla : anclas) {
      publicaciones.add(PublicacionProveedor.abrir(enOrden.get(ancla)));
      ultimoAnexado.add(enOrden.get(ancla).enviadoEn());
    }

    int sueltos = 0;
    int anterior = -1; // posición en `anclas` del último precio ya visto
    for (int i = 0; i < enOrden.size(); i++) {
      if (anterior + 1 < anclas.size() && anclas.get(anterior + 1) == i) {
        anterior++;
        continue;
      }
      MensajeProveedor mensaje = enOrden.get(i);
      if (mensaje.tipo() == TipoMensaje.OTRO) {
        sueltos++;
        continue;
      }
      Instant cuando = mensaje.enviadoEn();
      int siguiente = anterior + 1 < anclas.size() ? anterior + 1 : -1;

      boolean cabeAtras =
          anterior >= 0 && !cuando.isAfter(ultimoAnexado.get(anterior).plus(ventana));
      boolean cabeAdelante =
          siguiente >= 0
              && !enOrden.get(anclas.get(siguiente)).enviadoEn().isAfter(cuando.plus(ventana));

      int elegida;
      if (cabeAtras && cabeAdelante) {
        Duration haciaAtras =
            Duration.between(enOrden.get(anclas.get(anterior)).enviadoEn(), cuando);
        Duration haciaAdelante =
            Duration.between(cuando, enOrden.get(anclas.get(siguiente)).enviadoEn());
        elegida = haciaAdelante.compareTo(haciaAtras) <= 0 ? siguiente : anterior;
      } else if (cabeAtras) {
        elegida = anterior;
      } else if (cabeAdelante) {
        elegida = siguiente;
      } else {
        sueltos++;
        continue;
      }
      publicaciones.get(elegida).anexar(mensaje);
      if (elegida == anterior) {
        ultimoAnexado.set(anterior, cuando);
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
