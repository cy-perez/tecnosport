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
 * <p><b>El envío ocurre dentro de la transacción de quien llama, y eso tiene dos caras.</b> La que
 * este proyecto eligió a propósito está escrita en cada caso de uso: si el correo falla, la
 * operación tampoco se guarda, porque dejar constancia y callar que el aviso no salió es el reclamo
 * que la constancia venía a evitar.
 *
 * <p>La otra cara nunca se había dicho, y conviene que quede escrita: el correo sale <b>antes</b>
 * del commit, así que un fallo al comprometer deja al comprador con un correo que dice
 * "reintegramos el dinero de tu pedido" y al sistema sin ninguna constancia de ese reintegro —
 * exactamente el estado inverso al que se quería evitar. Se acepta con los ojos abiertos: el orden
 * contrario exigiría una bandeja de salida (registrar el correo en la misma transacción y mandarlo
 * después, reintentando), que es un mecanismo entero y no una línea, y hoy no hay volumen que lo
 * justifique. El día que lo haya, esta es la nota que dice por dónde empezar.
 */
public interface EnviadorDeCorreo {

  void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml);
}
