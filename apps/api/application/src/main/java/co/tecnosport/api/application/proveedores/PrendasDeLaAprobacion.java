package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.proveedores.AprobarBorradorComando.FotoAprobada;
import co.tecnosport.api.application.proveedores.AprobarBorradorComando.PrendaAprobada;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Las variantes de color que crea una aprobación y de cuál cuelga cada foto, decididas antes de
 * crear nada: una prenda incoherente es un error del cuerpo y no debe dejar producto.
 *
 * <p><b>Una variante es una prenda</b>, y una prenda puede tener varias fotos, una sola o ninguna.
 * Las fotos que citan el mismo número de prenda cuelgan de la misma variante (ADR-0070). <b>Una
 * prenda sin foto propia</b> (ADR-0081) es la del anuncio que retrata todos los tonos en una foto
 * —el bodi en seis colores, la blusa negra, cocoa y verde de frente y de espalda—: no hay foto de
 * cada tono, y aun así cada tono se vende y quien compra tiene que poder elegirlo. Se ve en la foto
 * de todos, que vale para todas las prendas.
 *
 * <p><b>Un tono que se repite entre prendas se numera</b>: dos prendas «Negro» son «Negro 1» y
 * «Negro 2», en el orden de su primera foto, y las que no tienen foto detrás, en el orden en que
 * llegaron. Numerar es feo y es lo correcto: el valor del atributo es lo que el pedido congela
 * ({@code LineaPedido.detalleVariante}) y lo que lee quien empaca. Pero el número solo distingue
 * cuando hay una foto que mirar, así que una prenda sin foto no puede llamarse como otra.
 *
 * <p>Sin prendas dichas aparte salen de las fotos, como antes del campo: una foto con tono y sin
 * prenda es una prenda ella sola, y una sin tono vale para todas.
 */
final class PrendasDeLaAprobacion {

  /**
   * @param valorPorFoto el valor del atributo Color de la variante de cada foto, por posición; nulo
   *     donde la foto vale para todas
   * @param prendas las variantes de color que se crean, en orden
   */
  record Resueltas(List<String> valorPorFoto, List<Prenda> prendas) {}

  /**
   * @param valor el valor del atributo Color, ya numerado si se repite
   * @param tallas en cuáles tallas de las aprobadas viene, en su orden; nula si quien aprueba no lo
   *     dijo, y entonces deciden las tallas por tono que el proveedor escribió
   */
  record Prenda(String valor, String colorHex, List<String> tallas) {}

  private PrendasDeLaAprobacion() {}

  /**
   * @param tallasAprobadas las de la lista con que se aprueba; vacía si el producto es de talla
   *     única o no se sabe
   * @param conListaDeTallas si se aprueba con una lista de tallas: sin ella no hay tallas que
   *     repartir entre las prendas
   * @throws PrendaIncoherenteException si una prenda no se puede volver variante
   */
  static Resueltas resolver(
      List<FotoAprobada> fotos,
      List<PrendaAprobada> prendas,
      List<String> tallasAprobadas,
      boolean conListaDeTallas) {
    return prendas.isEmpty()
        ? desdeLasFotos(fotos)
        : explicitas(fotos, prendas, tallasAprobadas, conListaDeTallas);
  }

  /** La forma de antes de que las prendas llegaran aparte: cada foto con tono dice la suya. */
  private static Resueltas desdeLasFotos(List<FotoAprobada> fotos) {
    List<String> prendaPorFoto = new ArrayList<>(fotos.size());
    Map<String, String> tonoPorPrenda = new LinkedHashMap<>();
    Map<String, String> hexPorPrenda = new HashMap<>();
    for (int i = 0; i < fotos.size(); i++) {
      FotoAprobada foto = fotos.get(i);
      String prenda;
      if (foto.prenda() != null) {
        if (foto.tono() == null) {
          throw new PrendaIncoherenteException(foto.prenda(), "no tiene color");
        }
        prenda = "prenda-" + foto.prenda();
      } else {
        // Sin prenda: con tono es una prenda sola; sin tono vale para todas.
        prenda = foto.tono() == null ? null : "foto-" + i;
      }
      prendaPorFoto.add(prenda);
      if (prenda == null) {
        continue;
      }
      String tonoPrevio = tonoPorPrenda.putIfAbsent(prenda, foto.tono());
      String hexPrevio =
          foto.colorHex() == null ? null : hexPorPrenda.putIfAbsent(prenda, foto.colorHex());
      if ((tonoPrevio != null && !tonoPrevio.equals(foto.tono()))
          || (hexPrevio != null && !hexPrevio.equalsIgnoreCase(foto.colorHex()))) {
        throw new PrendaIncoherenteException(foto.prenda(), "tiene fotos de dos colores");
      }
    }
    Map<String, String> valorPorPrenda = numerar(tonoPorPrenda);
    List<Prenda> resueltas =
        tonoPorPrenda.keySet().stream()
            .map(p -> new Prenda(valorPorPrenda.get(p), hexPorPrenda.get(p), null))
            .toList();
    return new Resueltas(porFoto(prendaPorFoto, valorPorPrenda), resueltas);
  }

