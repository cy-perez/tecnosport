package co.tecnosport.api.application.envio;

/**
 * Lo que se pudo sacar del cuerpo de un aviso del webhook: tres respuestas, no dos.
 *
 * <p><strong>La tercera existe por lo que se ve en el registro.</strong> Antes esto era un {@code
 * Optional<String>} vacío, y ahí caían juntos un cuerpo corrupto y un evento perfectamente legítimo
 * que no habla de un paquete. No son lo mismo: la documentación de la plataforma describe eventos
 * de {@code orders}, {@code quotation}, {@code rate}, {@code extra_charges} y {@code pickups}, y
 * cualquiera de ellos escribía un aviso de "no se supo leer" que se lee como una falla.
 *
 * <p><strong>Y cuánto de eso llega de verdad se midió el 16 de septiembre de 2026</strong>: el
 * panel de la cuenta sólo deja suscribir once eventos y los once son de paquetes (docs/13 §6.9), de
 * modo que hoy esto no dispara nunca. Se conserva igual, y no por si acaso: el filtro por {@code
 * data.type} tiene que existir —sin él, un evento de otro tipo con un identificador dentro se
 * leería como si fuera una guía— y lo único que esta tercera respuesta agrega es que el día que la
 * plataforma mande uno, el registro diga qué era en vez de avisar de una falla que no hubo.
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
