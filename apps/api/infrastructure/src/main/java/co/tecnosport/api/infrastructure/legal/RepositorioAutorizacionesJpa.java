package co.tecnosport.api.infrastructure.legal;

import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.legal.OrigenAutorizacion;
import co.tecnosport.api.infrastructure.legal.entidad.AutorizacionDatosJpaEntity;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Sin transacción propia: se guarda dentro de la que ya abrió el caso de uso que la produce
 * (registro o creación de pedido), a propósito. Una constancia comprometida junto a un registro que
 * después se revierte diría que alguien autorizó algo que nunca ocurrió; y al revés, un pedido sin
 * su constancia es exactamente lo que la Ley 1581 no permite. Las dos escrituras viven o mueren
 * juntas — mismo criterio que RepositorioPedidosJpa con su reserva de inventario.
 */
@Component
public class RepositorioAutorizacionesJpa implements RepositorioAutorizaciones {

  private final AutorizacionDatosJpaRepository autorizaciones;

  public RepositorioAutorizacionesJpa(AutorizacionDatosJpaRepository autorizaciones) {
    this.autorizaciones = Objects.requireNonNull(autorizaciones);
  }

  @Override
  public void guardar(AutorizacionDatos autorizacion) {
    autorizaciones.save(aEntidad(autorizacion));
  }

  @Override
  public List<AutorizacionDatos> buscarPorCorreo(CorreoElectronico correo) {
    return autorizaciones.findByCorreoOrderByOtorgadaEnDesc(correo.valor()).stream()
        .map(this::aDominio)
        .toList();
  }

  private AutorizacionDatos aDominio(AutorizacionDatosJpaEntity entidad) {
    return new AutorizacionDatos(
        entidad.getId(),
        new CorreoElectronico(entidad.getCorreo()),
        entidad.getUsuarioId(),
        entidad.getVersionPolitica(),
        entidad.getDireccionIp(),
        OrigenAutorizacion.valueOf(entidad.getOrigen()),
        entidad.getOtorgadaEn());
  }

  private AutorizacionDatosJpaEntity aEntidad(AutorizacionDatos autorizacion) {
    return new AutorizacionDatosJpaEntity(
        autorizacion.id(),
        autorizacion.usuarioId().orElse(null),
        autorizacion.correo().valor(),
        autorizacion.versionPolitica(),
        autorizacion.direccionIp(),
        autorizacion.origen().name(),
        autorizacion.otorgadaEn());
  }
}
