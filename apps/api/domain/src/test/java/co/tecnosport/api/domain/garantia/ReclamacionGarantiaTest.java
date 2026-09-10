package co.tecnosport.api.domain.garantia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReclamacionGarantiaTest {

  private static final Instant ENTREGA =
      ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

  private static Instant enFecha(int anio, int mes, int dia) {
    return ZonedDateTime.of(anio, mes, dia, 10, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();
  }

  private ReclamacionGarantia reclamacion(Instant radicadaEn, Integer meses) {
    return ReclamacionGarantia.radicar(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        ENTREGA,
        radicadaEn,
        meses,
        "La suela se despego a la semana");
  }

  @Test
  void dentroDelTerminoQuedaCubierta() {
    assertEquals(VigenciaGarantia.CUBIERTA, reclamacion(enFecha(2026, 12, 1), 12).vigencia());
  }

  @Test
  void pasadoElTerminoQuedaFueraDeTermino() {
    assertEquals(
        VigenciaGarantia.FUERA_DE_TERMINO, reclamacion(enFecha(2027, 2, 1), 12).vigencia());
  }

  /**
   * El limite exacto por los dos lados: el ultimo dia amparado y el primero que ya no. Sin las dos
   * afirmaciones, un mutante que cuente el mes de la entrega o que sume un dia de mas pasa.
   */
  @Test
  void elUltimoDiaDelTerminoTodaviaCuentaYElSiguienteNo() {
    assertEquals(VigenciaGarantia.CUBIERTA, reclamacion(enFecha(2027, 1, 15), 12).vigencia());
    assertEquals(
        VigenciaGarantia.FUERA_DE_TERMINO, reclamacion(enFecha(2027, 1, 16), 12).vigencia());
  }

  /**
   * Sin el termino de la categoria no se afirma nada: decir FUERA_DE_TERMINO ahi seria negarle un
   * derecho a alguien que quiza lo tiene. Ninguna categoria del catalogo esta en ese estado hoy
   * —los celulares salieron cuando se verifico que su termino es el ano legal—, pero el mecanismo
   * se queda: es la unica respuesta honesta el dia que aparezca una que si lo necesite.
   */
  @Test
  void sinTerminoConocidoLaVigenciaEsIndeterminada() {
    ReclamacionGarantia sinTermino = reclamacion(enFecha(2030, 1, 1), null);

    assertEquals(VigenciaGarantia.INDETERMINADA, sinTermino.vigencia());
    assertTrue(sinTermino.finDelTermino().isEmpty());
  }

  /**
   * La vigencia se congela contra la fecha de radicacion: una reclamacion presentada a tiempo no
   * deja de estarlo porque el negocio tarde en atenderla.
   */
  @Test
  void laVigenciaNoSeMueveAunqueElNegocioTardeEnResolver() {
    ReclamacionGarantia aTiempo = reclamacion(enFecha(2027, 1, 10), 12);

    aTiempo.resolver(DesenlaceGarantia.REPARACION, null, enFecha(2027, 6, 1), "admin:1");

    assertEquals(VigenciaGarantia.CUBIERTA, aTiempo.vigencia());
  }

  @Test
  void resolverConReintegroExigeLaConstancia() {
    ReclamacionGarantia sinConstancia = reclamacion(enFecha(2026, 6, 1), 12);

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            sinConstancia.resolver(
                DesenlaceGarantia.REINTEGRO, null, enFecha(2026, 6, 2), "admin:1"));
    assertEquals(EstadoReclamacionGarantia.RADICADA, sinConstancia.estado());
  }

  @Test
  void unaReparacionNoApuntaAUnaConstanciaDeDinero() {
    ReclamacionGarantia reclamacion = reclamacion(enFecha(2026, 6, 1), 12);

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            reclamacion.resolver(
                DesenlaceGarantia.REPARACION, UUID.randomUUID(), enFecha(2026, 6, 2), "admin:1"));
  }

  /** Reparar y reponer existen igual que devolver el dinero: son las tres salidas de la ley. */
  @Test
  void lasTresSalidasSeRegistran() {
    for (DesenlaceGarantia desenlace : DesenlaceGarantia.values()) {
      ReclamacionGarantia reclamacion = reclamacion(enFecha(2026, 6, 1), 12);
      UUID constancia = desenlace == DesenlaceGarantia.REINTEGRO ? UUID.randomUUID() : null;

      reclamacion.resolver(desenlace, constancia, enFecha(2026, 6, 2), "admin:1");

      assertEquals(desenlace, reclamacion.desenlace().orElseThrow());
      assertEquals(EstadoReclamacionGarantia.RESUELTA, reclamacion.estado());
    }
  }

  @Test
  void noSeResuelveDosVeces() {
    ReclamacionGarantia reclamacion = reclamacion(enFecha(2026, 6, 1), 12);
    reclamacion.resolver(DesenlaceGarantia.REPOSICION, null, enFecha(2026, 6, 2), "admin:1");

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            reclamacion.resolver(
                DesenlaceGarantia.REPARACION, null, enFecha(2026, 6, 3), "admin:1"));
  }

  @Test
  void unaReclamacionSinDescripcionDelFalloNoSePuedeAtender() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            ReclamacionGarantia.radicar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ENTREGA,
                enFecha(2026, 6, 1),
                12,
                "   "));
  }

  /** La categoria pendiente no cae al termino por defecto: eso seria inventar el dato. */
  @Test
  void unaCategoriaSinTerminoConocidoNoCaeAlPorDefecto() {
    TerminosDeGarantia terminos =
        TerminosDeGarantia.de(12, Map.of("calzado", 6), Set.of("linea-por-decidir"));

    assertTrue(terminos.mesesPara("linea-por-decidir").isEmpty());
    assertEquals(6, terminos.mesesPara("calzado").orElseThrow());
    assertEquals(12, terminos.mesesPara("ropa-deportiva").orElseThrow());
  }

  /**
   * La promesa que se acaba de publicar: un celular nuevo tiene el mismo ano legal que cualquier
   * otro producto nuevo. Con la categoria fuera de la lista de pendientes, un celular reclamado
   * dentro del ano queda CUBIERTA y no INDETERMINADA, que es lo que respondia antes.
   */
  @Test
  void unCelularNuevoTieneElAnoLegalComoCualquierProductoNuevo() {
    TerminosDeGarantia terminos = TerminosDeGarantia.de(12, Map.of(), Set.of());

    assertEquals(12, terminos.mesesPara("celulares").orElseThrow());
    assertEquals(
        VigenciaGarantia.CUBIERTA,
        reclamacion(enFecha(2026, 12, 1), terminos.mesesPara("celulares").orElseThrow())
            .vigencia());
  }

  /** Y si un fabricante anuncia mas, manda el mayor: eso es lo que la ley obliga a respetar. */
  @Test
  void unTerminoAnunciadoMayorPorElProductorManda() {
    TerminosDeGarantia terminos = TerminosDeGarantia.de(12, Map.of("celulares", 24), Set.of());

    assertEquals(24, terminos.mesesPara("celulares").orElseThrow());
  }

  @Test
  void unaCategoriaNoPuedeTenerTerminoYEstarPendienteALaVez() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> TerminosDeGarantia.de(12, Map.of("celulares", 24), Set.of("celulares")));
  }
}
