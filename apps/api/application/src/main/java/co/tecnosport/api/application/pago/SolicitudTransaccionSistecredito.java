package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.DocumentoIdentidad;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.Objects;

/**
 * Lo que hay que mandarle a Sistecrédito para que cree una transacción (guía {@code G-ALI-10}).
 *
 * <p>La {@code referencia} viaja como {@code invoice}: la pasarela impide crear una transacción
 * nueva para una factura que ya tenga otra activa o aprobada (error {@code 738}), y nuestra
 * referencia —{@code TS-2026-000123-1}, número de pedido más número de intento— ya es única por
 * intento, así que el comportamiento coincide sin tener que pedirle a la pasarela que invente la
 * suya.
 *
 * <p>{@code sandbox} no es un ambiente: es un campo del cuerpo que le pide a la pasarela simular la
 * respuesta del medio de pago sin ir a pedir un crédito real. Como esta integración <b>solo tiene
 * credenciales productivas</b>, este booleano es lo único que separa una prueba de un crédito a
 * nombre de una persona ({@code adr/0048}), y por eso entra explícito en cada solicitud en vez de
 * quedarse como un ajuste global del cliente: quien arma la solicitud tiene que decidirlo mirando.
 *
 * <p>Las dos URL son absolutas y públicas: {@code urlRespuesta} es a donde vuelve el comprador, y
 * {@code urlConfirmacion} es a donde la pasarela manda las notificaciones. En local no hay ninguna
 * que sirva — por eso las pruebas de verdad van contra el despliegue de dev.
 */
public record SolicitudTransaccionSistecredito(
    ReferenciaPago referencia,
    String descripcion,
    Dinero monto,
    DocumentoIdentidad documento,
    String urlRespuesta,
    String urlConfirmacion,
    boolean sandbox,
    String estadoSimulado) {

  public SolicitudTransaccionSistecredito {
    Objects.requireNonNull(referencia, "La referencia no puede ser nula.");
    Objects.requireNonNull(monto, "El monto no puede ser nulo.");
    Objects.requireNonNull(documento, "El documento del comprador no puede ser nulo.");
    if (descripcion == null || descripcion.isBlank()) {
      throw new IllegalArgumentException("La descripción no puede estar vacía.");
    }
    if (urlRespuesta == null || urlRespuesta.isBlank()) {
      throw new IllegalArgumentException("La URL de respuesta no puede estar vacía.");
    }
    if (urlConfirmacion == null || urlConfirmacion.isBlank()) {
      throw new IllegalArgumentException("La URL de confirmación no puede estar vacía.");
    }
    if (sandbox && (estadoSimulado == null || estadoSimulado.isBlank())) {
      throw new IllegalArgumentException(
          "Una solicitud en modo sandbox tiene que decir qué estado simula.");
    }
  }
}
