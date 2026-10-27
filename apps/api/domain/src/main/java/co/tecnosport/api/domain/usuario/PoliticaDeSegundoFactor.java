package co.tecnosport.api.domain.usuario;

import java.util.Objects;

/**
 * Si el panel exige segundo factor al administrador (ADR-0096, {@code ADMIN_EXIGE_SEGUNDO_FACTOR}).
 * El cliente nunca queda restringido: el segundo factor es del panel.
 *
 * <p>El alcance se recalcula en cada emisión de un token —inicio de sesión, refresco, cambio de
 * clave, activación—, así que encender la bandera alcanza también a las sesiones ya abiertas en
 * cuanto refrescan, a más tardar en quince minutos.
 */
public record PoliticaDeSegundoFactor(boolean exigidoAAdmin) {

  public boolean obligatorioPara(Usuario usuario) {
    Objects.requireNonNull(usuario, "El usuario no puede ser nulo.");
    return exigidoAAdmin && usuario.rol() == Rol.ADMIN;
  }

  public AlcanceDeAcceso alcance(Usuario usuario, boolean segundoFactorActivo) {
    return obligatorioPara(usuario) && !segundoFactorActivo
        ? AlcanceDeAcceso.SOLO_ACTIVAR_SEGUNDO_FACTOR
        : AlcanceDeAcceso.COMPLETO;
  }
}
