package co.tecnosport.api.domain.sugerencia;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que alguien nos quiso decir desde el buzón de sugerencias.
 *
 * <p><b>No es una {@code SolicitudAtencion}, y la diferencia importa.</b> Aquel agregado registra
 * peticiones, quejas, reclamos y derechos de datos personales: cosas a las que la ley le pone un
 * reloj —quince días hábiles, diez, cinco— y que por eso llevan número de radicado, plazo, prórroga
 * y respuesta. Una sugerencia no tiene ninguno de esos; lo que tiene es alguien que se tomó el
 * trabajo de escribir. Meterla allí habría exigido inventarle un plazo legal que no existe y un
 * {@code radicadaPor} —una persona del negocio— que aquí no hay, porque esto lo escribe quien
 * compra y no quien atiende.
 *
 * <p>Por eso el buzón dice en voz alta, en la propia pantalla, que lo que sí tiene plazo —garantía,
 * retracto, reclamo, datos personales— va por el canal de contacto y no por aquí. Un buzón que se
 * traga un reclamo es peor que no tener buzón.
 *
 * <h2>El correo es opcional, y es el centro del diseño</h2>
 *
 * <p>Sin correo no hay <b>ningún</b> dato personal en esta fila: solo un texto y una fecha. Eso no
 * es una carencia sino la forma honesta de pedir una opinión — quien quiere decir algo incómodo lo
 * dice si no tiene que firmarlo, y lo que se pierde es solo la posibilidad de contestar. Con
 * correo, el tratamiento entra de lleno en la Ley 1581 y el caso de uso exige la autorización y
 * deja su constancia, igual que el registro y el checkout.
 */
public final class Sugerencia {

  /**
   * El tope del mensaje. No es un dato de negocio —no hay tarifa ni plazo detrás— sino un límite
   * técnico: la columna es {@code text} y lo que esto evita es que una petición pública sin sesión
   * pueda escribir megabytes por intento. Dos mil caracteres son unas trescientas palabras, de
   * sobra para una sugerencia; quien necesite más espacio está escribiendo un reclamo, y para eso
   * está el canal de contacto.
   */
  public static final int MAXIMO_CARACTERES_MENSAJE = 2000;

  private final UUID id;
  private final String mensaje;
  private final CorreoElectronico correo;
  private final Instant recibidaEn;

  public Sugerencia(UUID id, String mensaje, CorreoElectronico correo, Instant recibidaEn) {
    this.id = Objects.requireNonNull(id, "El id de la sugerencia no puede ser nulo.");
    if (mensaje == null || mensaje.isBlank()) {
      throw new ExcepcionDeDominio("Una sugerencia sin mensaje no es una sugerencia.");
    }
    // Se recorta a los lados antes de medir: un mensaje de dos mil caracteres y una línea en blanco
    // al final no es un mensaje de dos mil uno.
    String limpio = mensaje.strip();
    if (limpio.length() > MAXIMO_CARACTERES_MENSAJE) {
      throw new ExcepcionDeDominio(
          "Una sugerencia no puede pasar de " + MAXIMO_CARACTERES_MENSAJE + " caracteres.");
    }
    this.mensaje = limpio;
    this.correo = correo;
    this.recibidaEn =
        Objects.requireNonNull(recibidaEn, "La fecha de la sugerencia no puede ser nula.");
  }

  /**
   * {@code correo} en nulo es una sugerencia anónima, que es un caso válido y no un dato faltante.
   */
  public static Sugerencia recibir(String mensaje, CorreoElectronico correo, Instant ahora) {
    return new Sugerencia(GeneradorIdentificador.nuevo(), mensaje, correo, ahora);
  }

  public UUID id() {
    return id;
  }

  public String mensaje() {
    return mensaje;
  }

  /**
   * Vacío cuando quien escribió prefirió no dejarlo. Se devuelve como {@link Optional} y no como
   * {@code null} porque la diferencia entre "no dio correo" y "se nos olvidó leerlo" es la
   * diferencia entre no contestar y no poder contestar, y quien llame tiene que decidirla a la
   * vista.
   */
  public Optional<CorreoElectronico> correo() {
    return Optional.ofNullable(correo);
  }

  public Instant recibidaEn() {
    return recibidaEn;
  }
}
