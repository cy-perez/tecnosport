package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PatronDeReplicaTest {

  /** Los dos mensajes que trajo el negocio el 3 de octubre de 2026. */
  @Test
  void reconoceLaMarcaUnoPuntoUno() {
    assertTrue(PatronDeReplica.esReplica("*NUEVA COLECCIÓN 1.1* *SUPERDRY*"));
    assertTrue(PatronDeReplica.esReplica("*NUEVA POLO 1.1🍯* *MARCA P U M A BMW*"));
    assertTrue(PatronDeReplica.esReplica("Producto importado 1:1"));
  }

  @Test
  void unPrecioOUnaMedidaNoSonLaMarca() {
    assertFalse(PatronDeReplica.esReplica("💰 1.100.000"));
    assertFalse(PatronDeReplica.esReplica("Talla 11"));
    assertFalse(PatronDeReplica.esReplica("Medida 21.1 cm"));
    assertFalse(PatronDeReplica.esReplica("Relación 1.15"));
    assertFalse(PatronDeReplica.esReplica("Bolso de dama 💰 53.000"));
    assertFalse(PatronDeReplica.esReplica(null));
  }
}
