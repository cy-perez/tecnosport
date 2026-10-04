package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RangoDeTallasTest {

  /** El caso que trajo el negocio el 4 de octubre de 2026. */
  @Test
  void treintaALaTreintaYSeisEsDeDosEnDos() {
    assertEquals(
        Optional.of(List.of("30", "32", "34", "36")),
        RangoDeTallas.deDosEnDos("Jean clásico 💲85\nTallas 30 a la 36"));
  }

  @Test
  void entiendeLasFormasEnQueSeEscribeElRango() {
    List<String> esperado = List.of("28", "30", "32", "34");
    assertEquals(Optional.of(esperado), RangoDeTallas.deDosEnDos("*TALLAS 28 AL 34*"));
    assertEquals(Optional.of(esperado), RangoDeTallas.deDosEnDos("talla: 28-34"));
    assertEquals(Optional.of(esperado), RangoDeTallas.deDosEnDos("Tallas 28 – 34"));
    assertEquals(Optional.of(esperado), RangoDeTallas.deDosEnDos("Tallas desde la 28 hasta la 34"));
    assertEquals(
        Optional.of(List.of("6", "8", "10", "12", "14")),
        RangoDeTallas.deDosEnDos("tallas del 6 al 14"));
  }

  /** Impar con impar también se cuenta: lo que no se adivina es un rango de distinta paridad. */
  @Test
  void respetaElPrimerExtremo() {
    assertEquals(
        Optional.of(List.of("29", "31", "33", "35")), RangoDeTallas.deDosEnDos("Tallas 29 a 35"));
  }

  @Test
  void seAbstieneCuandoHabriaQueAdivinar() {
    assertEquals(Optional.empty(), RangoDeTallas.deDosEnDos("Tallas 30 a 35"));
    assertEquals(Optional.empty(), RangoDeTallas.deDosEnDos("Tallas 36 a 30"));
    assertEquals(Optional.empty(), RangoDeTallas.deDosEnDos("Llega de 2 a 4 días"));
    assertEquals(Optional.empty(), RangoDeTallas.deDosEnDos("Tallas S, M, L"));
    assertEquals(Optional.empty(), RangoDeTallas.deDosEnDos("Tallas 2 a 60"));
    assertEquals(
        Optional.empty(), RangoDeTallas.deDosEnDos("Jean tallas 28 a 34. Short tallas 6 a 12"));
    assertEquals(Optional.empty(), RangoDeTallas.deDosEnDos(null));
  }
}
