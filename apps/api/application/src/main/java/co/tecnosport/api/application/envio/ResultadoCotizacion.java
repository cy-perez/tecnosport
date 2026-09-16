package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.TarifaEnvio;
import java.util.List;

/**
 * Qué pasó al pedirle tarifas al proveedor. Tres respuestas, y la distinción tiene consecuencias
 * para quien compra.
 *
 * <p><strong>Esto era una lista vacía, y agrupaba a propósito los tres casos</strong> (adr/0021):
 * el proveedor no respondió, el destino no tiene cobertura, o nadie devolvió tarifa. El argumento
 * era que el checkout hace lo mismo en todos —ofrecer la recogida en el punto—, y para el checkout
 * es cierto. Para el comprador no: "a esta dirección hoy no llega nadie" le pide cambiar la
 * dirección, y "no pudimos cotizar ahora" le pide reintentar. Mandarlo a corregir una dirección que
 * estaba bien es el peor de los dos errores.
 *
 * <p><strong>Y no es teórico, se midió el 16 de septiembre de 2026</strong> (docs/13 §6.9): la
 * primera cotización de un contenido nuevo se pasó de la ventana de sondeo y respondió "sin
 * cobertura" para Medellín, donde sí hay tres transportadoras; el reintento la trajo en 1,6 s, por
 * la deduplicación por contenido de Skydropx. O sea que el caso frecuente de esta confusión es
 * justo el que se arregla solo reintentando.
 *
 * <p>Lo que <strong>no</strong> cambia es el criterio <em>fail-closed</em> de adr/0021: sin tarifa
 * no se inventa un flete de respaldo por ninguno de los tres caminos. Cobrar uno inventado es
 * despachar a pérdida o cobrarle de más al comprador, y las dos son peores que no vender.
 */
public sealed interface ResultadoCotizacion {

  /** El proveedor respondió y hay tarifas. Nunca vacía: sin tarifas es {@link SinCobertura}. */
  record ConTarifas(List<TarifaEnvio> tarifas) implements ResultadoCotizacion {

    public ConTarifas {
      if (tarifas == null || tarifas.isEmpty()) {
        throw new IllegalArgumentException("Un resultado con tarifas exige al menos una.");
      }
      tarifas = List.copyOf(tarifas);
    }
  }

  /**
   * El proveedor respondió, completó la cotización, y no hay ninguna tarifa para ese destino. Es un
   * caso de negocio y no una falla: hay ciudades que hoy no se pueden despachar.
   */
  record SinCobertura() implements ResultadoCotizacion {}

  /**
   * No se pudo saber si hay cobertura. Es temporal por definición: la misma pregunta, repetida,
   * puede responderse.
   */
  record NoSePudoCotizar(Motivo motivo) implements ResultadoCotizacion {

    public NoSePudoCotizar {
      if (motivo == null) {
        throw new IllegalArgumentException("Un fallo de cotización exige decir cuál fue.");
      }
    }
  }

  /**
   * Para el registro, no para el comprador: a quien compra se le dice lo mismo en los cuatro —"no
   * pudimos cotizar, intenta de nuevo"— y quien opera necesita saber cuál fue, porque piden cosas
   * distintas. Sin credenciales es un despliegue mal configurado; el sondeo agotado es una
   * cotización que sigue viva del otro lado; una respuesta inesperada es la forma del proveedor
   * cambiando bajo nuestros pies.
   */
  enum Motivo {
    SIN_CREDENCIALES,
    PROVEEDOR_NO_DISPONIBLE,
    RESPUESTA_INESPERADA,
    SONDEO_AGOTADO
  }
}
