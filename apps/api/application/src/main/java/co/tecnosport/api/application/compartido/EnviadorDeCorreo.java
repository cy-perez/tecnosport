package co.tecnosport.api.application.compartido;

import co.tecnosport.api.domain.compartido.CorreoElectronico;

/**
 * Correo saliente transaccional (docs/08-seguridad-legal.md: verificación de correo al registrarse,
 * recuperación de contraseña). Puerto técnico, no de negocio — mismo criterio que {@link Reloj} y
 * {@link RepositorioIdempotencia}: application es la única capa entre presentation e
 * infrastructure.
 *
 * <p>El puerto no sabe nada de verificación ni de recuperación: asunto y cuerpo los arma el caso de
 * uso que envía (por ejemplo, {@code RegistrarUsuario}), con el enlace y el texto que le
 * corresponden. Esto se mantiene genérico a propósito, igual que {@code RepositorioPedidos} no sabe
 * qué es un pedido de transferencia manual — separa "cómo se manda un correo" de "qué correo hay
 * que mandar".
 */
public interface EnviadorDeCorreo {

  void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml);
}
