import { TranslocoService } from '@jsverse/transloco';
import { ErrorHttp } from '../http/respuesta-http';

/**
 * El mensaje que se le muestra a quien opera el panel, a partir del error que devolvió el backend.
 *
 * Existe porque los seis paneles de administración hacían lo mismo: `catch { error.set(translate('…
 * .error')) }`. El 422 de "el reintegro no cabe en lo que queda por devolver", el 409 de "ese pedido
 * ya tiene un retracto en curso" y una caída de red se veían idénticos —"no se pudo completar la
 * acción"— y el `ProblemDetail` que el backend sí manda, con su `codigo`, se tiraba a la basura.
 * Quien atiende se quedaba sin saber si corregir el monto, mirar otra solicitud o reintentar.
 *
 * La traducción sale del **código** y no del `detail` del backend, y eso no es un detalle de estilo:
 * la frase de Java viene en un solo idioma y escrita fuera de Transloco. Del código sale una clave
 * (`admin.errores.monto_de_reintegro_invalido`) que vive en los JSON, en español y en inglés, como
 * cualquier otro texto de pantalla.
 *
 * Un código sin traducción cae al mensaje genérico de la pantalla, que es lo correcto: hay
 * treinta y cuatro códigos en el backend y la mayoría no los puede provocar un panel. Traducir solo
 * los que sí, y no inventar frases para los demás, es lo que evita que este mapa se vuelva una
 * copia desactualizada del `ManejadorDeErrores`.
 */
export function mensajeDeError(
  error: unknown,
  transloco: TranslocoService,
  claveGenerica: string,
): string {
  const codigo = error instanceof ErrorHttp ? error.codigo : undefined;
  if (!codigo) {
    return transloco.translate(claveGenerica);
  }
  const clave = `admin.errores.${codigo.toLowerCase()}`;
  const texto = transloco.translate(clave);
  // Transloco devuelve la clave cuando no existe. Es la forma de preguntar "¿la tengo?" sin
  // mantener aparte una lista de las que hay.
  return texto === clave ? transloco.translate(claveGenerica) : texto;
}
