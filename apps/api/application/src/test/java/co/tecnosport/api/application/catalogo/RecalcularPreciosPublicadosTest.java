package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cuando cambian las tarifas de Wompi, el publicado se recalcula desde el panel (ADR-0095). */
class RecalcularPreciosPublicadosTest {

  /**
   * Un catálogo en memoria con la misma escritura condicional que el adaptador: solo graba si la
   * variante sigue con el contado y el publicado que se dicen.
   */
  private static final class PreciosFalsos implements PreciosDelCatalogo {
    final Map<UUID, PreciosDeVariante> variantes = new LinkedHashMap<>();
    final Map<UUID, Dinero> grabados = new LinkedHashMap<>();

    PreciosDeVariante con(String sku, long contado, long publicado) {
      PreciosDeVariante v =
          new PreciosDeVariante(
              UUID.randomUUID(),
              sku,
              "Producto " + sku,
              Dinero.deCop(contado),
              Dinero.deCop(publicado));
      variantes.put(v.varianteId(), v);
      return v;
    }

    /** Alguien cambió el precio desde la ficha entre la revisión y la aplicación. */
    void cambiar(PreciosDeVariante v, long contado, long publicado) {
      variantes.put(
          v.varianteId(),
          new PreciosDeVariante(
              v.varianteId(),
              v.sku(),
              v.nombreProducto(),
              Dinero.deCop(contado),
              Dinero.deCop(publicado)));
    }

    @Override
    public List<PreciosDeVariante> listar() {
      return List.copyOf(variantes.values());
    }

    @Override
    public boolean actualizarPublicado(
        UUID varianteId, Dinero contado, Dinero publicadoActual, Dinero publicadoNuevo) {
      PreciosDeVariante v = variantes.get(varianteId);
      if (v == null || !v.contado().equals(contado) || !v.publicado().equals(publicadoActual)) {
        return false;
      }
      variantes.put(
          varianteId,
          new PreciosDeVariante(varianteId, v.sku(), v.nombreProducto(), contado, publicadoNuevo));
      grabados.put(varianteId, publicadoNuevo);
      return true;
    }
  }

  private final PreciosFalsos precios = new PreciosFalsos();

  private static List<PrecioRevisado> revisadas(List<PrecioPorRecalcular> vistas) {
    return vistas.stream()
        .map(
            p ->
                new PrecioRevisado(
                    p.varianteId(), p.contado(), p.publicadoActual(), p.publicadoNuevo()))
        .toList();
  }

  @Test
  void previsualizarMuestraSoloLasQueNoCuadranConLasTarifasDeHoy() {
    PreciosDeVariante bolso = precios.con("BOLSO", 230_000, 230_000);
    precios.con("CELULAR", 1_000_000, 1_034_000);
    PreciosDeVariante camiseta = precios.con("CAMISETA", 50_000, 52_000);

    List<PrecioPorRecalcular> pendientes =
        new RecalcularPreciosPublicados(precios, TarifasDePrueba.WOMPI).previsualizar();

    assertEquals(
        List.of(
            new PrecioPorRecalcular(
                bolso.varianteId(),
                "BOLSO",
                "Producto BOLSO",
                Dinero.deCop(230_000),
                Dinero.deCop(230_000),
                Dinero.deCop(239_000)),
            new PrecioPorRecalcular(
                camiseta.varianteId(),
                "CAMISETA",
                "Producto CAMISETA",
                Dinero.deCop(50_000),
                Dinero.deCop(52_000),
                Dinero.deCop(52_500))),
        pendientes);
    assertTrue(precios.grabados.isEmpty(), "previsualizar no graba nada");
  }

  @Test
  void aplicarGrabaLaListaRevisadaYNadaMas() {
    PreciosDeVariante bolso = precios.con("BOLSO", 230_000, 230_000);
    precios.con("CELULAR", 1_000_000, 1_034_000);
    RecalcularPreciosPublicados caso =
        new RecalcularPreciosPublicados(precios, TarifasDePrueba.WOMPI);

    RecalculoAplicado resultado = caso.aplicar(revisadas(caso.previsualizar()));

    assertEquals(1, resultado.recalculadas().size());
    assertTrue(resultado.omitidas().isEmpty());
    assertEquals(Map.of(bolso.varianteId(), Dinero.deCop(239_000)), precios.grabados);
  }