  private static Resueltas explicitas(
      List<FotoAprobada> fotos,
      List<PrendaAprobada> prendas,
      List<String> tallasAprobadas,
      boolean conListaDeTallas) {
    Map<Integer, PrendaAprobada> porNumero = new HashMap<>();
    for (PrendaAprobada prenda : prendas) {
      if (prenda.tono() == null) {
        throw new PrendaIncoherenteException(prenda.numero(), "no tiene color");
      }
      porNumero.put(prenda.numero(), prenda);
    }

    // Las prendas con foto, en el orden de su primera foto; detrás, las que no tienen ninguna, en
    // el orden en que llegaron. Es el mismo orden que el resumen del panel.
    List<String> prendaPorFoto = new ArrayList<>(fotos.size());
    Map<String, PrendaAprobada> enOrden = new LinkedHashMap<>();
    Map<String, String> hexDeLasFotos = new HashMap<>();
    for (int i = 0; i < fotos.size(); i++) {
      FotoAprobada foto = fotos.get(i);
      if (foto.prenda() == null) {
        if (foto.tono() != null) {
          throw PrendaIncoherenteException.deLaFoto(
              i + 1, "tiene color y no dice de qué prenda es");
        }
        prendaPorFoto.add(null);
        continue;
      }
      PrendaAprobada prenda = porNumero.get(foto.prenda());
      if (prenda == null) {
        throw new PrendaIncoherenteException(foto.prenda(), "no está entre las del producto");
      }
      if (foto.tono() != null && !foto.tono().equals(prenda.tono())) {
        throw new PrendaIncoherenteException(prenda.numero(), "tiene fotos de dos colores");
      }
      String clave = "prenda-" + prenda.numero();
      enOrden.putIfAbsent(clave, prenda);
      if (foto.colorHex() != null) {
        hexDeLasFotos.putIfAbsent(clave, foto.colorHex());
      }
      prendaPorFoto.add(clave);
    }
    List<String> sinFoto = new ArrayList<>();
    for (PrendaAprobada prenda : prendas) {
      String clave = "prenda-" + prenda.numero();
      if (!enOrden.containsKey(clave)) {
        enOrden.put(clave, prenda);
        sinFoto.add(clave);
      }
    }

    Map<String, String> tonoPorPrenda = new LinkedHashMap<>();
    enOrden.forEach((clave, prenda) -> tonoPorPrenda.put(clave, prenda.tono()));
    Map<String, Long> veces =
        tonoPorPrenda.values().stream()
            .collect(Collectors.groupingBy(PrendasDeLaAprobacion::clave, Collectors.counting()));
    for (String clave : sinFoto) {
      PrendaAprobada prenda = enOrden.get(clave);
      if (veces.get(clave(prenda.tono())) > 1) {
        throw new PrendaIncoherenteException(
            prenda.numero(),
            "se llama «"
                + prenda.tono()
                + "» como otra y no tiene foto propia que las distinga: dale otro nombre");
      }
    }

    Map<String, String> valorPorPrenda = numerar(tonoPorPrenda);
    List<Prenda> resueltas = new ArrayList<>();
    enOrden.forEach(
        (clave, prenda) ->
            resueltas.add(
                new Prenda(
                    valorPorPrenda.get(clave),
                    prenda.colorHex() != null ? prenda.colorHex() : hexDeLasFotos.get(clave),
                    tallasDe(prenda, tallasAprobadas, conListaDeTallas))));
    return new Resueltas(porFoto(prendaPorFoto, valorPorPrenda), List.copyOf(resueltas));
  }

  /**
   * Las tallas de una prenda, en el orden de la lista aprobada. Quien aprueba las marca en la tabla
   * de color y talla (12 de octubre de 2026); sin marcarlas, nula.
   */
  private static List<String> tallasDe(
      PrendaAprobada prenda, List<String> tallasAprobadas, boolean conListaDeTallas) {
    if (prenda.tallas() == null) {
      return null;
    }
    if (!conListaDeTallas) {
      throw new PrendaIncoherenteException(
          prenda.numero(), "dice sus tallas y el producto no se aprueba con una lista de tallas");
    }
    if (prenda.tallas().isEmpty()) {
      throw new PrendaIncoherenteException(prenda.numero(), "no viene en ninguna talla");
    }
    for (String talla : prenda.tallas()) {
      if (tallasAprobadas.stream().noneMatch(talla::equalsIgnoreCase)) {
        throw new PrendaIncoherenteException(
            prenda.numero(), "viene en la talla «" + talla + "», que el producto no tiene");
      }
    }
    return tallasAprobadas.stream()
        .filter(t -> prenda.tallas().stream().anyMatch(t::equalsIgnoreCase))
        .toList();
  }

  /** Un tono que se repite entre prendas se numera, en el orden de las prendas. */
  private static Map<String, String> numerar(Map<String, String> tonoPorPrenda) {
    Map<String, Long> prendasPorTono =
        tonoPorPrenda.values().stream()
            .collect(Collectors.groupingBy(tono -> tono, Collectors.counting()));
    Map<String, Integer> vistos = new HashMap<>();
    Map<String, String> valorPorPrenda = new HashMap<>();
    tonoPorPrenda.forEach(
        (prenda, tono) -> {
          int cual = vistos.merge(tono, 1, Integer::sum);
          valorPorPrenda.put(prenda, prendasPorTono.get(tono) == 1 ? tono : tono + " " + cual);
        });
    return valorPorPrenda;
  }

  private static List<String> porFoto(
      List<String> prendaPorFoto, Map<String, String> valorPorPrenda) {
    List<String> porFoto = new ArrayList<>(prendaPorFoto.size());
    for (String prenda : prendaPorFoto) {
      porFoto.add(prenda == null ? null : valorPorPrenda.get(prenda));
    }
    // `Collections.unmodifiableList` y no `List.copyOf`: la lista lleva nulos en las fotos sin
    // tono, y `List.copyOf` los rechaza.
    return Collections.unmodifiableList(porFoto);
  }

  private static String clave(String tono) {
    return Objects.requireNonNull(tono).toLowerCase(Locale.ROOT);
  }
}
