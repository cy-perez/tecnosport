package co.tecnosport.api.infrastructure.difusion.siembra;

import co.tecnosport.api.application.difusion.ImagenAPublicar;
import co.tecnosport.api.application.difusion.PublicadorEnRedSocial;
import co.tecnosport.api.application.difusion.ResultadoPublicacion;
import co.tecnosport.api.domain.difusion.RedSocial;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El publicador de desarrollo y de los recorridos de extremo a extremo: no llama a Meta, registra
 * lo que habría publicado y devuelve un identificador inventado.
 *
 * <p><b>Existe por una razón que no es la comodidad</b>: {@code bootRun} en la máquina de quien
 * desarrolla apunta a la cuenta de Instagram <em>real</em> del negocio, porque no hay otra — Meta
 * no tiene un sandbox de la Graph API. Sin este bean, pulsar el botón del panel mientras se prueba
 * publicaría de verdad, y un post en Instagram no se deshace. Es más grave que el equivalente de
 * envíos, donde equivocarse cuesta una guía de sandbox.
 *
 * <p>Escribe el pie entero en el registro a propósito: es la única forma de revisar lo que el
 * armador propone sin mandarlo a ninguna parte.
 */
public final class PublicadorSembrado implements PublicadorEnRedSocial {

  private static final Logger log = LoggerFactory.getLogger(PublicadorSembrado.class);

  private final AtomicLong contador = new AtomicLong(1);

  /** Vacío: sin red de verdad detrás, encajar las fotos solo gastaría objetos en el bucket. */
  @Override
  public OptionalDouble proporcionDelCarrusel(RedSocial red, List<ImagenAPublicar> imagenes) {
    return OptionalDouble.empty();
  }

  /**
   * Las admite todas. Simular también el filtro de proporciones de Instagram escondería en
   * desarrollo justo la foto que en producción se va a caer, y el sentido de este doble es enseñar
   * lo que se habría mandado, no adivinar lo que Meta habría contestado.
   */
  @Override
  public List<ImagenAPublicar> admitidasPor(RedSocial red, List<ImagenAPublicar> imagenes) {
    return List.copyOf(imagenes);
  }

  @Override
  public ResultadoPublicacion publicar(
      RedSocial red, List<ImagenAPublicar> imagenes, String pieDeFoto) {
    String id = "SEMBRADO-" + red + "-" + contador.getAndIncrement();
    log.info(
        "Publicación SIMULADA en {} (nada salió a Meta). {} imagen(es): {}. Pie:\n{}",
        red,
        imagenes.size(),
        imagenes.stream().map(ImagenAPublicar::url).toList(),
        pieDeFoto);
    return ResultadoPublicacion.publicada(id);
  }
}
