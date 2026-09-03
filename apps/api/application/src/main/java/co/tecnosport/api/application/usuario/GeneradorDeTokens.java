package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Instant;

/**
 * Puerto hacia la firma del JWT de acceso (docs/08-seguridad-legal.md: 15 minutos de vigencia). La
 * vigencia no es parámetro de este método: vive en la configuración de la implementación de
 * infrastructure, igual que el secreto de integridad de Wompi en {@code WompiClient}.
 */
public interface GeneradorDeTokens {

  String generarAcceso(Usuario usuario, Instant ahora);
}
