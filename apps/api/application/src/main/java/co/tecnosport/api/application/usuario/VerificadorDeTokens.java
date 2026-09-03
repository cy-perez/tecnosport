package co.tecnosport.api.application.usuario;

import java.util.Optional;

/**
 * Puerto hacia la verificación del JWT de acceso — la contraparte de {@link GeneradorDeTokens},
 * para que quien intercepta cada request (el filtro de autenticación, en presentation) no necesite
 * conocer la librería JOSE directamente. {@code Optional.empty()} cuando el token es inválido,
 * vencido o está mal formado: todos esos casos son "no autenticado", no una excepción de negocio.
 */
public interface VerificadorDeTokens {

  Optional<ClaimsAcceso> verificar(String jwt);
}
