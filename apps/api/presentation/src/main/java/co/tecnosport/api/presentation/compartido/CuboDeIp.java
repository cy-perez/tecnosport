package co.tecnosport.api.presentation.compartido;

import java.util.Locale;

/**
 * Qué parte de una IP identifica a quien la usa, para la llave del límite de intentos (ADR-0087).
 *
 * <p>Una IPv4 se cuenta entera. Una IPv6 se cuenta por su <b>/64</b>, y no es un detalle: la API de
 * dev publica cinco registros AAAA, y cualquier servidor alquilado trae un /64 propio —2^64
 * direcciones—. Contada entera, cada petición desde una dirección nueva del mismo rango estrenaba
 * cubo, que es exactamente lo que hacía cambiar el {@code X-Forwarded-For} antes de este ADR. Lo
 * levantó la revisión de seguridad.
 *
 * <p>Solo para la llave. La constancia de {@code autorizacion_datos} guarda la IP completa: ahí lo
 * que importa es qué dirección fue, no a quién agrupar.
 *
 * <p>Sin {@code InetAddress}: con algo que no es un literal hace una consulta de DNS, y lo que
 * llega aquí viene de una cabecera.
 */
public final class CuboDeIp {

  private static final int GRUPOS = 8;
  private static final int GRUPOS_DEL_PREFIJO = 4;

  private CuboDeIp() {}

  public static String de(String ip) {
    if (ip == null || ip.indexOf(':') < 0) {
      return ip;
    }
    String[] grupos = expandir(ip.toLowerCase(Locale.ROOT));
    if (grupos == null) {
      // Algo con dos puntos que no se deja expandir: mejor contarlo entero que adivinar.
      return ip;
    }
    return String.join(":", grupos[0], grupos[1], grupos[2], grupos[3]) + "::/64";
  }

  /**
   * Los ocho grupos de una IPv6 escrita con {@code ::} o sin él, o {@code null} si no tiene forma.
   * Una IPv4 incrustada al final ({@code ::ffff:190.24.10.5}) cuenta como sus dos grupos; da igual
   * qué valgan, porque el prefijo solo mira los cuatro primeros.
   */
  private static String[] expandir(String ip) {
    String texto = ip;
    int ultimoGrupo = texto.lastIndexOf(':');
    if (texto.indexOf('.', ultimoGrupo) >= 0) {
      texto = texto.substring(0, ultimoGrupo + 1) + "0:0";
    }
    int doble = texto.indexOf("::");
    if (doble != texto.lastIndexOf("::")) {
      return null;
    }
    String[] izquierda;
    String[] derecha;
    if (doble < 0) {
      izquierda = texto.split(":", -1);
      derecha = new String[0];
    } else {
      String antes = texto.substring(0, doble);
      String despues = texto.substring(doble + 2);
      izquierda = antes.isEmpty() ? new String[0] : antes.split(":", -1);
      derecha = despues.isEmpty() ? new String[0] : despues.split(":", -1);
    }
    int faltan = GRUPOS - izquierda.length - derecha.length;
    if (faltan < 0 || (doble < 0 && faltan != 0) || (doble >= 0 && faltan < 1)) {
      return null;
    }
    String[] grupos = new String[GRUPOS];
    int i = 0;
    for (String grupo : izquierda) {
      grupos[i++] = grupo;
    }
    for (int k = 0; k < faltan; k++) {
      grupos[i++] = "0";
    }
    for (String grupo : derecha) {
      grupos[i++] = grupo;
    }
    for (int k = 0; k < GRUPOS_DEL_PREFIJO; k++) {
      String normalizado = normalizar(grupos[k]);
      if (normalizado == null) {
        return null;
      }
      grupos[k] = normalizado;
    }
    return grupos;
  }

  /** Sin ceros a la izquierda: {@code 0db8} y {@code db8} son el mismo grupo y el mismo cubo. */
  private static String normalizar(String grupo) {
    if (grupo.isEmpty() || grupo.length() > 4 || !grupo.matches("[0-9a-f]+")) {
      return null;
    }
    return Integer.toHexString(Integer.parseInt(grupo, 16));
  }
}
