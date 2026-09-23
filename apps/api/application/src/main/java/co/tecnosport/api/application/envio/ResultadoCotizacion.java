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
   * No se pudo saber si hay cobertura. Cuatro de los cinco motivos son temporales —la misma
   * pregunta, repetida, puede responderse—, y {@link Motivo#DATOS_RECHAZADOS} no: ahí el proveedor
   * ya contestó, y contestó que el cuerpo está mal.
   */
  record NoSePudoCotizar(Motivo motivo) implements ResultadoCotizacion {

    public NoSePudoCotizar {
      if (motivo == null) {
        throw new IllegalArgumentException("Un fallo de cotización exige decir cuál fue.");
      }
    }
  }

  /**
   * Cuatro de los cinco son para el registro y no para el comprador: a quien compra se le dice lo
   * mismo —"no pudimos cotizar, intenta de nuevo"— y quien opera necesita saber cuál fue, porque
   * piden cosas distintas. Sin credenciales es un despliegue mal configurado; el sondeo agotado es
   * una cotización que sigue viva del otro lado; una respuesta inesperada es la forma del proveedor
   * cambiando bajo nuestros pies.
   *
   * <p><strong>{@link #DATOS_RECHAZADOS} es el que rompe esa regla</strong>, y es la razón de que
   * exista: el proveedor respondió, y respondió que <em>nuestro</em> cuerpo está mal. Llamar a eso
   * "no disponible" era falso por dos lados —culpaba al que sí contestó— y le pedía al comprador
   * reintentar algo que no puede funcionar, porque Skydropx deduplica las cotizaciones por
   * contenido y el reintento trae el mismo rechazo. Es la forma exacta que tenía el valor declarado
   * por debajo del mínimo antes de adr/0035: una venta que no ocurre y ningún error que la
   * explique.
   *
   * <p>No entra aquí todo lo que el proveedor conteste con un {@code 4xx}. Un {@code 401} es
   * despliegue mal configurado y un {@code 429} es el límite de dos peticiones por segundo, que sí
   * se arregla reintentando; el mapeo vive en el adaptador, que es quien ve el código.
   *
   * <p><strong>{@code TODO (depende de Skydropx)}: si el rechazo del código postal merece su propio
   * motivo.</strong> Los 74 municipios de docs/13 §6.18 caen hoy en {@link #DATOS_RECHAZADOS}, y
   * para ellos la frase de arriba es falsa: el cuerpo está bien y el que no conoce el municipio es
   * el proveedor —responde {@code 422 postal_code: "no existe"}, que no es {@code no_coverage}—. El
   * comprador ya ve lo correcto, un 409 y la recogida en el punto; lo que se cobra es el registro,
   * donde cada compra desde esos municipios escribe un {@code error} que culpa a nuestro cuerpo, y
   * un canal de error con ruido conocido deja de servir para lo que se creó (adr/0035).
   *
   * <p>Queda pendiente a propósito, y no por falta de tiempo: se le pidió a Skydropx su catálogo de
   * códigos el 23 de septiembre de 2026 (docs/tramites/2026-09-19-skydropx.md), y cruzarlo contra
   * DIVIPOLA dirá si son esos 74, si son otros y si la lista es estable. Decidirlo antes sería
   * escribir una regla sobre una medición nuestra en vez de sobre su catálogo. Hay además una
   * precondición de este lado: {@code Direccion} no valida el código DANE —solo exige que no esté
   * vacío— y un código postal de seis dígitos devuelve ese mismo {@code 422} (docs/13 §6), así que
   * hasta que validemos la forma el mensaje es ambiguo y el motivo nuevo sería una inferencia.
   *
   * <p><strong>Cómo saber si esta nota ya caducó</strong>: si docs/13 §6.18 trae el resultado del
   * cruce, sobra.
   */
  enum Motivo {
    SIN_CREDENCIALES,
    DATOS_RECHAZADOS,
    PROVEEDOR_NO_DISPONIBLE,
    RESPUESTA_INESPERADA,
    SONDEO_AGOTADO
  }
}
