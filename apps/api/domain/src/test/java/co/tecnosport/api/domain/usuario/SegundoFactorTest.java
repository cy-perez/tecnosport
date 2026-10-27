package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SegundoFactorTest {

  private static final Instant AHORA = Instant.parse("2026-10-27T12:00:00Z");
  private static final Duration VIGENCIA_PENDIENTE = Duration.ofMinutes(15);

  private static String codigoActual(SegundoFactor factor, Instant instante) {
    return Totp.codigoEn(factor.clave(), Totp.pasoDe(instante));
  }

  private static SegundoFactor pendiente() {
    return SegundoFactor.iniciar(UUID.randomUUID(), AHORA);
  }

  private static SegundoFactor activo(List<CodigoDeRespaldo> codigos) {
    SegundoFactor factor = pendiente();
    codigos.addAll(factor.confirmar(codigoActual(factor, AHORA), AHORA, VIGENCIA_PENDIENTE));
    return factor;
  }

  @Test
  void nacePendienteYConLaClaveYaGenerada() {
    SegundoFactor factor = pendiente();
    assertFalse(factor.activo());
    assertEquals(ClaveTotp.BYTES, factor.clave().bytes().length);
    assertEquals(0, factor.restantes());
  }

  @Test
  void confirmarConElCodigoDeLaAplicacionLoActivaYDevuelveDiezCodigosDistintos() {
    SegundoFactor factor = pendiente();

    List<CodigoDeRespaldo> codigos =
        factor.confirmar(codigoActual(factor, AHORA), AHORA, VIGENCIA_PENDIENTE);

    assertTrue(factor.activo());
    assertEquals(AHORA, factor.activadoEn().orElseThrow());
    assertEquals(Totp.pasoDe(AHORA), factor.ultimoPasoUsado().orElseThrow());
    assertEquals(SegundoFactor.CANTIDAD_DE_CODIGOS, codigos.size());
    assertEquals(SegundoFactor.CANTIDAD_DE_CODIGOS, new HashSet<>(codigos).size());
    assertEquals(SegundoFactor.CANTIDAD_DE_CODIGOS, factor.restantes());
    // Lo que se guarda son las huellas.
    for (CodigoDeRespaldo codigo : codigos) {
      assertTrue(
          factor.respaldos().stream().anyMatch(r -> r.huella().equals(codigo.huella())),
          "falta la huella");
    }
  }

  @Test
  void unCodigoEquivocadoNoLoActiva() {
    SegundoFactor factor = pendiente();
    String malo = codigoActual(factor, AHORA.plus(Duration.ofHours(1)));

    assertThrows(
        CodigoSegundoFactorInvalidoException.class,
        () -> factor.confirmar(malo, AHORA, VIGENCIA_PENDIENTE));
    assertFalse(factor.activo());
  }

  @Test
  void unaActivacionVencidaNoSeConfirma() {
    SegundoFactor factor = pendiente();
    Instant tarde = AHORA.plus(VIGENCIA_PENDIENTE);

    assertThrows(
        ActivacionSegundoFactorNoIniciadaException.class,
        () -> factor.confirmar(codigoActual(factor, tarde), tarde, VIGENCIA_PENDIENTE));
  }

  @Test
  void noSeConfirmaDosVeces() {
    SegundoFactor factor = activo(new java.util.ArrayList<>());
    Instant luego = AHORA.plusSeconds(60);

    assertThrows(
        SegundoFactorYaActivoException.class,
        () -> factor.confirmar(codigoActual(factor, luego), luego, VIGENCIA_PENDIENTE));
  }

  @Test
  void elCodigoDeLaActivacionNoSirveDespuesParaEntrar() {
    SegundoFactor factor = activo(new java.util.ArrayList<>());

    assertThrows(
        CodigoSegundoFactorInvalidoException.class,
        () -> factor.verificar(codigoActual(factor, AHORA), AHORA.plusSeconds(5)));
  }

  @Test
  void verificarConLaAplicacionAvanzaElUltimoPaso() {
    SegundoFactor factor = activo(new java.util.ArrayList<>());
    Instant luego = AHORA.plusSeconds(60);

    assertEquals(
        SegundoFactor.Verificacion.TOTP, factor.verificar(codigoActual(factor, luego), luego));
    assertEquals(Totp.pasoDe(luego), factor.ultimoPasoUsado().orElseThrow());
    assertThrows(
        CodigoSegundoFactorInvalidoException.class,
        () -> factor.verificar(codigoActual(factor, luego), luego));
  }

  @Test
  void unCodigoDeRespaldoSirveUnaSolaVezYComoSeTeclee() {
    List<CodigoDeRespaldo> codigos = new java.util.ArrayList<>();
    SegundoFactor factor = activo(codigos);
    String tecleado = codigos.get(3).mostrado().replace("-", " ").toLowerCase(Locale.ROOT);

    assertEquals(SegundoFactor.Verificacion.RESPALDO, factor.verificar(tecleado, AHORA));
    assertEquals(SegundoFactor.CANTIDAD_DE_CODIGOS - 1, factor.restantes());
    assertThrows(
        CodigoSegundoFactorInvalidoException.class,
        () -> factor.verificar(codigos.get(3).mostrado(), AHORA));
  }

  @Test
  void unCodigoDeRespaldoAjenoOBasuraNoVale() {
    SegundoFactor factor = activo(new java.util.ArrayList<>());

    assertThrows(
        CodigoSegundoFactorInvalidoException.class,
        () -> factor.verificar(CodigoDeRespaldo.generar().mostrado(), AHORA));
    assertThrows(CodigoSegundoFactorInvalidoException.class, () -> factor.verificar("hola", AHORA));
    assertThrows(CodigoSegundoFactorInvalidoException.class, () -> factor.verificar(null, AHORA));
    assertEquals(SegundoFactor.CANTIDAD_DE_CODIGOS, factor.restantes());
  }

  @Test
  void unoPendienteNoVerificaNada() {
    SegundoFactor factor = pendiente();

    assertThrows(
        SegundoFactorNoActivoException.class,
        () -> factor.verificar(codigoActual(factor, AHORA), AHORA));
    assertThrows(SegundoFactorNoActivoException.class, () -> factor.regenerarCodigos(AHORA));
  }

  @Test
  void regenerarInvalidaLosCodigosAnteriores() {
    List<CodigoDeRespaldo> viejos = new java.util.ArrayList<>();
    SegundoFactor factor = activo(viejos);
    factor.verificar(viejos.get(0).mostrado(), AHORA);

    List<CodigoDeRespaldo> nuevos = factor.regenerarCodigos(AHORA);

    assertEquals(SegundoFactor.CANTIDAD_DE_CODIGOS, factor.restantes());
    assertThrows(
        CodigoSegundoFactorInvalidoException.class,
        () -> factor.verificar(viejos.get(1).mostrado(), AHORA));
    assertEquals(
        SegundoFactor.Verificacion.RESPALDO, factor.verificar(nuevos.get(0).mostrado(), AHORA));
  }

  @Test
  void laClaveNoSeImprime() {
    SegundoFactor factor = pendiente();
    assertFalse(factor.clave().toString().contains(factor.clave().enBase32()));
    assertFalse(CodigoDeRespaldo.generar().toString().contains("-"));
  }

  @Test
  void elCodigoDeRespaldoSeMuestraEnCuatroGruposYSeLeeSinImportarLaForma() {
    CodigoDeRespaldo codigo = CodigoDeRespaldo.generar();
    assertTrue(codigo.mostrado().matches("[A-Z2-7]{4}-[A-Z2-7]{4}-[A-Z2-7]{4}-[A-Z2-7]{4}"));
    assertEquals(
        codigo, CodigoDeRespaldo.leer(codigo.mostrado().toLowerCase(Locale.ROOT)).orElseThrow());
    assertTrue(CodigoDeRespaldo.leer("ABCD-EFGH-IJKL-MNO").isEmpty());
    assertTrue(CodigoDeRespaldo.leer("ABCD-EFGH-IJKL-MNO1").isEmpty());
  }
}
