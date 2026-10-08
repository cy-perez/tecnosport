package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.pedido.VarianteNoEncontradaException;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.ContenidoDeclarado;
import co.tecnosport.api.domain.envio.MedidasDeReferencia;
import co.tecnosport.api.domain.envio.PesoDeReferencia;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cómo se empaca un pedido: qué bultos salen, con su peso, sus medidas, su valor declarado y lo que
 * dirá su etiqueta.
 *
 * <p><strong>Dos clases de bulto</strong> ({@code adr/0071}, que reemplaza en parte la decisión del
 * 11 de septiembre de 2026):
 *
 * <ul>
 *   <li><strong>Lo medido viaja solo</strong>: una variante con su {@link Paquete} sale en un bulto
 *       por unidad, con su peso y sus medidas reales. Es lo que pasa con la tecnología, cuyo
 *       fabricante publica la caja.
 *   <li><strong>Lo promediado viaja junto</strong>: la ropa, el calzado y los bolsos sin medir van
 *       en <em>una</em> bolsa —o en varias, solo si una pasaría del techo asegurable— con las
 *       medidas transversales de referencia y la suma de los pesos promedio de cada categoría. Así
 *       se llena el formulario de la plataforma cuando la guía se crea a mano, y el flete que paga
 *       el comprador tiene que salir de esos mismos datos.
 * </ul>
 *
 * <p>Un pedido mixto —unos tenis y unos audífonos— son por eso dos bultos: la bolsa y la caja.
 *
 * <p><strong>Todo peso sale redondeado hacia arriba al kilo entero</strong> ({@link
 * Paquete#alKiloSiguiente()}): el formulario solo acepta kilos enteros. La suma va antes del
 * redondeo, no después: dos prendas de 800 g son una bolsa de 2 kg, no de 2 kg por prenda.
 *
 * <p>Lo que no tiene ni medidas ni promedio —tecnología sin medir, o una categoría a la que nadie
 * le ha puesto peso— sigue el {@code adr/0046}: no se cotiza y va solo con recogida.
 *
 * <p>Existe como pieza aparte porque <strong>lo usan dos</strong>: la cotización, que necesita los
 * bultos, y la emisión, que necesita además el contenido de cada uno y —esto es lo importante— en
 * el <em>mismo orden</em>. La plataforma empareja los paquetes del envío con los bultos de la
 * cotización por posición, así que si los dos caminos armaran su propia lista, el día que uno
 * cambiara el orden el contenido de una caja iría declarado en otra, y nada fallaría: la guía se
 * emite igual. La bolsa va siempre <strong>al final</strong>, después de los bultos medidos.
 *
 * <p>El peso y las medidas se leen del catálogo y no de la línea congelada del pedido, a propósito:
 * son del producto físico, no del precio, y si se corrigen porque estaban mal medidos el despacho
 * tiene que usar los buenos. <strong>El valor declarado es al revés</strong> y por eso entra por
 * {@link LineaAEmpacar}: es el monto que la transportadora paga si pierde el paquete, tiene que
 * coincidir con la factura, y esa dice lo que el comprador pagó — no lo que el producto cuesta hoy.
 * El de la bolsa es la suma de lo que lleva dentro.
 *
 * <p><strong>Con un piso y un techo</strong>, y los dos extremos del mismo rango se tratan distinto
 * a propósito:
 *
 * <ul>
 *   <li><strong>El piso se eleva</strong> ({@code adr/0035}). La plataforma exige un mínimo
 *       asegurable por bulto y rechaza la cotización <em>entera</em> si uno solo queda por debajo,
 *       así que un cable de 8.000 dentro de un pedido de 400.000 dejaba al comprador sin envío a
 *       domicilio y sin un error que lo explicara. Elevarlo no le quita nada a nadie.
 *   <li><strong>El techo rechaza</strong> ({@code adr/0036}). Recortar el declarado de un celular
 *       de 8.000.000 al tope de 5.000.000 haría que la transportadora responda hasta ahí si se
 *       pierde, y los tres millones restantes los pondría el negocio. Eso no es un ajuste de borde,
 *       así que ese artículo no va a domicilio y el checkout lo dice. Los dos se miran <strong>por
 *       bulto</strong>, que es como valida la plataforma: si una bolsa entera pasara del techo,
 *       todo lo que lleva quedaría fuera.
 * </ul>
 *
 * <p>Los dos límites llegan de fuera, en pesos y sin nombre de proveedor: quién los exige es
 * problema de {@code bootstrap}.
 *
 * <p><strong>Y en contraentrega el valor declarado tiene un segundo trabajo</strong> ({@code
 * adr/0037}): es lo que la transportadora cobra en la puerta. No hay ningún campo donde declarar
 * ese monto —se probaron diez grafías y no existe—, la plataforma lo calcula como la suma de lo
 * declarado, y {@code recipient_pays_shipping} está medido y no suma el flete
 * (docs/13-skydropx-capacidades.md §6.15). Así que para cobrar el total del pedido hay que
 * declararlo: {@link #armarParaRecaudo} reparte el flete entre los bultos hasta que la suma sea
 * exactamente {@code Pedido.total()}.
 */
public final class ArmadorDeBultos {

  /**
   * Para quien arma sin promedios: los sitios que montan el armador para hablar de otra cosa, casi
   * todos pruebas. Sin medidas no hay bolsa, así que todo lo que no esté medido sigue el {@code
   * adr/0046} igual que antes.
   */
  private static final RepositorioReferenciasDeEnvio SIN_REFERENCIAS =
      new RepositorioReferenciasDeEnvio() {
        @Override
        public Optional<MedidasDeReferencia> medidas() {
          return Optional.empty();
        }

        @Override
        public void guardarMedidas(MedidasDeReferencia medidas) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<PesoDeReferencia> pesos() {
          return List.of();
        }

        @Override
        public void guardarPeso(PesoDeReferencia peso) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void quitarPeso(UUID categoriaId) {
          throw new UnsupportedOperationException();
        }
      };

  private final RepositorioProductos repositorioProductos;
  private final RepositorioReferenciasDeEnvio referencias;
  private final Dinero valorDeclaradoMinimo;
  private final Dinero valorDeclaradoMaximo;

  /**
   * Un armador <strong>sin promedios</strong>: lo que no esté medido va solo con recogida, como en
   * el {@code adr/0046}. Lo usan los sitios que montan el armador para hablar de otra cosa —casi
   * todos pruebas—. Es una fábrica con nombre y no un constructor más a propósito: una sobrecarga
   * que apaga los promedios se elige sin querer al cablear, y nada fallaría.
   */
  public static ArmadorDeBultos sinPromedios(
      RepositorioProductos repositorioProductos,
      Dinero valorDeclaradoMinimo,
      Dinero valorDeclaradoMaximo) {
    return new ArmadorDeBultos(
        repositorioProductos, SIN_REFERENCIAS, valorDeclaradoMinimo, valorDeclaradoMaximo);
  }

  public ArmadorDeBultos(
      RepositorioProductos repositorioProductos,
      RepositorioReferenciasDeEnvio referencias,
      Dinero valorDeclaradoMinimo,
      Dinero valorDeclaradoMaximo) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.referencias =
        Objects.requireNonNull(referencias, "Las referencias de envío no pueden ser nulas.");
    this.valorDeclaradoMinimo =
        Objects.requireNonNull(
            valorDeclaradoMinimo, "El valor declarado mínimo no puede ser nulo.");
    this.valorDeclaradoMaximo =
        Objects.requireNonNull(
            valorDeclaradoMaximo, "El valor declarado máximo no puede ser nulo.");
  }

  /** Un pedido que se paga en línea: el declarado solo asegura, así que es el de la mercancía. */
  public List<BultoDespachable> armar(List<LineaAEmpacar> lineas) {
    return armar(lineas, null);
  }

  /**
   * Un pedido contraentrega: la suma de lo declarado es lo que la transportadora cobra en la
   * puerta, así que tiene que ser exactamente {@code totalARecaudar} ({@code adr/0037}).
   *
   * <p><strong>El flete se reparte aquí y no en la cotización del checkout</strong>, y ese es el
   * único orden que no se muerde la cola: el {@code costoEnvio} que entra por {@code
   * totalARecaudar} es el que el pedido ya congeló, no el que una cotización está calculando en
   * este momento con estos mismos bultos.
   */
  public List<BultoDespachable> armarParaRecaudo(
      List<LineaAEmpacar> lineas, Dinero totalARecaudar) {
    Objects.requireNonNull(totalARecaudar, "El total a recaudar no puede ser nulo.");
    return armar(lineas, totalARecaudar);
  }

  /**
   * Los bultos con los que sale un pedido ya creado: con el precio congelado de cada línea como
   * valor declarado y, si es contraentrega, con el flete repartido hasta {@code Pedido.total()}.
   *
   * <p>Vive aquí y no en cada caso de uso porque <strong>lo usan dos que tienen que coincidir al
   * peso</strong>: la emisión por API y la consulta con la que el panel muestra qué escribir en el
   * formulario de la plataforma cuando la guía se crea a mano ({@code adr/0071}). Si cada uno
   * decidiera por su cuenta cuándo hay recaudo, el panel podría enseñar unos valores declarados y
   * la emisión mandar otros.
   *
   * <p>El valor declarado sale del <strong>precio congelado de la línea</strong> y no del catálogo
   * vivo: es el monto que la transportadora paga si pierde el paquete, y tiene que ser el que el
   * comprador pagó y el que aparece en la factura contra la que se reclama.
   */
  public List<BultoDespachable> armarParaDespachar(Pedido pedido) {
    Objects.requireNonNull(pedido, "El pedido no puede ser nulo.");
    List<LineaAEmpacar> lineas =
        pedido.lineas().stream()
            .map(
                linea ->
                    new LineaAEmpacar(linea.varianteId(), linea.cantidad(), linea.precioUnitario()))
            .toList();
    return llevaRecaudo(pedido) ? armarParaRecaudo(lineas, pedido.total()) : armar(lineas);
  }

  /**
   * En contraentrega el valor declarado es además lo que la transportadora cobra en la puerta, y la
   * plataforma no tiene ningún campo donde declarar ese monto: lo calcula sumando lo declarado
   * ({@code adr/0037}).
   */
  public static boolean llevaRecaudo(Pedido pedido) {
    return pedido.metodoPago() == MetodoPago.CONTRAENTREGA;
  }

  private List<BultoDespachable> armar(List<LineaAEmpacar> lineas, Dinero totalARecaudar) {
    Objects.requireNonNull(lineas, "Las líneas a empacar no pueden ser nulas.");
    List<Unidad> unidades = new ArrayList<>();
    for (LineaAEmpacar linea : lineas) {
      if (linea.cantidad() <= 0) {
        throw new IllegalArgumentException(
            "La cantidad de una línea debe ser mayor que cero: " + linea.cantidad());
      }
      Producto producto = producto(linea.varianteId());
      Variante variante = variante(producto, linea.varianteId());
      // El del pedido cuando lo hay —es el que el comprador pagó y contra el que se reclama—, y el
      // del catálogo cuando todavía no hay pedido, que es el caso del checkout.
      Dinero declarado = Objects.requireNonNullElseGet(linea.valorDeclarado(), variante::precio);
      for (int unidad = 0; unidad < linea.cantidad(); unidad++) {
        unidades.add(new Unidad(producto, variante, declarado));
      }
    }

    List<Grupo> grupos = agrupar(unidades);
    List<Dinero> declarados =
        totalARecaudar == null
            ? grupos.stream().map(Grupo::conPiso).toList()
            : conElFleteRepartido(grupos, totalARecaudar);

    List<BultoDespachable> bultos = new ArrayList<>();
    // Se recogen todos los que se pasan del techo y se falla al final, no en el primero: quitar un
    // artículo del carrito y volver a chocar con el siguiente es cómo se abandona un carrito.
    List<ArticuloNoAsegurableException.Articulo> noAsegurables = new ArrayList<>();
    // Los que no tienen ni medidas ni promedio se recogen igual que los no asegurables, y por el
    // mismo motivo: el comprador tiene que enterarse de todos de una vez, no de uno por intento.
    List<ArticuloSinMedidasException.Articulo> sinMedidas = new ArrayList<>();
    for (int i = 0; i < grupos.size(); i++) {
      Grupo grupo = grupos.get(i);
      Dinero declarado = declarados.get(i);
      // El techo se mira DESPUÉS de repartir el flete, porque el flete es parte de lo declarado y
      // por tanto de lo que la plataforma valida (adr/0037). Consecuencia buscada: un artículo
      // puede ser asegurable pagando en línea y no pagando contraentrega.
      if (superaElMaximo(declarado)) {
        grupo.unidades().stream()
            .map(
                unidad ->
                    new ArticuloNoAsegurableException.Articulo(
                        unidad.variante().id(), unidad.producto().nombre()))
            .forEach(noAsegurables::add);
        continue;
      }
      if (grupo.paquete().isEmpty()) {
        grupo.unidades().stream()
            .map(
                unidad ->
                    new ArticuloSinMedidasException.Articulo(
                        unidad.variante().id(), unidad.producto().nombre()))
            .forEach(sinMedidas::add);
        continue;
      }
      bultos.add(
          new BultoDespachable(
              new Bulto(grupo.paquete().get().alKiloSiguiente(), declarado), grupo.contenido()));
    }
    // El techo asegurable primero: de los dos motivos para no despachar, ese es el que no se
    // arregla nunca, y si un artículo tiene los dos conviene que el comprador lea el definitivo.
    if (!noAsegurables.isEmpty()) {
      throw new ArticuloNoAsegurableException(noAsegurables);
    }
    if (!sinMedidas.isEmpty()) {
      throw new ArticuloSinMedidasException(sinMedidas);
    }
    return List.copyOf(bultos);
  }

  /**
   * Reparte las unidades en bultos: una por bulto si está medida o si no tiene cómo cotizarse, y
   * las promediables juntas en bolsas al final.
   *
   * <p><strong>Una bolsa, salvo que pase del techo asegurable.</strong> El techo se valida por
   * bulto, y antes de la bolsa cada unidad era su propio bulto: doce pares de tenis de 450.000
   * salían como doce bultos asegurables. Metidos en una sola bolsa serían 5.400.000 declarados, y
   * ese pedido —que antes se despachaba— quedaría solo con recogida. Así que la bolsa se cierra
   * cuando la siguiente unidad la llevaría por encima del techo, y se abre otra con las mismas
   * medidas. Una unidad que sola ya pasa del techo va sola, y el techo la rechaza como antes.
   *
   * <p>Se llena por el valor de la mercancía. En contraentrega el flete se reparte después y puede
   * empujar una bolsa llena por encima: es la misma consecuencia, buscada, que ya tenía una unidad
   * al filo del techo ({@code adr/0037}).
   *
   * <p>Las referencias se leen solo si hace falta: un carrito de puros celulares medidos no tiene
   * por qué ir a la base a preguntar cuánto pesa una camiseta.
   */
  private List<Grupo> agrupar(List<Unidad> unidades) {
    boolean hayQuePromediar =
        unidades.stream()
            .anyMatch(
                unidad ->
                    unidad.variante().paquete().isEmpty()
                        && PesoDeReferencia.admiteLaLinea(unidad.linea()));
    Optional<MedidasDeReferencia> medidas =
        hayQuePromediar ? referencias.medidas() : Optional.empty();
    Map<UUID, Integer> pesos =
        medidas.isPresent()
            ? referencias.pesos().stream()
                .collect(
                    Collectors.toMap(PesoDeReferencia::categoriaId, PesoDeReferencia::pesoGramos))
            : Map.of();

    List<Grupo> grupos = new ArrayList<>();
    List<List<Unidad>> bolsas = new ArrayList<>();
    List<Unidad> bolsa = new ArrayList<>();
    BigDecimal valorDeLaBolsa = BigDecimal.ZERO;
    for (Unidad unidad : unidades) {
      Optional<Paquete> propio = unidad.variante().paquete();
      // La medida real manda siempre: una prenda que alguien pasó por la báscula viaja con lo que
      // marcó, no con el promedio de su categoría.
      if (propio.isPresent()) {
        grupos.add(Grupo.deUna(unidad, propio, valorDeclaradoMinimo));
        continue;
      }
      if (PesoDeReferencia.admiteLaLinea(unidad.linea())
          && pesos.containsKey(unidad.producto().categoria().id())) {
        BigDecimal conEsta = valorDeLaBolsa.add(unidad.declarado().valor());
        if (!bolsa.isEmpty() && conEsta.compareTo(valorDeclaradoMaximo.valor()) > 0) {
          bolsas.add(bolsa);
          bolsa = new ArrayList<>();
          conEsta = unidad.declarado().valor();
        }
        bolsa.add(unidad);
        valorDeLaBolsa = conEsta;
        continue;
      }
      grupos.add(Grupo.deUna(unidad, Optional.empty(), valorDeclaradoMinimo));
    }
    if (!bolsa.isEmpty()) {
      bolsas.add(bolsa);
    }
    for (List<Unidad> llena : bolsas) {
      grupos.add(
          Grupo.de(
              llena,
              Optional.of(medidas.orElseThrow().conPeso(pesoDe(llena, pesos))),
              valorDeclaradoMinimo));
    }
    return grupos;
  }

  /**
   * La suma de los promedios, en {@code long} y topada: el carrito público no limita la cantidad, y
   * tres millones de jeans darían la vuelta a un {@code int} y saldrían como un peso negativo. Una
   * bolsa así de pesada no la cotiza ninguna transportadora, que es la respuesta correcta.
   */
  private static int pesoDe(List<Unidad> bolsa, Map<UUID, Integer> pesos) {
    long gramos = 0;
    for (Unidad unidad : bolsa) {
      gramos += pesos.get(unidad.producto().categoria().id());
    }
    return (int) Math.min(gramos, Integer.MAX_VALUE);
  }

  /**
   * Reparte lo que falta hasta {@code totalARecaudar} entre los bultos, proporcional al valor de
   * cada uno y con el residuo de la división en el de mayor valor. Proporcional y no "todo en uno"
   * para que el declarado de cada bulto siga pareciéndose a lo que lleva dentro; el residuo al
   * mayor porque es donde menos distorsiona en términos relativos.
   *
   * <p><strong>Y si no hay nada que repartir porque ya sobra, no se recauda.</strong> Pasa cuando
   * el piso del {@code adr/0035} infló la suma por encima del total: diez cables de 8.000 son
   * 100.000 declarados contra 80.000 de mercancía, y ningún flete nacional cierra esa diferencia.
   * La salida no es cobrar de más —esa diferencia la ve el comprador en la puerta, con el paquete
   * en la mano y sin haber aceptado nada— sino no ofrecerle contraentrega a ese carrito.
   */
  private static List<Dinero> conElFleteRepartido(List<Grupo> grupos, Dinero totalARecaudar) {
    BigDecimal base =
        grupos.stream()
            .map(grupo -> grupo.conPiso().valor())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal aRepartir = totalARecaudar.valor().subtract(base);
    if (aRepartir.signum() < 0) {
      throw new RecaudoNoCuadraException(Dinero.deCop(base), totalARecaudar);
    }

    List<BigDecimal> declarados = new ArrayList<>();
    BigDecimal repartido = BigDecimal.ZERO;
    for (Grupo grupo : grupos) {
      BigDecimal parte =
          base.signum() == 0
              ? BigDecimal.ZERO
              : aRepartir.multiply(grupo.conPiso().valor()).divide(base, 0, RoundingMode.DOWN);
      declarados.add(grupo.conPiso().valor().add(parte));
      repartido = repartido.add(parte);
    }

    // El residuo de las divisiones enteras. Sin esto la suma quedaría unos pesos por debajo del
    // total y la transportadora cobraría de menos: poco, pero en cada pedido.
    BigDecimal residuo = aRepartir.subtract(repartido);
    if (residuo.signum() > 0) {
      int mayor =
          java.util.stream.IntStream.range(0, declarados.size())
              .boxed()
              .max(Comparator.comparing(declarados::get))
              .orElse(0);
      declarados.set(mayor, declarados.get(mayor).add(residuo));
    }
    return declarados.stream().map(Dinero::deCop).toList();
  }

  /** Una unidad ya resuelta contra el catálogo, antes de saber en qué bulto va. */
  private record Unidad(Producto producto, Variante variante, Dinero declarado) {

    LineaCatalogo linea() {
      return producto.categoria().linea();
    }
  }

  /**
   * Lo que va en un bulto: una unidad medida, una sin cómo cotizarse, o la bolsa de las
   * promediadas. {@code paquete} vacío es "no se puede despachar", no "todavía no se sabe".
   *
   * <p>El piso se aplica <strong>por bulto y no por unidad</strong>, porque así es como lo valida
   * la plataforma ({@code adr/0035}). Para lo medido no cambia nada —es un bulto por unidad—; para
   * la bolsa significa que cinco camisetas declaran la suma de las cinco, que ya supera el mínimo,
   * en vez de cinco veces el mínimo.
   */
  private record Grupo(
      List<Unidad> unidades, Optional<Paquete> paquete, String contenido, Dinero conPiso) {

    static Grupo deUna(Unidad unidad, Optional<Paquete> paquete, Dinero minimo) {
      return de(List.of(unidad), paquete, minimo);
    }

    /**
     * El contenido de la bolsa nombra cada línea que lleva, una vez: una bolsa con una camiseta y
     * unos tenis declara "Ropa deportiva, Calzado deportivo". Declarar solo una dejaría la otra
     * fuera de una reclamación por pérdida.
     */
    static Grupo de(List<Unidad> unidades, Optional<Paquete> paquete, Dinero minimo) {
      String contenido =
          unidades.stream()
              .map(unidad -> ContenidoDeclarado.de(unidad.linea()))
              .distinct()
              .collect(Collectors.joining(", "));
      BigDecimal suma =
          unidades.stream()
              .map(unidad -> unidad.declarado().valor())
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      Dinero conPiso = suma.compareTo(minimo.valor()) < 0 ? minimo : Dinero.deCop(suma);
      return new Grupo(List.copyOf(unidades), paquete, contenido, conPiso);
    }
  }

  /**
   * El techo se mira contra el valor <strong>de un bulto</strong>, que es lo que la plataforma
   * valida. Dos celulares de tres millones caben —son dos bultos de tres—; uno de seis no, y no hay
   * forma de partirlo (docs/13 §6.13).
   */
  private boolean superaElMaximo(Dinero valorDeclarado) {
    return valorDeclarado.valor().compareTo(valorDeclaradoMaximo.valor()) > 0;
  }

  private Producto producto(UUID varianteId) {
    return repositorioProductos
        .buscarPorVarianteId(varianteId)
        .orElseThrow(() -> new VarianteNoEncontradaException(varianteId));
  }

  private static Variante variante(Producto producto, UUID varianteId) {
    return producto.variantes().stream()
        .filter(candidata -> candidata.id().equals(varianteId))
        .findFirst()
        .orElseThrow(() -> new VarianteNoEncontradaException(varianteId));
  }
}
