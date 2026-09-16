package co.tecnosport.api.infrastructure.envio.siembra;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.ResultadoCotizacion;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Un cotizador de escenario para los recorridos de Playwright. Hermano de {@code
 * SembradorCatalogo}: datos falsos que se encienden a mano y nunca en producción.
 *
 * <p><strong>Por qué existe.</strong> El recorrido de compra a domicilio no puede correr contra
 * Skydropx en integración continua: no hay credenciales, y {@code SkydropxClient} —que falla
 * cerrado por diseño (adr/0021)— responde sin tarifas. Con eso el checkout hace lo correcto
 * —ofrecer la recogida y no dejar confirmar— y el recorrido no llega nunca al pedido creado. Sin
 * este doble las salidas eran dos, las dos peores: comprar con retiro en punto y dejar sin probar
 * el camino que más plata mueve, o meter credenciales de un tercero como secreto del repositorio.
 *
 * <p><strong>Qué devuelve.</strong> Una sola tarifa, fija y deliberadamente sintética. El costo no
 * es un dato de negocio y no pretende parecerlo: {@code 12.345} no es una tarifa que ninguna
 * transportadora haya cobrado nunca, y ese es justo el punto — si este número aparece en una
 * pantalla real, se reconoce de inmediato. La tarifa real la cotiza Skydropx y nadie más.
 *
 * <p><strong>Por qué no se puede encender por accidente.</strong> Solo existe bajo el perfil {@code
 * e2e}, y bajo ese mismo perfil {@code ConfiguracionEnvio} deja de registrar el cliente real: los
 * dos beans no pueden convivir, así que no hay un orden de precedencia que alguien pueda entender
 * al revés. Producción no activa el perfil en ningún sitio — el único que lo activa es el flujo
 * {@code recorridos}.
 */
public final class CotizadorEnvioSembrado implements CotizadorEnvio {

  /**
   * Sintético a propósito (ver arriba). No se lee de configuración: un costo configurable invita a
   * apuntarlo a un valor creíble, y lo que hace seguro a este doble es justamente que su tarifa se
   * delate sola.
   */
  private static final Dinero COSTO = Dinero.deCop(12_345);

  /**
   * Lo que Skydropx da a sus tarifas, para que el escenario se parezca al real en lo que importa.
   */
  private static final Duration VIGENCIA = Duration.ofHours(24);

  private final Reloj reloj;

  public CotizadorEnvioSembrado(Reloj reloj) {
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
  }

  /**
   * Responde igual con recaudo y sin él: la tarifa admite contraentrega, así que el recorrido puede
   * probar los dos métodos de pago sin que el doble tenga que decidir nada. Dejar caer el recaudo
   * aquí probaría el camino de {@code ContraentregaNoDisponibleException}, que es otro escenario y
   * merece su propia prueba, no un doble que se comporta de dos maneras.
   */
  @Override
  public ResultadoCotizacion cotizar(CotizacionEnvio cotizacion) {
    Objects.requireNonNull(cotizacion, "La cotización no puede ser nula.");
    return new ResultadoCotizacion.ConTarifas(
        List.of(
            new TarifaEnvio(
                "tarifa-de-escenario",
                "Transportadora de escenario",
                "Estándar",
                COSTO,
                3,
                true,
                reloj.ahora().plus(VIGENCIA))));
  }
}
