package co.tecnosport.api.domain.legal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutorizacionDatosTest {

  private static final Instant AHORA = Instant.parse("2026-09-07T12:00:00Z");
  private static final CorreoElectronico CORREO = new CorreoElectronico("comprador@ejemplo.com");
  private static final String VERSION = "2026-09-07";
  private static final String IP = "190.24.10.5";

  @Test
  void enRegistroGuardaLaConstanciaCompleta() {
    UUID usuarioId = UUID.randomUUID();

    AutorizacionDatos autorizacion =
        AutorizacionDatos.enRegistro(true, CORREO, usuarioId, VERSION, IP, AHORA);

    assertEquals(CORREO, autorizacion.correo());
    assertEquals(Optional.of(usuarioId), autorizacion.usuarioId());
    assertEquals(VERSION, autorizacion.versionPolitica());
    assertEquals(IP, autorizacion.direccionIp());
    assertEquals(OrigenAutorizacion.REGISTRO, autorizacion.origen());
    assertEquals(AHORA, autorizacion.otorgadaEn());
  }

  @Test
  void enCheckoutSeCompraSinCuentaYLaConstanciaIgualVale() {
    AutorizacionDatos autorizacion =
        AutorizacionDatos.enCheckout(true, CORREO, null, VERSION, IP, AHORA);

    assertEquals(Optional.empty(), autorizacion.usuarioId());
    assertEquals(OrigenAutorizacion.CHECKOUT, autorizacion.origen());
  }

  @Test
  void enCheckoutConCuentaLaAsocia() {
    UUID usuarioId = UUID.randomUUID();

    AutorizacionDatos autorizacion =
        AutorizacionDatos.enCheckout(true, CORREO, usuarioId, VERSION, IP, AHORA);

    assertEquals(Optional.of(usuarioId), autorizacion.usuarioId());
  }

  @Test
  void sinAutorizarNoSeConstruyeLaConstanciaEnElRegistro() {
    assertThrows(
        AutorizacionRequeridaException.class,
        () -> AutorizacionDatos.enRegistro(false, CORREO, UUID.randomUUID(), VERSION, IP, AHORA));
  }

  @Test
  void sinAutorizarNoSeConstruyeLaConstanciaEnElCheckout() {
    assertThrows(
        AutorizacionRequeridaException.class,
        () -> AutorizacionDatos.enCheckout(false, CORREO, null, VERSION, IP, AHORA));
  }

  @Test
  void elRegistroSiempreExigeUsuario() {
    assertThrows(
        NullPointerException.class,
        () -> AutorizacionDatos.enRegistro(true, CORREO, null, VERSION, IP, AHORA));
  }

  /**
   * La versión ausente es un servidor mal configurado, no una autorización faltante: si lanzara
   * {@link AutorizacionRequeridaException} el comprador vería "hay que autorizar" después de haber
   * autorizado, y nadie encontraría nunca la variable de entorno vacía.
   */
  @Test
  void sinVersionDePoliticaNoHayConstanciaYNoSeConfundeConNoAutorizar() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () -> AutorizacionDatos.enCheckout(true, CORREO, null, "  ", IP, AHORA));

    assertTrue(error.getMessage().contains("versión"));
    assertNotEquals(AutorizacionRequeridaException.class, error.getClass());
  }

  /**
   * La IP es metadato de auditoría: que falte no puede tumbar una compra. Se guarda que no la hubo,
   * en vez de reventar.
   */
  @Test
  void sinIpLaConstanciaSeGuardaIgualComoDesconocida() {
    AutorizacionDatos sinIp =
        AutorizacionDatos.enCheckout(true, CORREO, null, VERSION, null, AHORA);
    AutorizacionDatos ipEnBlanco =
        AutorizacionDatos.enCheckout(true, CORREO, null, VERSION, "   ", AHORA);

    assertEquals("desconocida", sinIp.direccionIp());
    assertEquals("desconocida", ipEnBlanco.direccionIp());
  }

  @Test
  void cadaConstanciaTieneSuPropioId() {
    AutorizacionDatos una = AutorizacionDatos.enCheckout(true, CORREO, null, VERSION, IP, AHORA);
    AutorizacionDatos otra = AutorizacionDatos.enCheckout(true, CORREO, null, VERSION, IP, AHORA);

    assertNotEquals(una.id(), otra.id());
  }
}
