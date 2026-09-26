package co.tecnosport.api.application.sugerencia;

import co.tecnosport.api.domain.sugerencia.Sugerencia;

/**
 * Dónde queda lo que llega al buzón.
 *
 * <p><b>Se guarda aunque el aviso salga por correo</b>, y no es redundancia: el correo del aviso
 * pasa por la bandeja de salida, que reintenta pero puede acabar rindiéndose, y una sugerencia
 * perdida no se recupera de ninguna parte. La fila es el registro; el correo es solo la forma de
 * enterarse a tiempo.
 *
 * <p>Sin método de lectura a propósito, por ahora. La bandeja del panel que las liste es alcance
 * aparte, y un puerto con un {@code listar} que nadie llama es una promesa de pantalla que no
 * existe — el mismo criterio con el que {@code iconos.ts} no registra iconos "por si acaso". Lo que
 * sí hay mientras tanto es el aviso al correo del negocio, que es lo que hace que alguien las lea.
 */
public interface RepositorioSugerencias {

  void guardar(Sugerencia sugerencia);
}
