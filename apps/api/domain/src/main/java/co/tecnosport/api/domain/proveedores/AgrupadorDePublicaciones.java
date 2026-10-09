package co.tecnosport.api.domain.proveedores;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
 * pocos minutos después— gana el más cercano, y <b>el empate lo decide el proveedor</b>, con su
 * {@link OrdenDePublicacion}. El empate no es raro, es lo normal: Android exporta sin segundos, y
 * cuando el proveedor manda en el mismo minuto el precio de un producto, una foto y el precio del
 * siguiente, la foto queda a cero de los dos. Quien manda primero el álbum ({@code FOTOS_PRIMERO})
 * quiere el de después: con el empate hacia atrás, en la exportación de Imperio Wicho (3 de octubre
 * de 2026) la foto de la Superstar cayó en el anuncio de caballero. Quien manda primero el texto
 * ({@code TEXTO_PRIMERO}) quiere el de antes: con el empate hacia adelante, en la de La Riverah (5
 * de octubre de 2026) la foto del jean de cuero cayó en el jean blanco del mismo minuto. Fuera del
 * empate el orden no cuenta.
 *
 * <h2>El álbum que llega lejos de su precio</h2>
 *
 * <p>Un precio que terminó el reparto <b>sin una sola foto</b> recoge el álbum suelto que tiene
 * pegado: las fotos que nadie se quedó y que van seguidas, sin otro mensaje en medio, justo antes o
 * justo después de él. La ventana corta el silencio entre fotos de un mismo envío; no puede cortar
 * el que hay entre un álbum y su texto cuando no hay nada más en el chat. En la exportación de
 * D'Osman del 7 de octubre de 2026 el álbum del bolso ejecutivo salió a las 14:24 y el texto a las
 * 15:01: con la ventana de quince minutos las cinco fotos quedaban sueltas y el borrador salía con
 * {@code SIN_FOTOS}. Las fotos del álbum siguen pidiendo la ventana entre ellas, el álbum tiene que
 * llegar a {@link #RESCATE_DE_ALBUM} o menos del precio, y si hay uno a cada lado decide el {@link
 * OrdenDePublicacion}. Un precio que ya tiene fotos no recoge nada: ahí las sueltas son de otro
 * anuncio o de ninguno.
 *
 * <p>Lo que queda fuera —un texto sin precio lejos de todo, una foto huérfana, un audio— no es de
 * ninguna publicación. Se cuenta, y no se inventa un producto para darle sitio.
 */
public final class AgrupadorDePublicaciones {

  /**
   * Hasta dónde un precio sin fotos va a buscar su álbum suelto. Parámetro técnico, no de negocio:
   * cubre los 37 minutos de D'Osman con holgura, y más allá ya no es un álbum que se demoró sino
   * fotos de otra cosa.
   */
  static final Duration RESCATE_DE_ALBUM = Duration.ofHours(1);

  private final Duration ventana;

  public AgrupadorDePublicaciones(Duration ventana) {
    Objects.requireNonNull(ventana, "La ventana de agrupación no puede ser nula.");
    if (ventana.isNegative() || ventana.isZero()) {
      throw new IllegalArgumentException("La ventana de agrupación tiene que ser positiva.");
    }
    this.ventana = ventana;
  }

  public Resultado agrupar(List<MensajeProveedor> mensajes, OrdenDePublicacion orden) {
    Objects.requireNonNull(orden, "El orden de publicación no puede ser nulo.");
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

    boolean[] suelto = new boolean[enOrden.size()];
    int sueltos = 0;
    int anterior = -1; // posición en `anclas` del último precio ya visto
    for (int i = 0; i < enOrden.size(); i++) {
      if (anterior + 1 < anclas.size() && anclas.get(anterior + 1) == i) {
        anterior++;
        continue;
      }
      MensajeProveedor mensaje = enOrden.get(i);
      if (mensaje.tipo() == TipoMensaje.OTRO) {
        suelto[i] = true;
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
        int comparacion = haciaAdelante.compareTo(haciaAtras);
        boolean empateHaciaAdelante = orden == OrdenDePublicacion.FOTOS_PRIMERO;
        elegida =
            comparacion < 0 || (comparacion == 0 && empateHaciaAdelante) ? siguiente : anterior;
      } else if (cabeAtras) {
        elegida = anterior;
      } else if (cabeAdelante) {
        elegida = siguiente;
      } else {
        suelto[i] = true;
        sueltos++;
        continue;
      }
      publicaciones.get(elegida).anexar(mensaje);
      if (elegida == anterior) {
        ultimoAnexado.set(anterior, cuando);
      }
    }

    for (int k = 0; k < anclas.size(); k++) {
      PublicacionProveedor publicacion = publicaciones.get(k);
      if (!publicacion.medios().isEmpty()) {
        continue;
      }
      List<Integer> atras = albumSuelto(enOrden, suelto, anclas.get(k), -1);
      List<Integer> adelante = albumSuelto(enOrden, suelto, anclas.get(k), 1);
      List<Integer> album;
      if (!atras.isEmpty() && !adelante.isEmpty()) {
        album = orden == OrdenDePublicacion.FOTOS_PRIMERO ? atras : adelante;
      } else {
        album = atras.isEmpty() ? adelante : atras;
      }
      for (int i : album) {
        publicacion.anexar(enOrden.get(i));
        suelto[i] = false;
        sueltos--;
      }
    }
    return new Resultado(publicaciones, sueltos);
  }

  /**
   * Las fotos sueltas seguidas que hay pegadas al precio en la dirección dada, en orden de fecha:
   * la más cercana a {@link #RESCATE_DE_ALBUM} o menos del precio, y cada una de las demás a la
   * ventana o menos de su vecina. Cualquier otro mensaje en medio corta el álbum.
   */
  private List<Integer> albumSuelto(
      List<MensajeProveedor> enOrden, boolean[] suelto, int ancla, int direccion) {
    List<Integer> album = new ArrayList<>();
    Instant vecina = enOrden.get(ancla).enviadoEn();
    for (int i = ancla + direccion; i >= 0 && i < enOrden.size(); i += direccion) {
      MensajeProveedor mensaje = enOrden.get(i);
      if (!suelto[i] || mensaje.tipo() != TipoMensaje.IMAGEN) {
        break;
      }
      Duration tope = album.isEmpty() ? RESCATE_DE_ALBUM : ventana;
      if (Duration.between(mensaje.enviadoEn(), vecina).abs().compareTo(tope) > 0) {
        break;
      }
      album.add(i);
      vecina = mensaje.enviadoEn();
    }
    if (direccion < 0) {
      Collections.reverse(album);
    }
    return album;
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
