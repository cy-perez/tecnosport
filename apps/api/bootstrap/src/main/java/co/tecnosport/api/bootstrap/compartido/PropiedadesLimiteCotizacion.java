package co.tecnosport.api.bootstrap.compartido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límite para {@code POST /api/v1/envios/cotizacion}, que tiene un problema propio: es el único
 * endpoint público que llama <strong>sincrónicamente</strong> a una API externa con cuota —Skydropx
 * admite dos peticiones por segundo— y además se paga. Sin techo, un script agota la cuota del
 * negocio y deja el checkout sin cotizar para todo el mundo.
 *
 * <p>Más generoso que el de pedidos porque cotizar no es comprar: un comprador legítimo corrige la
 * dirección varias veces, cambia de ciudad y vuelve. El tope está pensado para frenar a un script,
 * no a una persona indecisa.
 *
 * <p>Solo por IP, sin variante por cuenta: la cotización no exige sesión ni correo, así que no hay
 * a quién atribuirla más que a su origen.
 */
@ConfigurationProperties(prefix = "tecnosport.limite-intentos.cotizacion")
public record PropiedadesLimiteCotizacion(int ipMaximo, int ipMinutos) {

  public PropiedadesLimiteCotizacion {
    if (ipMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.cotizacion.ip-maximo debe ser mayor que cero.");
    }
    if (ipMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.cotizacion.ip-minutos debe ser mayor que cero.");
    }
  }
}
