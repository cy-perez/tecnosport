package co.tecnosport.api.application.compartido;

import co.tecnosport.api.domain.compartido.CorreoElectronico;

/**
 * Correo saliente transaccional (docs/08-seguridad-legal.md: verificación de correo al registrarse,
 * recuperación de contraseña). Puerto técnico, no de negocio — mismo criterio que {@link Reloj} y
 * {@link RepositorioIdempotencia}: application es la única capa entre presentation e
 * infrastructure.
 *
 * <p>El puerto no sabe nada de verificación ni de recuperación: recibe un asunto y un cuerpo ya
 * armados. Quién los arma <b>ya no es el caso de uso</b>: los resuelve {@link TextosDeCorreo} desde
 * los paquetes de mensajes, y el caso de uso solo elige qué {@link TextoDeCorreo} corresponde y con
 * qué datos. Hasta que eso se separó, los siete correos del sistema se concatenaban a mano dentro
 * de los casos de uso, en un solo idioma y —salvo los dos de la Fase 4— sin una sola tilde, citando
 * artículos de la Ley 1480 con faltas de ortografía. Esto sigue separando "cómo se manda un correo"
 * de "qué correo hay que mandar", y ahora también de "qué dice".
 *
 * <p><b>Un envío que falla lanza {@link CorreoNoEnviadoException}, y quien llama decide qué hacer
 * con ella.</b> Es el contrato, y hay que enunciarlo así de explícito porque durante cuatro fases
 * fue al revés: el único adaptador de producción registraba el fallo y se lo tragaba, de modo que
 * los doce llamadores tenían la decisión tomada por ellos y ninguno podía enterarse. Cinco casos de
 * uso llevaban escrito que "si el correo falla, la operación tampoco se guarda" y era falso. Lo
 * encontró una revisión adversarial; por qué ninguna prueba lo destapó es lo interesante: los
 * dobles <b>sí</b> lanzan, así que las pruebas comprobaban un escenario que el adaptador de
 * producción no podía producir. Hoy esas mismas pruebas valen, y valen para lo real.
 *
 * <p>Las tres respuestas posibles, y las tres están en uso:
 *
 * <ol>
 *   <li><b>Atraparla a propósito</b>, cuando relanzarla haría daño: {@code SolicitarRecuperacion}
 *       responde 204 exista o no la cuenta, y un 500 solo cuando la cuenta sí existe es el oráculo
 *       de enumeración que ese diseño evita; {@code RegistrarUsuario} ya creó la cuenta.
 *   <li><b>Atraparla y devolver el reclamo</b>, en las tareas que marcan "ya avisé" antes de
 *       mandar: así el siguiente ciclo lo reintenta. Es lo que hace {@code
 *       EnviarComprobantesDeCompra} con el documento de la venta.
 *   <li><b>Atraparla porque la operación pesa más que el aviso</b>, en los caminos del dinero: un
 *       reintegro registrado que no se pudo comunicar es mejor que un reintegro sin constancia, y
 *       deshacer un despacho con la guía ya emitida y cobrada no lo desemite. Decidido en {@code
 *       adr/0044}, que es donde tenía que decidirse: es negocio, no programación.
 * </ol>
 *
 * <p><b>Lo que sigue abierto, y no lo cierra este puerto:</b> el envío ocurre <b>dentro</b> de la
 * transacción de quien llama y antes del commit, así que un fallo al comprometer deja al comprador
 * con un correo que dice "reintegramos el dinero de tu pedido" y al sistema sin ninguna constancia
 * de ese reintegro. Eso lo cierra una bandeja de salida —guardar el correo en la misma transacción
 * y mandarlo después, con reintentos—, que es un mecanismo entero y no una línea.
 */
public interface EnviadorDeCorreo {

  /**
   * @throws CorreoNoEnviadoException si el correo no salió. Nunca es opcional atenderla: si este
   *     caso de uso no tiene nada que hacer con ella, eso también es una decisión y va escrita.
   */
  void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml);
}
