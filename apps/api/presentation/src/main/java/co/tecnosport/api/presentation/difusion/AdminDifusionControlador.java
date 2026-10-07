package co.tecnosport.api.presentation.difusion;

import co.tecnosport.api.application.difusion.DifundirProducto;
import co.tecnosport.api.application.difusion.DifundirProductoComando;
import co.tecnosport.api.application.difusion.RepositorioPublicaciones;
import co.tecnosport.api.domain.difusion.PublicacionEnRed;
import co.tecnosport.api.domain.difusion.RedSocial;
import co.tecnosport.api.presentation.difusion.dto.DifundirProductoPeticion;
import co.tecnosport.api.presentation.difusion.dto.PropuestaDePieRespuesta;
import co.tecnosport.api.presentation.difusion.dto.PublicacionEnRedRespuesta;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Difundir un producto en redes, y ver qué se difundió antes.
 *
 * <p><b>Controlador propio y no un método más en {@code AdminProductoControlador}</b>: aquel ya
 * tiene dieciséis rutas y todas son del catálogo. Esto no es catálogo — es marketing sobre un
 * producto—, y meterlo allí habría sido la vía por la que un controlador se convierte en el sitio
 * donde cabe todo lo que empieza por {@code /productos/}.
 *
 * <p><b>Sin {@code TransactionTemplate}, y a propósito.</b> Lo normal en el panel es abrir la
 * transacción aquí, pero {@link DifundirProducto} llama a Meta en la mitad y por eso gestiona sus
 * escrituras con {@code EnTransaccionPropia} — la de {@code PENDIENTE} tiene que estar confirmada
 * cuando Meta publique. Envolverlo aquí anidaría las dos y dejaría sin efecto justo lo que protege.
 * Es la misma forma que {@code EmitirGuiaDePedido} (adr/0033).
 */
@RestController
@RequestMapping("/api/v1/admin/productos/{id}/difusion")
public class AdminDifusionControlador {

  private static final Logger log = LoggerFactory.getLogger(AdminDifusionControlador.class);

  private final DifundirProducto difundirProducto;
  private final RepositorioPublicaciones repositorioPublicaciones;

  public AdminDifusionControlador(
      DifundirProducto difundirProducto, RepositorioPublicaciones repositorioPublicaciones) {
    this.difundirProducto = Objects.requireNonNull(difundirProducto);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
  }

  @PostMapping
  public List<PublicacionEnRedRespuesta> difundir(
      @PathVariable("id") UUID id, @RequestBody DifundirProductoPeticion cuerpo) {
    List<PublicacionEnRed> publicaciones =
        difundirProducto.ejecutar(
            new DifundirProductoComando(id, redes(cuerpo.redes()), cuerpo.pieDeFoto()));

    log.info(
        "Producto {} difundido en {}: {}",
        id,
        cuerpo.redes(),
        publicaciones.stream().map(p -> p.red() + "=" + p.estado()).toList());

    return publicaciones.stream().map(AdminDifusionControlador::aRespuesta).toList();
  }

  /** El historial, para que la ficha pueda avisar antes de repetir. */
  @GetMapping
  public List<PublicacionEnRedRespuesta> historial(@PathVariable("id") UUID id) {
    return repositorioPublicaciones.historialDe(id).stream()
        .map(AdminDifusionControlador::aRespuesta)
        .toList();
  }

  /**
   * La propuesta de pie, para pintarla en la caja editable antes de publicar.
   *
   * <p>Se pide al servidor en vez de armarla en el navegador, y no es un capricho: el pie lleva el
   * precio, y el precio lo decide el servidor. Un pie armado en el cliente sería el cliente
   * decidiendo qué precio se anuncia, que es justo lo que la regla dura #7 prohíbe.
   */
  @GetMapping("/propuesta")
  public PropuestaDePieRespuesta propuesta(
      @PathVariable("id") UUID id, @RequestParam(name = "red") String red) {
    return new PropuestaDePieRespuesta(difundirProducto.proponerPie(id, RedSocial.valueOf(red)));
  }

  private static List<RedSocial> redes(List<String> nombres) {
    return nombres.stream().map(RedSocial::valueOf).toList();
  }

  private static PublicacionEnRedRespuesta aRespuesta(PublicacionEnRed publicacion) {
    return new PublicacionEnRedRespuesta(
        publicacion.id(),
        publicacion.red().name(),
        publicacion.estado().name(),
        publicacion.idPublicacionExterna().orElse(null),
        publicacion.pieDeFoto(),
        publicacion.urlImagen(),
        publicacion.urlsImagen(),
        publicacion.solicitadaEn(),
        publicacion.publicadaEn().orElse(null),
        publicacion.detalleDelFallo().orElse(null));
  }
}
