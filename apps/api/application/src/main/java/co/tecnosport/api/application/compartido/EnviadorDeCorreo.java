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
 * <p><b>Esto no manda un correo: lo encola</b> (adr/0045). El adaptador de producción escribe una
 * fila en la bandeja de salida <b>uniéndose a la transacción de quien llama</b>, y {@link
 * DrenarBandejaDeSalida} la manda después, con reintentos. Quien quiera mandar un correo de verdad,
 * aquí y ahora, tiene que pedir {@link TransporteDeCorreo} por su nombre — y no debería quererlo.
 *
 * <p><b>Por qué esto es así, en una historia de tres pasos</b>, porque explica el estado del código
 * que la rodea y evita que alguien "simplifique" hacia atrás:
 *
 * <ol>
 *   <li>Durante cuatro fases el único adaptador de producción registraba el fallo de SMTP y <b>se
 *       lo tragaba</b>, de modo que los doce llamadores tenían la decisión tomada por ellos y
 *       ninguno podía enterarse. Cinco casos de uso llevaban escrito que "si el correo falla, la
 *       operación tampoco se guarda" y era falso.
 *   <li>{@code adr/0044} lo puso a lanzar y devolvió la decisión a cada caso de uso. Correcto, y
 *       sin embargo insuficiente: el correo seguía saliendo <b>dentro</b> de la transacción y antes
 *       del commit, así que un fallo al comprometer dejaba a quien compró leyendo "reintegramos el
 *       dinero de tu pedido" y al sistema sin constancia del reintegro. Ese sentido no lo arregla
 *       ningún {@code catch}, y el propio ADR lo dejó anotado.
 *   <li>{@code adr/0045} invierte el orden, y al hacerlo se lleva por delante la pregunta entera:
 *       <b>si el correo se encola con la transacción, ya no hay ninguna decisión que tomar en el
 *       sitio de la llamada</b>. Reintentar es de la bandeja.
 * </ol>
 *
 * <p><b>Los {@code catch} de los llamadores siguen ahí y ya no protegen lo que decían.</b> Están
 * reescritos uno por uno, y lo que hoy atrapan es un fallo de base de datos al encolar — caso en el
 * que la transacción de quien llama está condenada de todas formas. Se dejaron puestos porque
 * quitarlos obligaría a que un error de escritura se propagara distinto en trece sitios; lo que no
 * se dejó fue el comentario viejo, que afirmaba una protección inexistente.
 */
public interface EnviadorDeCorreo {

  /**
   * Deja el correo en la bandeja de salida.
   *
   * @throws CorreoNoEnviadoException si ni siquiera se pudo encolar, que en la práctica es un fallo
   *     de base de datos. Un SMTP caído ya no llega hasta aquí.
   */
  void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml);
}
