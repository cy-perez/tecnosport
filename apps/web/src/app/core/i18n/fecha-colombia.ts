/**
 * Fechas escritas en el idioma de quien lee y en la hora de Colombia.
 *
 * Existe porque `DatePipe` no sirve para esto en este proyecto: no hay `LOCALE_ID` ni
 * `registerLocaleData` en ninguna parte, así que el pipe cae a `en-US` —una página en castellano
 * pintaba «September 18, 2026» a un comprador colombiano— y sin `timezone` usa la zona del entorno,
 * que en SSR es la del contenedor (UTC) y en el navegador la de quien mira: un despacho de las 19:00
 * en Bogotá salía con dos fechas distintas antes y después de hidratar. Lo levantaron dos revisiones
 * adversariales a la vez, una por accesibilidad y otra por arquitectura.
 *
 * `America/Bogota` explícita, que es la convención del backend para todo lo que se le muestra a una
 * persona (apps/api/CLAUDE.md).
 */
function locale(idioma: string): string {
  return idioma === 'en' ? 'en-US' : 'es-CO';
}

/** Solo el día, en largo: «18 de septiembre de 2026». */
export function fechaLarga(iso: string, idioma: string): string {
  if (!iso) {
    return '';
  }
  return new Intl.DateTimeFormat(locale(idioma), {
    dateStyle: 'long',
    timeZone: 'America/Bogota',
  }).format(new Date(iso));
}

/** Día y hora, en medio: para el panel, donde el minuto importa. */
export function fechaConHora(iso: string, idioma: string): string {
  if (!iso) {
    return '';
  }
  return new Intl.DateTimeFormat(locale(idioma), {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'America/Bogota',
  }).format(new Date(iso));
}

/**
 * El último día en que todavía se podía hacer algo, sin hora.
 *
 * El límite que manda el servidor es el instante en que el plazo se agota, o sea el comienzo del día
 * siguiente: pintarlo tal cual decía «vence 1/09/2026» y se leía como que había hasta el 1, cuando
 * el último día era el 31. Se resta un milisegundo y se pinta solo el día, que es la granularidad
 * que tiene un plazo contado en días.
 */
export function ultimoDia(iso: string, idioma: string): string {
  if (!iso) {
    return '';
  }
  return new Intl.DateTimeFormat(locale(idioma), {
    dateStyle: 'long',
    timeZone: 'America/Bogota',
  }).format(new Date(Date.parse(iso) - 1));
}
