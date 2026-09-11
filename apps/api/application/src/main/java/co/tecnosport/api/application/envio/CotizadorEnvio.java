package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.TarifaEnvio;
import java.util.List;

/**
 * El puerto de la cotización de envío (adr/0021, que lo retoma de adr/0004). La única
 * implementación real es el cliente de Skydropx, en {@code infrastructure}; aquí no aparece su
 * nombre por ninguna parte.
 *
 * <p>Devuelve **todas** las tarifas, no la elegida. Quedarse con la más económica es una decisión
 * de negocio y vive en {@link TarifaEnvio#masEconomica}: si algún día se ofrece "más rápido por más
 * plata", el puerto ya trae lo necesario y no hay que tocarlo.
 *
 * <p><strong>Lista vacía significa "no hay envío a domicilio"</strong>, y agrupa a propósito los
 * tres casos que el checkout trata igual: el proveedor no respondió, el destino no tiene cobertura,
 * o ninguna transportadora devolvió tarifa. Ninguno autoriza inventar un flete de respaldo — se
 * ofrece la recogida en el punto y se explica. Es el criterio <em>fail-closed</em> de adr/0021:
 * cobrar un flete inventado es despachar a pérdida o cobrarle de más al comprador, y las dos son
 * peores que no vender.
 */
public interface CotizadorEnvio {

  List<TarifaEnvio> cotizar(CotizacionEnvio cotizacion);
}
