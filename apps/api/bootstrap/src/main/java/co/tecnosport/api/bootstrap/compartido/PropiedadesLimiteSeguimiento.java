package co.tecnosport.api.bootstrap.compartido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límite para {@code POST /api/v1/pedidos/seguimiento}, y es el más estrecho de los cuatro por una
 * razón concreta: <strong>es el único endpoint público donde el identificador es
 * adivinable</strong>.
 *
 * <p>El seguimiento por {@code id} no necesitaba techo porque el id es un UUID v7: recorrerlo no es
 * una opción. El número legible sí lo es — van del {@code 000001} hacia arriba dentro de cada año—,
 * así que lo único que protege el pedido es que el correo coincida. Quien ya conozca el correo de
 * una persona puede recorrer números hasta dar con uno suyo, y contra eso el caso de uso no puede
 * hacer nada: lo que se puede hacer es encarecer el intento.
 *
 * <p>Más estrecho que el de pedidos y mucho más que el de cotización porque el uso legítimo es
 * mínimo: alguien consulta su pedido unas pocas veces al día, y copia el número de un correo. Un
 * comprador que se equivoca tres veces seguidas tiene margen de sobra; un script no.
 *
 * <p>Solo por IP, sin variante por cuenta: no hay sesión, y el correo del cuerpo no sirve para
 * atribuir —es justo lo que el atacante controla—.
 */
@ConfigurationProperties(prefix = "tecnosport.limite-intentos.seguimiento")
public record PropiedadesLimiteSeguimiento(int ipMaximo, int ipMinutos) {

  public PropiedadesLimiteSeguimiento {
    if (ipMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.seguimiento.ip-maximo debe ser mayor que cero.");
    }
    if (ipMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.seguimiento.ip-minutos debe ser mayor que cero.");
    }
  }
}
