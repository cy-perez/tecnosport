package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cuando cambian las tarifas de Wompi, el publicado se recalcula desde el panel (ADR-0095). */
class RecalcularPreciosPublicadosTest {

  /** Un catálogo en memoria: los dos precios por variante, y lo que se grabó. */
  private static final class PreciosFalsos implements PreciosDelCatalogo {
    final List<PreciosDeVariante> variantes = new ArrayList<>();
    final Map<UUID, Dinero> grabados = new LinkedHashMap<>();

    PreciosDeVariante con(String sku, long contado, long publicado) {
      PreciosDeVariante v =
          new PreciosDeVariante(
              UUID.randomUUID(),
              sku,
              "Producto " + sku,
              Dinero.deCop(contado),
              Dinero.deCop(publicado));
      variantes.add(v);
      return v;
    }

    @Override
    public List<PreciosDeVariante> listar() {
      return List.copyOf(variantes);
    }

    @Override
    public void actualizarPublicado(UUID varianteId, Dinero publicado) {
      grabados.put(varianteId, publicado);
    }
  }

  private final PreciosFalsos precios = new PreciosFalsos();

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
  void aplicarGrabaElPublicadoNuevoDeLasQueNoCuadranYNadaMas() {
    PreciosDeVariante bolso = precios.con("BOLSO", 230_000, 230_000);
    precios.con("CELULAR", 1_000_000, 1_034_000);

    List<PrecioPorRecalcular> aplicados =
        new RecalcularPreciosPublicados(precios, TarifasDePrueba.WOMPI).aplicar();

    assertEquals(1, aplicados.size());
    assertEquals(Map.of(bolso.varianteId(), Dinero.deCop(239_000)), precios.grabados);
  }

  /** Si Wompi baja la tarifa, el publicado también baja: la fórmula manda en los dos sentidos. */
  @Test
  void conTarifasMasBajasElPublicadoBaja() {
    PreciosDeVariante celular = precios.con("CELULAR", 1_000_000, 1_034_000);
    ComisionDePasarela masBarata =
        new ComisionDePasarela(new BigDecimal("0.0200"), Dinero.deCop(700), new BigDecimal("0.19"));

    new RecalcularPreciosPublicados(precios, masBarata).aplicar();

    // (1.000.000 + 833) / (1 − 0,0238) = 1.025.233,6 → 1.026.000
    assertEquals(Map.of(celular.varianteId(), Dinero.deCop(1_026_000)), precios.grabados);
  }

  @Test
  void conTodoAlDiaNoHayNadaQueAplicar() {
    precios.con("BOLSO", 230_000, 239_000);

    RecalcularPreciosPublicados caso =
        new RecalcularPreciosPublicados(precios, TarifasDePrueba.WOMPI);

    assertTrue(caso.previsualizar().isEmpty());
    assertTrue(caso.aplicar().isEmpty());
    assertTrue(precios.grabados.isEmpty());
  }
}
