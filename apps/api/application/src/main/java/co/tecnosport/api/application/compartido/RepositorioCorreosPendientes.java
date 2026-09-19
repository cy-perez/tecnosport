package co.tecnosport.api.application.compartido;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La bandeja de salida, vista desde el drenaje.
 *
 * <p><b>No tiene {@code encolar}, y es a propósito.</b> Quien encola es el adaptador de {@link
 * EnviadorDeCorreo}, que vive entero en {@code infrastructure} y escribe su fila con la transacción
 * de quien llama ya abierta — ésa es toda la gracia del mecanismo. Meter aquí un método para
 * encolar solo serviría para que alguien encolara desde un caso de uso saltándose esa propiedad.
 */
public interface RepositorioCorreosPendientes {

  /**
   * Los correos que toca intentar ahora: sin enviar, por debajo del tope de intentos y con su
   * próximo intento ya vencido.
   */
  List<CorreoPendiente> buscarEnviables(int maxIntentos, Instant ahora, int limite);

  /**
   * Reclama un correo <b>y de paso programa su siguiente intento</b>. Las dos cosas en la misma
   * sentencia condicional, que es lo que hace que el mecanismo no tenga filas atascadas: si esta
   * instancia muere con el correo en la mano, la fila ya tiene su reintento puesto y otra vuelta la
   * recoge. Un {@code reclamado_en} aparte habría necesitado además un corte por tiempo para
   * soltarlo, que es justo el defecto que {@code EmisionDeGuia} pagó con una emisión {@code
   * EN_CURSO} que no vencía nunca.
   *
   * @return {@code true} si esta instancia se lo quedó; {@code false} si otra se adelantó.
   */
  boolean reclamar(UUID id, Instant ahora, Instant proximoIntento);

  /** El correo salió. */
  void marcarEnviado(UUID id, Instant ahora);

  /**
   * El correo no salió. No reprograma nada —eso ya lo hizo {@link #reclamar}— y solo deja escrito
   * el porqué, que es lo único que va a tener delante quien mire la tabla cuando un correo se
   * rinda.
   */
  void registrarFallo(UUID id, String detalle);

  /**
   * Borra los ya enviados anteriores a {@code limite}. El cuerpo de un correo lleva el nombre de
   * quien compró, su pedido y a veces su dirección: es dato personal en reposo y no se guarda
   * indefinidamente (docs/08-seguridad-legal.md).
   *
   * @return cuántos se borraron.
   */
  int purgarEnviados(Instant limite);
}
