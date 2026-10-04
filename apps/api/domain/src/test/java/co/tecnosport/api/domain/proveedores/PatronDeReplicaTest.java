package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PatronDeReplicaTest {

  /** Los dos mensajes que trajo el negocio el 3 de octubre de 2026. */
  @Test
  void reconoceLaMarcaUnoPuntoUno() {
    assertTrue(PatronDeReplica.esReplica("*NUEVA COLECCIÓN 1.1* *SUPERDRY*"));
    assertTrue(PatronDeReplica.esReplica("*NUEVA POLO 1.1🍯* *MARCA P U M A BMW*"));
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

  /** Una medida, una relación de aspecto o una lista numerada no son la marca de una réplica. */
  @Test
  void lasMedidasYLasListasNoSonReplicas() {
    assertFalse(PatronDeReplica.esReplica("Parlante JBL 1.1 kg"));
    assertFalse(PatronDeReplica.esReplica("Termo 1.1 L"));
    assertFalse(PatronDeReplica.esReplica("Pantalla de 1.1\""));
    assertFalse(PatronDeReplica.esReplica("Proyector relación 1:1"));
    assertFalse(PatronDeReplica.esReplica("1. 1 par de medias"));
    assertTrue(PatronDeReplica.esReplica("Tenis 1.1 Nike"));
  }

  /** Los dos mensajes que trajo el negocio el 4 de octubre de 2026. */
  @Test
  void reconoceLaTripleA() {
    assertTrue(PatronDeReplica.esReplica("Superstar Importado AAA"));
    assertTrue(PatronDeReplica.esReplica("Adidas Importado AAA 💲120"));
    assertTrue(PatronDeReplica.esReplica("*TENIS aaa* NIKE"));
    assertTrue(PatronDeReplica.esReplica("Calidad AAA."));
  }

  /** El tamaño de una pila no es una réplica, y «AAAA» o «AAA1» son otra cosa pegada. */
  @Test
  void lasPilasYLoPegadoNoSonLaTripleA() {
    assertFalse(PatronDeReplica.esReplica("Control remoto, usa pilas AAA"));
    assertFalse(PatronDeReplica.esReplica("Incluye 2 baterías tipo AAA"));
    assertFalse(PatronDeReplica.esReplica("Pila AAA recargable"));
    assertFalse(PatronDeReplica.esReplica("Código AAAA"));
    assertFalse(PatronDeReplica.esReplica("Ref AAA1"));
  }

  @Test
  void sinMarcaQuitaLaTripleAYElUnoPuntoUnoYDejaLoDemas() {
    assertEquals("Tenis estilo Superstar", PatronDeReplica.sinMarca("Tenis estilo Superstar AAA"));
    assertEquals(
        "Camiseta estilo Superdry", PatronDeReplica.sinMarca("Camiseta 1.1 estilo Superdry"));
    assertEquals("Control con pilas AAA", PatronDeReplica.sinMarca("Control con pilas AAA"));
    assertEquals("Parlante 1.1 kg", PatronDeReplica.sinMarca("Parlante 1.1 kg"));
    assertNull(PatronDeReplica.sinMarca(null));
  }
}
