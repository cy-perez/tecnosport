package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.EstadoSetRotacion;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.domain.catalogo.SetRotacionIncompletoException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompletarSetRotacionTest {

  private static final UUID PRODUCTO = UUID.randomUUID();

  private final RepositorioSetsRotacionFalso repositorioSets = new RepositorioSetsRotacionFalso();
  private final AlmacenDeImagenesFalso almacenDeImagenes = new AlmacenDeImagenesFalso();
  private final CompletarSetRotacion completarSetRotacion =
      new CompletarSetRotacion(repositorioSets, almacenDeImagenes);

  @Test
  void completaElSetCuandoLosCuatroObjetosExistenDeVerdad() {
    SetRotacion set = abiertoConObjetos(4, 40_000);

    SetRotacion completado =
        completarSetRotacion.ejecutar(
            new CompletarSetRotacionComando(set.id(), fotogramas(set, 4)));

    assertEquals(EstadoSetRotacion.COMPLETO, completado.estado());
    assertEquals(4, completado.fotogramas().size());
    assertEquals(40_000, completado.fotogramas().get(0).bytes());
    assertEquals(completado, repositorioSets.ultimoActualizado);
  }

  @Test
  void noConfiaEnQueLaSubidaTermino() {
    // El asistente reporta los cuatro fotogramas, pero al bucket solo llegaron tres.
    SetRotacion set = abierto(4);
    conObjetos(set, 40_000, 0, 1, 3);

    assertThrows(
        ObjetoDeImagenNoEncontradoException.class,
        () ->
            completarSetRotacion.ejecutar(
                new CompletarSetRotacionComando(set.id(), fotogramas(set, 4))));
    assertEquals(EstadoSetRotacion.BORRADOR, set.estado());
  }

  @Test
  void unObjetoVacioNoCuentaComoFotograma() {
    SetRotacion set = abiertoConObjetos(4, 40_000);
    almacenDeImagenes.conObjeto(clave(set, 1), 0);

    assertThrows(
        SetRotacionIncompletoException.class,
        () ->
            completarSetRotacion.ejecutar(
                new CompletarSetRotacionComando(set.id(), fotogramas(set, 4))));
    assertEquals(EstadoSetRotacion.BORRADOR, set.estado());
  }

  @Test
  void faltandoFotogramasElSetSeQuedaEnBorrador() {
    SetRotacion set = abiertoConObjetos(8, 40_000);

    assertThrows(
        SetRotacionIncompletoException.class,
        () ->
            completarSetRotacion.ejecutar(
                new CompletarSetRotacionComando(set.id(), fotogramas(set, 4))));
    assertEquals(EstadoSetRotacion.BORRADOR, set.estado());
  }

  @Test
  void rechazaUnFotogramaQueNoEsCuadradoDeMilPixeles() {
    SetRotacion set = abiertoConObjetos(4, 40_000);
    List<FotogramaComando> fotogramas = new ArrayList<>(fotogramas(set, 4));
    fotogramas.set(2, new FotogramaComando(2, clave(set, 2), 1000, 750));

    assertThrows(
        SetRotacionIncompletoException.class,
        () -> completarSetRotacion.ejecutar(new CompletarSetRotacionComando(set.id(), fotogramas)));
  }

  @Test
  void rechazaUnObjetoQueNoPerteneceAlSet() {
    SetRotacion set = abiertoConObjetos(4, 40_000);
    List<FotogramaComando> fotogramas = new ArrayList<>(fotogramas(set, 4));
    fotogramas.set(0, new FotogramaComando(0, "productos/otro/rotacion/otro/0.webp", 1000, 1000));

    assertThrows(
        SetRotacionIncompletoException.class,
        () -> completarSetRotacion.ejecutar(new CompletarSetRotacionComando(set.id(), fotogramas)));
  }

  @Test
  void setInexistenteLanzaSetNoEncontrado() {
    assertThrows(
        SetRotacionNoEncontradoException.class,
        () ->
            completarSetRotacion.ejecutar(
                new CompletarSetRotacionComando(UUID.randomUUID(), List.of())));
  }

  private SetRotacion abiertoConObjetos(int fotogramas, long bytes) {
    SetRotacion set = abierto(fotogramas);
    for (int orden = 0; orden < fotogramas; orden++) {
      almacenDeImagenes.conObjeto(clave(set, orden), bytes);
    }
    return set;
  }

  private SetRotacion abierto(int fotogramas) {
    SetRotacion set = SetRotacion.abrir(PRODUCTO, fotogramas, "admin:1", null, "iPhone 14", "v1");
    repositorioSets.con(set);
    return set;
  }

  private void conObjetos(SetRotacion set, long bytes, int... ordenes) {
    for (int orden : ordenes) {
      almacenDeImagenes.conObjeto(clave(set, orden), bytes);
    }
  }

  private static List<FotogramaComando> fotogramas(SetRotacion set, int cuantos) {
    List<FotogramaComando> fotogramas = new ArrayList<>();
    for (int orden = 0; orden < cuantos; orden++) {
      fotogramas.add(new FotogramaComando(orden, clave(set, orden), 1000, 1000));
    }
    return fotogramas;
  }

  private static String clave(SetRotacion set, int orden) {
    return "productos/" + PRODUCTO + "/rotacion/" + set.id() + "/" + orden + ".webp";
  }
}