  /**
   * Lo que se desalineó después de la revisión no se toca: la persona no lo vio. Antes el POST
   * recalculaba lo que estuviera desalineado al llegar, y eso no es lo que se confirmó.
   */
  @Test
  void loQueNoEstabaEnLaListaRevisadaNoSeToca() {
    precios.con("BOLSO", 230_000, 230_000);
    RecalcularPreciosPublicados caso =
        new RecalcularPreciosPublicados(precios, TarifasDePrueba.WOMPI);
    List<PrecioRevisado> vistas = revisadas(caso.previsualizar());
    PreciosDeVariante nueva = precios.con("CAMISETA", 50_000, 52_000);

    RecalculoAplicado resultado = caso.aplicar(vistas);

    assertEquals(1, resultado.recalculadas().size());
    assertEquals(Dinero.deCop(52_000), precios.variantes.get(nueva.varianteId()).publicado());
  }

  /** Alguien cambió el contado desde la ficha entre la revisión y la aplicación: no se pisa. */
  @Test
  void unContadoQueCambioEntreLaRevisionYLaAplicacionSeOmite() {
    PreciosDeVariante bolso = precios.con("BOLSO", 230_000, 230_000);
    PreciosDeVariante camiseta = precios.con("CAMISETA", 50_000, 52_000);
    RecalcularPreciosPublicados caso =
        new RecalcularPreciosPublicados(precios, TarifasDePrueba.WOMPI);
    List<PrecioRevisado> vistas = revisadas(caso.previsualizar());
    precios.cambiar(bolso, 240_000, 249_000);

    RecalculoAplicado resultado = caso.aplicar(vistas);

    assertEquals(List.of(camiseta.varianteId()), ids(resultado.recalculadas()));
    assertEquals(List.of(bolso.varianteId()), ids(resultado.omitidas()));
    assertEquals(Dinero.deCop(240_000), precios.variantes.get(bolso.varianteId()).contado());
    assertEquals(Dinero.deCop(249_000), precios.variantes.get(bolso.varianteId()).publicado());
  }

  /**
   * La tarifa cambió con un despliegue entre la revisión y la aplicación: el publicado que se
   * enseñó ya no es el de la fórmula, y grabarlo sería anunciar uno que nadie vio calcular.
   */
  @Test
  void unaFilaQueLaTarifaDeHoyYaNoDaIgualSeOmite() {
    PreciosDeVariante bolso = precios.con("BOLSO", 230_000, 230_000);
    List<PrecioRevisado> vistas =
        revisadas(new RecalcularPreciosPublicados(precios, TarifasDePrueba.WOMPI).previsualizar());
    ComisionDePasarela otra =
        new ComisionDePasarela(new BigDecimal("0.0300"), Dinero.deCop(700), new BigDecimal("0.19"));

    RecalculoAplicado resultado = new RecalcularPreciosPublicados(precios, otra).aplicar(vistas);

    assertTrue(resultado.recalculadas().isEmpty());
    assertEquals(List.of(bolso.varianteId()), ids(resultado.omitidas()));
    assertTrue(precios.grabados.isEmpty());
  }

  /** Si Wompi baja la tarifa, el publicado también baja: la fórmula manda en los dos sentidos. */
  @Test
  void conTarifasMasBajasElPublicadoBaja() {
    PreciosDeVariante celular = precios.con("CELULAR", 1_000_000, 1_034_000);
    ComisionDePasarela masBarata =
        new ComisionDePasarela(new BigDecimal("0.0200"), Dinero.deCop(700), new BigDecimal("0.19"));
    RecalcularPreciosPublicados caso = new RecalcularPreciosPublicados(precios, masBarata);

    caso.aplicar(revisadas(caso.previsualizar()));

    // (1.000.000 + 833) / (1 − 0,0238) = 1.025.233,6 → 1.026.000
    assertEquals(Map.of(celular.varianteId(), Dinero.deCop(1_026_000)), precios.grabados);
  }

  @Test
  void conTodoAlDiaNoHayNadaQueAplicar() {
    precios.con("BOLSO", 230_000, 239_000);

    RecalcularPreciosPublicados caso =
        new RecalcularPreciosPublicados(precios, TarifasDePrueba.WOMPI);

    assertTrue(caso.previsualizar().isEmpty());
    RecalculoAplicado resultado = caso.aplicar(List.of());
    assertTrue(resultado.recalculadas().isEmpty());
    assertTrue(resultado.omitidas().isEmpty());
    assertTrue(precios.grabados.isEmpty());
  }

  private static List<UUID> ids(List<PrecioRevisado> filas) {
    return filas.stream().map(PrecioRevisado::varianteId).toList();
  }
}
