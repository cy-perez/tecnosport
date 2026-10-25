package co.tecnosport.api.presentation.compartido;

import jakarta.servlet.http.HttpServletRequest;

/**
 * La IP real de quien hace la petición, tal como la dejó resuelta {@link FiltroIpDelCliente}. La
 * leen el límite de intentos y la constancia de autorización de datos (docs/08-seguridad-legal.md:
 * "se guarda fecha, versión del texto e IP").
 *
 * <p><b>Aquí no se decide nada, y es a propósito.</b> Hasta el 25 de octubre de 2026 esta clase
 * leía el primer valor de {@code X-Forwarded-For}, que es el que escribe el cliente: con una
 * cabecera distinta en cada petición, los cuatro límites por IP no limitaban nada y la constancia
 * guardaba la IP que el titular quisiera (ADR-0087). Qué valor de la cabecera es de fiar depende
 * del despliegue —cuántos intermediarios hay delante y si el proxy de la web viene probando quién
 * es—, y eso es configuración, que esta clase estática no tiene.
 *
 * <p>Sin el filtro delante se cae a {@code getRemoteAddr()}. En Cloud Run es la IP del frente de
 * Google y mete a todo el mundo en el mismo cubo, que es un fallo ruidoso; lo que no hace nunca es
 * volver a creerle a la cabecera.
 */
public final class IpDelCliente {

  static final String ATRIBUTO = IpDelCliente.class.getName();

  private IpDelCliente() {}

  public static String de(HttpServletRequest request) {
    return request.getAttribute(ATRIBUTO) instanceof String ip ? ip : request.getRemoteAddr();
  }
}
