import { fechaConHora } from '../../../../core/i18n/fecha-colombia';
import { origenPublico } from '../../../../core/seo/origen-publico';
import { EnlaceDeCompraAdmin, urlDeCompra } from '../domain/enlace-de-compra-admin.model';

type Traducir = (clave: string, parametros?: Record<string, unknown>) => string;

/**
 * La URL de compra y el mensaje listo para pegar en el chat. La ficha se abre en el idioma del
 * panel: quien escribe por WhatsApp lo hace en el mismo en que el negocio le contesta.
 */
export function compraPorWhatsapp(
  enlace: EnlaceDeCompraAdmin,
  idioma: string,
  traducir: Traducir,
): { readonly url: string; readonly mensaje: string } | null {
  if (!enlace.slug) {
    return null;
  }
  const url = urlDeCompra(origenPublico(), idioma, enlace.slug, enlace.token);
  const mensaje = traducir('admin.enlacesCompra.bandeja.mensaje', {
    producto: enlace.producto ?? '',
    detalle: enlace.detalle ?? '',
    fecha: fechaConHora(enlace.expiraEn, idioma),
    url,
  });
  return { url, mensaje };
}
