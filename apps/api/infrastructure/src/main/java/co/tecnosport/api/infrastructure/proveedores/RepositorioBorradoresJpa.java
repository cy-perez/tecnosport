package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.BorradoresPaginados;
import co.tecnosport.api.application.proveedores.HuellaVisual;
import co.tecnosport.api.application.proveedores.RepositorioBorradores;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoDeTalla;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import co.tecnosport.api.infrastructure.proveedores.entidad.BorradorProductoJpaEntity;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioBorradoresJpa implements RepositorioBorradores {

  /** Las listas cortas van una por línea; ninguna trae saltos de línea dentro. */
  private static final String SEPARADOR = "\n";

  private final BorradorProductoJpaRepository jpa;
  private final Reloj reloj;

  public RepositorioBorradoresJpa(BorradorProductoJpaRepository jpa, Reloj reloj) {
    this.jpa = Objects.requireNonNull(jpa);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  public void guardar(BorradorProducto borrador) {
    jpa.save(aFila(borrador, reloj.ahora()));
  }

  @Override
  public void actualizar(BorradorProducto borrador) {
    if (!jpa.existsById(borrador.id())) {
      throw new IllegalStateException(
          "No se actualiza un borrador que no existe: " + borrador.id());
    }
    jpa.save(aFila(borrador, reloj.ahora()));
  }

  @Override
  public Optional<BorradorProducto> buscarPorId(UUID id) {
    return jpa.findById(id).map(RepositorioBorradoresJpa::aDominio);
  }

  @Override
  public BorradoresPaginados listar(
      EstadoBorrador estado, UUID proveedorId, int pagina, int tamanoPagina) {
    PageRequest peticion =
        PageRequest.of(pagina, tamanoPagina, Sort.by(Sort.Direction.DESC, "creadoEn"));
    Page<BorradorProductoJpaEntity> resultado;
    if (estado != null && proveedorId != null) {
      resultado = jpa.findByEstadoAndProveedorId(estado.name(), proveedorId, peticion);
    } else if (estado != null) {
      resultado = jpa.findByEstado(estado.name(), peticion);
    } else if (proveedorId != null) {
      resultado = jpa.findByProveedorId(proveedorId, peticion);
    } else {
      resultado = jpa.findAll(peticion);
    }
    return new BorradoresPaginados(
        resultado.getContent().stream().map(RepositorioBorradoresJpa::aDominio).toList(),
        resultado.getNumber(),
        resultado.getTotalPages(),
        resultado.getTotalElements());
  }

  @Override
  public List<HuellaVisual> huellasVisualesDelProveedor(UUID proveedorId) {
    // Cada renovación deja una fila más con el mismo producto y el mismo pHash: se dedupe aquí.
    return jpa.findByProveedorIdAndProductoIdIsNotNullAndPhashIsNotNull(proveedorId).stream()
        .map(f -> new HuellaVisual(f.getProductoId(), PHash.deHex(f.getPhash())))
        .distinct()
        .toList();
  }

  @Override
  public boolean existeEnRevisionConHuella(UUID proveedorId, HuellaProveedor huella) {
    return jpa.existsByProveedorIdAndEstadoAndHuella(
        proveedorId, EstadoBorrador.EN_REVISION.name(), huella.valor());
  }

  private static BorradorProductoJpaEntity aFila(BorradorProducto b, Instant ahora) {
    return new BorradorProductoJpaEntity(
        b.id(),
        b.publicacionId(),
        b.proveedorId(),
        b.extraccionCruda(),
        b.titulo().orElse(null),
        b.linea().map(LineaCatalogo::name).orElse(null),
        b.tipo().name(),
        b.precioProveedor().map(Dinero::valor).orElse(null),
        b.precioVentaSugerido().map(Dinero::valor).orElse(null),
        b.tallas().tipo().name(),
        b.tallas().sirveHasta(),
        unir(b.tallas().valores()),
        b.cantidadTonos().orElse(null),
        unir(b.tonosNombrados()),
        b.material().orElse(null),
        unir(b.caracteristicas()),
        b.huella().map(HuellaProveedor::valor).orElse(null),
        b.pHash().map(PHash::hex).orElse(null),
        b.alertas().stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
        b.estado().name(),
        b.productoId().orElse(null),
        b.motivoRechazo().orElse(null),
        b.creadoEn(),
        ahora);
  }

  private static BorradorProducto aDominio(BorradorProductoJpaEntity f) {
    Set<AlertaBorrador> alertas = EnumSet.noneOf(AlertaBorrador.class);
    if (f.getAlertas() != null && !f.getAlertas().isBlank()) {
      Arrays.stream(f.getAlertas().split(","))
          .map(String::strip)
          .filter(s -> !s.isEmpty())
          .map(AlertaBorrador::valueOf)
          .forEach(alertas::add);
    }
    return new BorradorProducto(
        f.getId(),
        f.getPublicacionId(),
        f.getProveedorId(),
        f.getExtraccionCruda(),
        f.getTitulo(),
        f.getLinea() == null ? null : LineaCatalogo.valueOf(f.getLinea()),
        TipoProductoProveedor.valueOf(f.getTipo()),
        f.getPrecioProveedor() == null ? null : Dinero.deCop(f.getPrecioProveedor()),
        f.getPrecioVentaSugerido() == null ? null : Dinero.deCop(f.getPrecioVentaSugerido()),
        new Tallas(
            TipoDeTalla.valueOf(f.getTallasTipo()),
            f.getTallasSirveHasta(),
            partir(f.getTallasValores())),
        f.getCantidadTonos(),
        partir(f.getTonosNombrados()),
        f.getMaterial(),
        partir(f.getCaracteristicas()),
        f.getHuella() == null ? null : new HuellaProveedor(f.getHuella()),
        f.getPhash() == null ? null : PHash.deHex(f.getPhash()),
        alertas,
        EstadoBorrador.valueOf(f.getEstado()),
        f.getProductoId(),
        f.getMotivoRechazo(),
        f.getCreadoEn());
  }

  private static String unir(List<String> valores) {
    return valores.isEmpty() ? null : String.join(SEPARADOR, valores);
  }

  private static List<String> partir(String texto) {
    return texto == null || texto.isBlank() ? List.of() : Arrays.asList(texto.split(SEPARADOR));
  }
}
