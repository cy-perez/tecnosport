package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.catalogo.ObjetoDeImagenNoEncontradoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.BorradorNoEditableException;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoDeBorradorTecnologia;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Segundo paso: comprueba contra el bucket que cada archivo de la foto llegó —no confía en que el
 * {@code PUT} terminó— y cuelga la foto del borrador, al final de las que ya tiene.
 *
 * <p><b>Cada clave tiene que ser de este borrador.</b> Sin esa guarda, una clave de otro borrador o
 * de una exportación se copiaría al catálogo al aprobar, y quitarla de aquí borraría el archivo de
 * otro.
 */
public final class AgregarFotoABorradorTecnologia {

  private final RepositorioBorradoresTecnologia repositorio;
  private final AlmacenDeArchivosDeProveedor almacen;
  private final Reloj reloj;

  public AgregarFotoABorradorTecnologia(
      RepositorioBorradoresTecnologia repositorio,
      AlmacenDeArchivosDeProveedor almacen,
      Reloj reloj) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.almacen = Objects.requireNonNull(almacen);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public FotoDeBorradorTecnologia ejecutar(AgregarFotoABorradorTecnologiaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    BorradorTecnologia borrador =
        repositorio
            .buscarPorIdParaActualizar(comando.borradorId())
            .orElseThrow(() -> new BorradorNoEncontradoException(comando.borradorId()));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    if (comando.variantes().isEmpty()) {
      throw new IllegalArgumentException("Hay que confirmar al menos un ancho de la foto.");
    }

    List<FotoDeBorradorTecnologia.Variante> variantes = new ArrayList<>();
    for (AgregarFotoABorradorTecnologiaComando.Variante v : comando.variantes()) {
      String clave = v.objectKey() == null ? null : v.objectKey().strip();
      variantes.add(
          new FotoDeBorradorTecnologia.Variante(
              v.ancho(), clave, tamanoComprobado(borrador, clave)));
    }
    String vistaPrevia =
        comando.objectKeyVistaPrevia() == null || comando.objectKeyVistaPrevia().isBlank()
            ? null
            : comando.objectKeyVistaPrevia().strip();
    if (vistaPrevia != null) {
      tamanoComprobado(borrador, vistaPrevia);
    }

    FotoDeBorradorTecnologia foto =
        new FotoDeBorradorTecnologia(
            GeneradorIdentificador.nuevo(),
            variantes,
            vistaPrevia,
            comando.alto(),
            comando.hash(),
            comando.altEs(),
            comando.altEn(),
            reloj.ahora());
    borrador.agregarFoto(foto);
    repositorio.actualizar(borrador);
    return foto;
  }

  private long tamanoComprobado(BorradorTecnologia borrador, String clave) {
    if (!ClavesDeFotosDeTecnologia.esDelBorrador(borrador.proveedorId(), borrador.id(), clave)) {
      throw new IllegalArgumentException(
          "El objeto '" + clave + "' no es una foto subida a este borrador.");
    }
    return almacen
        .tamanoBytes(clave)
        .orElseThrow(() -> new ObjetoDeImagenNoEncontradoException(clave));
  }
}
