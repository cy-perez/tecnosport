package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Objects;

/**
 * Un bulto de la cotización: el paquete empacado y lo que vale lo que va dentro.
 *
 * <p>El valor declarado no es un adorno. La transportadora cobra el seguro sobre él y responde
 * hasta él si el paquete se pierde, y Skydropx lo exige en cada {@code parcel}: si no va, queda en
 * COP 2.500 por omisión — verificado contra la cuenta el 11 de septiembre de 2026. Perder un
 * celular declarado en 2.500 pesos es perderlo entero.
 *
 * <p>Va por bulto y no uno solo para toda la cotización porque un pedido viaja en un paquete por
 * variante (decisión del 11 de septiembre de 2026): cada uno declara lo suyo. Repetir el total del
 * pedido en cada bulto pagaría el seguro tantas veces como bultos haya.
 */
public record Bulto(Paquete paquete, Dinero valorDeclarado) {

  public Bulto {
    Objects.requireNonNull(paquete, "El paquete de un bulto no puede ser nulo.");
    Objects.requireNonNull(valorDeclarado, "El valor declarado de un bulto no puede ser nulo.");
  }
}
