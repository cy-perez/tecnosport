package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.proveedores.tecnologia.DefinirPaletaDeTecnologia;
import co.tecnosport.api.presentation.proveedores.dto.DefinirPaletaPeticion;
import co.tecnosport.api.presentation.proveedores.dto.PaletaDefinidaRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La paleta oficial de un equipo de tecnología (ADR-0086): la ficha la dice al pie de las fotos, y
 * cada color de ella se puede elegir y preguntar por WhatsApp.
 */
@RestController
@RequestMapping("/api/v1/admin/productos")
public class AdminPaletaControlador {

  private final DefinirPaletaDeTecnologia definirPaleta;
  private final TransactionTemplate transaccion;

  public AdminPaletaControlador(
      DefinirPaletaDeTecnologia definirPaleta, PlatformTransactionManager transactionManager) {
    this.definirPaleta = Objects.requireNonNull(definirPaleta);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PutMapping("/{id}/paleta")
  public PaletaDefinidaRespuesta definir(
      @PathVariable UUID id, @RequestBody DefinirPaletaPeticion cuerpo) {
    Integer nacidas = transaccion.execute(estado -> definirPaleta.ejecutar(id, cuerpo.colores()));
    return new PaletaDefinidaRespuesta(nacidas == null ? 0 : nacidas);
  }
}
