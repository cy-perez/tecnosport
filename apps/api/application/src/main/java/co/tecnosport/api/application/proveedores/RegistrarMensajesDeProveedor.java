package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.TipoMensaje;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * De los mensajes crudos de un lote, deja registrados los del proveedor que no estaban.
 *
 * <p>Es la costura entre la fuente y el resto del flujo: la exportación llega por {@link
 * FuenteDeMensajes} y un webhook llegaría por su controlador, y los dos terminan aquí con la misma
 * lista de {@link MensajeCrudo}. Lo que hace es lo mismo en los dos casos: quedarse con lo que dijo
 * el proveedor, fabricar el id externo cuando la fuente no trae uno, descartar lo que ya estaba
 * —dentro de la base y dentro del propio lote— y guardar los adjuntos en el bucket con la key del
 * mensaje.
 *
 * <h2>Las fotos omitidas del mismo minuto</h2>
 *
 * <p>Una exportación sin medios escribe cada foto como {@code <Multimedia omitido>}: mismo texto,
 * misma hora sin segundos, mismo remitente. Con el id fabricado de proveedor, fecha y contenido,
 * las cuatro fotos de un producto serían un solo mensaje. Se numeran dentro del minuto, y el número
 * es estable mientras el minuto esté completo en el archivo, que es siempre.
 */
public final class RegistrarMensajesDeProveedor {

  private final RepositorioProveedores repositorioProveedores;
  private final RepositorioLotesIngesta repositorioLotes;
  private final RepositorioMensajesProveedor repositorioMensajes;
  private final AlmacenDeArchivosDeProveedor almacen;
  private final CalculadorDePHash calculadorDePHash;

  /**
   * @param calculadorDePHash con qué se saca el pHash de cada foto al registrarla, con los bytes ya
   *     en la mano: guardarlo es lo que deja comparar todas las fotos de un anuncio con todas las
   *     de otro sin volver a leer el bucket (9 de octubre de 2026)
   */
  public RegistrarMensajesDeProveedor(
      RepositorioProveedores repositorioProveedores,
      RepositorioLotesIngesta repositorioLotes,
      RepositorioMensajesProveedor repositorioMensajes,
      AlmacenDeArchivosDeProveedor almacen,
      CalculadorDePHash calculadorDePHash) {
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.repositorioMensajes = Objects.requireNonNull(repositorioMensajes);
    this.almacen = Objects.requireNonNull(almacen);
    this.calculadorDePHash = Objects.requireNonNull(calculadorDePHash);
  }

  public MensajesRegistrados ejecutar(UUID loteId, List<MensajeCrudo> crudos) {
    Objects.requireNonNull(crudos, "La lista de mensajes no puede ser nula.");
    LoteIngesta lote =
        repositorioLotes
            .buscarPorId(loteId)
            .orElseThrow(() -> new LoteNoEncontradoException(loteId));
    Proveedor proveedor =
        repositorioProveedores
            .buscarPorId(lote.proveedorId())
            .orElseThrow(() -> new ProveedorNoEncontradoException(lote.proveedorId()));

    List<MensajeCrudo> delProveedor =
        crudos.stream().filter(m -> proveedor.esRemitente(m.remitente())).toList();
    int ignorados = crudos.size() - delProveedor.size();

    // LinkedHashMap: el orden de llegada se conserva y un id repetido dentro del lote se queda
    // con su primera aparición.
    Map<IdExternoDeMensaje, MensajeCrudo> candidatos = new LinkedHashMap<>();
    Map<Instant, Integer> omitidosPorMinuto = new HashMap<>();
    for (MensajeCrudo crudo : delProveedor) {
      candidatos.putIfAbsent(idExternoDe(proveedor.id(), crudo, omitidosPorMinuto), crudo);
    }

    Set<IdExternoDeMensaje> existentes =
        new HashSet<>(
            repositorioMensajes.idsExternosExistentes(proveedor.id(), candidatos.keySet()));

    List<MensajeProveedor> nuevos = new ArrayList<>();
    for (Map.Entry<IdExternoDeMensaje, MensajeCrudo> entrada : candidatos.entrySet()) {
      if (existentes.contains(entrada.getKey())) {
        continue;
      }
      nuevos.add(registrar(proveedor.id(), lote.id(), entrada.getKey(), entrada.getValue()));
    }
    repositorioMensajes.guardarTodos(nuevos);

    return new MensajesRegistrados(crudos.size(), ignorados, nuevos);
  }

  private static IdExternoDeMensaje idExternoDe(
      UUID proveedorId, MensajeCrudo crudo, Map<Instant, Integer> omitidosPorMinuto) {
    if (crudo.idExterno() != null && !crudo.idExterno().isBlank()) {
      return new IdExternoDeMensaje(crudo.idExterno());
    }
    String contenido;
    if (crudo.adjunto() != null) {
      contenido = crudo.adjunto().nombre();
    } else if (crudo.medioOmitido()) {
      int posicion = omitidosPorMinuto.merge(crudo.enviadoEn(), 1, Integer::sum);
      contenido = "<omitido>#" + posicion + nulo(crudo.pieDeFoto());
    } else {
      contenido = nulo(crudo.texto()) + nulo(crudo.pieDeFoto());
    }
    return IdExternoDeMensaje.deExportacion(proveedorId, crudo.enviadoEn(), contenido);
  }

  private MensajeProveedor registrar(
      UUID proveedorId, UUID loteId, IdExternoDeMensaje idExterno, MensajeCrudo crudo) {
    UUID id = GeneradorIdentificador.nuevo();
    String referencia = null;
    PHash pHash = null;
    if (crudo.tipo() == TipoMensaje.IMAGEN && crudo.adjunto() != null) {
      MensajeCrudo.Adjunto adjunto = crudo.adjunto();
      referencia =
          ClavesDeProveedor.medio(proveedorId, crudo.enviadoEn(), id, adjunto.contentType());
      almacen.guardar(referencia, adjunto.contentType(), adjunto.bytes());
      pHash = calculadorDePHash.de(adjunto.bytes()).orElse(null);
    }
    return new MensajeProveedor(
        id,
        proveedorId,
        loteId,
        idExterno,
        crudo.enviadoEn(),
        crudo.tipo(),
        crudo.texto(),
        crudo.pieDeFoto(),
        referencia,
        crudo.tipo() == TipoMensaje.IMAGEN ? referencia == null : crudo.medioOmitido(),
        pHash);
  }

  private static String nulo(String texto) {
    return texto == null ? "" : texto;
  }
}
