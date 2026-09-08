package co.tecnosport.api.application.legal;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import java.util.List;

public interface RepositorioAutorizaciones {

  void guardar(AutorizacionDatos autorizacion);

  /**
   * Todavía no lo consume ningún caso de uso, y aun así existe: una bitácora de auditoría que solo
   * se escribe no se puede auditar. La constancia se guarda precisamente para poder mostrarla —al
   * titular que pregunta qué autorizó, o a la SIC— y sin una lectura ese propósito sería
   * declarativo. El canal de derechos que la va a usar (consultar, actualizar, rectificar,
   * suprimir) es alcance aparte.
   */
  List<AutorizacionDatos> buscarPorCorreo(CorreoElectronico correo);
}
