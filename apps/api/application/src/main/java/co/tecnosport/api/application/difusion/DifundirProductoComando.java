package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.difusion.RedSocial;
import java.util.List;
import java.util.UUID;

/**
 * Qué producto se difunde, en qué redes y con qué texto.
 *
 * <p>{@code pieDeFoto} vacío o nulo significa "arma el que toque": es el camino normal cuando nadie
 * editó la propuesta. Si viene escrito se usa tal cual, sin retocarlo — quien publica ya decidió.
 *
 * <p><b>El mismo pie para las dos redes cuando se piden juntas</b>, y es una limitación consciente
 * del comando: la propuesta que el armador genera sí distingue —en Facebook pinta el enlace y en
 * Instagram remite a la biografía— pero si la persona lo edita, lo edita una vez. Partirlo en dos
 * campos habría significado dos cajas de texto en el panel para un caso que todavía no se ha
 * pedido.
 */
public record DifundirProductoComando(UUID productoId, List<RedSocial> redes, String pieDeFoto) {}
