package co.tecnosport.api.application.envio;

/**
 * Lo que se pudo sacar del cuerpo de un aviso del webhook: tres respuestas, no dos.
 *
 * <p><strong>La tercera existe por lo que se ve en el registro.</strong> Antes esto era un {@code
 * Optional<String>} vacío, y ahí caían juntos un cuerpo corrupto y un evento perfectamente legítimo
 * que no habla de un paquete. No son lo mismo: por la misma suscripción llegan {@code orders},
 * {@code quotation}, {@code rate}, {@code extra_charges} y {@code pickups} —se suscribieron todos a
 * propósito— y cada uno escribía un aviso de "no se supo leer" que se lee como una falla. Con el
 * canal entero suscrito eso no es una rareza: es el caso común.
 *
 * <p>Y hay un momento en que la diferencia decide algo: al configurar {@code
 * SKYDROPX_SECRETO_WEBHOOK} por primera vez, la única prueba barata de que el secreto quedó bien es
 * un evento de {@code quotation} —cotizar no cuesta saldo, emitir una guía sí—. Si ese evento se
 * registra igual que un cuerpo roto, la señal que hace falta leer no se distingue del ruido.
 *
 * <p>{@code Ilegible} es lo que queda para lo que de verdad no se entiende: lo que no es JSON, lo
 * que no trae {@code data.type}, y el paquete sin número de guía — que sí es nuestro y sí está mal.
 */
public sealed interface LecturaDeEvento {

  /** El aviso habla de un paquete, y este es su número de guía. */
  record DeUnaGuia(String numero) implements LecturaDeEvento {
    public DeUnaGuia {
      if (numero == null || numero.isBlank()) {
        throw new IllegalArgumentException("Una lectura con guía exige el número.");
      }
    }
  }

  /** El aviso es de otra cosa de la plataforma —una orden, una cotización, una recolección—. */
  record DeOtroTipo(String tipo) implements LecturaDeEvento {
    public DeOtroTipo {
      if (tipo == null || tipo.isBlank()) {
        throw new IllegalArgumentException("Un evento de otro tipo exige cuál es ese tipo.");
      }
    }
  }

  /** No se entendió el cuerpo. Se descarta, se responde 200 y queda escrito. */
  record Ilegible() implements LecturaDeEvento {}
}
