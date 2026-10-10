package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.ArchivoDeIngesta;

/**
 * Un zip en el historial, con lo que hace falta saber de sus lotes para decidir si se puede borrar.
 *
 * @param lotes cuántos lotes lo leen: uno, o dos si el proveedor sube sus dos chats juntos
 * @param enUso si alguno de ellos sigue abierto
 */
public record ArchivoDeIngestaEnLista(ArchivoDeIngesta archivo, int lotes, boolean enUso) {}
