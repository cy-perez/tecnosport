package co.tecnosport.api.presentation.usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Responde 401 cuando la petición llega <b>sin autenticar</b> a una ruta que lo exige. Sin esto,
 * Spring Security usa su punto de entrada por omisión y contesta <b>403</b>: el código de "sé quién
 * eres y aun así no puedes", para el caso contrario, en el que no sabe quién es nadie.
 *
 * <p>No es cosmética. El frontend renueva el token de acceso —quince minutos de vigencia— dentro de
 * {@code crearClienteAutenticado}, y ese reintento cuelga de recibir un <b>401</b>: con 403 no
 * disparaba nunca, así que cualquier pantalla del panel abierta un rato largo mostraba un error en
 * vez de renovar sola. Encontrado el 22 de septiembre de 2026 midiendo el recorrido de {@code POST
 * /auth/clave} contra la API viva, y comprobado que venía de mucho antes: {@code /api/v1/admin/**}
 * respondía lo mismo desde que existe.
 *
 * <p><b>El 403 no desaparece, cambia de sitio.</b> Quien sí está autenticado y no tiene el rol —un
 * {@code CLIENTE} llamando al panel— no pasa por aquí sino por el manejador de acceso denegado, y
 * ese caso sigue siendo 403, que es lo correcto. Los dos están cubiertos por {@code
 * CadenaDeSeguridadTest}.
 *
 * <p>El cuerpo se escribe a mano, con la forma de {@code ProblemDetail} del resto de la API: este
 * punto corre dentro de la cadena de filtros, antes del {@code DispatcherServlet}, así que {@code
 * ManejadorDeErrores} no lo alcanza. Mismo motivo por el que lleva el charset explícito que {@code
 * FiltroLimiteIntentos} documenta: sin él {@code getWriter()} escribe en ISO-8859-1 y las tildes
 * salen corruptas.
 */
public class PuntoDeEntradaNoAutenticado implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException excepcion)
      throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write(
            "{\"type\":\"https://tecnosport.co/errores/no-autenticado\","
                + "\"title\":\"No autenticado\","
                + "\"status\":401,"
                + "\"detail\":\"Esta ruta exige una sesión iniciada.\","
                + "\"instance\":\""
                + escaparJson(request.getRequestURI())
                + "\","
                + "\"codigo\":\"NO_AUTENTICADO\"}");
  }

  /**
   * La URI de la petición es entrada del cliente y aquí se concatena dentro de un literal JSON, sin
   * que ningún serializador la toque: este punto corre antes del {@code DispatcherServlet} y arma
   * el cuerpo a mano.
   *
   * <p><b>Hoy no es explotable, y ese es justo el motivo de escribir esto.</b> Lo único que lo
   * sostiene es que Tomcat rechaza las comillas y la barra invertida crudas en la línea de petición
   * y que {@code getRequestURI()} no decodifica {@code %22} — o sea, una configuración por omisión
   * del contenedor, no el código. El día que eso cambie, el cuerpo de error sale malformado y el
   * frontend deja de poder leer el {@code codigo}: la renovación silenciosa del token cuelga de
   * este 401, así que {@code cambiarClave} y {@code refrescar} caerían a la rama genérica.
   *
   * <p>Se escapa a mano y no con Jackson por lo mismo que el cuerpo se arma a mano: traer un {@code
   * ObjectMapper} aquí solo para esto sería más maquinaria que el problema.
   */
  private static String escaparJson(String valor) {
    if (valor == null) {
      return "";
    }
    StringBuilder salida = new StringBuilder(valor.length() + 8);
    for (int i = 0; i < valor.length(); i++) {
      char caracter = valor.charAt(i);
      switch (caracter) {
        case '"' -> salida.append("\\\"");
        case '\\' -> salida.append("\\\\");
        case '\n' -> salida.append("\\n");
        case '\r' -> salida.append("\\r");
        case '\t' -> salida.append("\\t");
        default -> {
          // JSON no admite caracteres de control crudos (RFC 8259 §7).
          if (caracter < 0x20) {
            salida.append(String.format("\\u%04x", (int) caracter));
          } else {
            salida.append(caracter);
          }
        }
      }
    }
    return salida.toString();
  }
}
