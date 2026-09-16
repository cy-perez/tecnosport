package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.TarifaEnvio;

/**
 * El puerto de la cotización de envío (adr/0021, que lo retoma de adr/0004). La única
 * implementación real es el cliente de Skydropx, en {@code infrastructure}; aquí no aparece su
 * nombre por ninguna parte.
 *
 * <p>Devuelve **todas** las tarifas, no la elegida. Quedarse con la más económica es una decisión
 * de negocio y vive en {@link TarifaEnvio#masEconomica}: si algún día se ofrece "más rápido por más
 * plata", el puerto ya trae lo necesario y no hay que tocarlo.
 *
 * <p><strong>Devolvía una lista vacía para todo lo que saliera mal</strong>, agrupando a propósito
 * el proveedor caído, el destino sin cobertura y la cotización que no completó. Desde el 16 de
 * septiembre de 2026 devuelve {@link ResultadoCotizacion}, que los distingue: el porqué —y la
 * medición que lo obligó— está ahí.
 *
 * <p>Lo que no cambia es el criterio <em>fail-closed</em> de adr/0021: ningún camino autoriza
 * inventar un flete de respaldo. Cobrar uno inventado es despachar a pérdida o cobrarle de más al
 * comprador, y las dos son peores que no vender.
 */
public interface CotizadorEnvio {

  ResultadoCotizacion cotizar(CotizacionEnvio cotizacion);
}
