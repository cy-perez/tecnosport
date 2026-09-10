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
 * <p><b>Un envío que falla no cancela nada, y durante cuatro fases este proyecto creyó lo
 * contrario.</b> Cinco casos de uso llevan escrito que "si el correo falla, la operación tampoco se
 * guarda", y es falso: el único adaptador de producción, {@code EnviadorDeCorreoSpringMail},
 * registra el fallo y se lo traga sin relanzarlo. Lo hace por una razón buena y también escrita
 * —{@code SolicitarRecuperacion} responde 204 exista o no la cuenta, y un 500 solo cuando la cuenta
 * sí existe sería justo el oráculo de enumeración que ese diseño evita—, así que el defecto no es
 * el adaptador: es que la promesa se escribió en el otro extremo sin comprobar este.
 *
 * <p>Lo encontró una revisión adversarial, y conviene saber por qué ninguna prueba lo destapó: los
 * dobles de prueba <b>sí</b> lanzan ({@code EnviadorDeCorreoFalso.hazQueFalle()}), así que las
 * pruebas que afirman "un correo caído no deja la solicitud guardada a medias" comprueban un
 * escenario que el adaptador de producción no puede producir. Siguen valiendo para lo que fijan
 * —qué hace el caso de uso si el puerto lanza— pero no demuestran la garantía.
 *
 * <p>Lo que de verdad ocurre hoy, dicho sin adornos: si el correo no sale, la operación queda
 * comprometida igual y el comprador no se entera. En los caminos del dinero eso significa un
 * reintegro registrado que nadie le comunicó. La salida es una bandeja de salida —guardar el correo
 * en la misma transacción y mandarlo después, con reintentos—, que es un mecanismo entero y no una
 * línea; mientras no exista, el registro de error del adaptador es la única señal, y hay que
 * mirarlo. Decidir si además el envío debería poder tumbar la transacción en los caminos del dinero
 * es una decisión de negocio, no de programación, y no está tomada.
 *
 * <p>El otro sentido, que tampoco estaba dicho: el envío ocurre <b>dentro</b> de la transacción de
 * quien llama y antes del commit, así que un fallo al comprometer deja al comprador con un correo
 * que dice "reintegramos el dinero de tu pedido" y al sistema sin ninguna constancia de ese
 * reintegro. La misma bandeja de salida lo cerraría.
 */
public interface EnviadorDeCorreo {

  void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml);
}
