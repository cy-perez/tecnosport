import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { LogoPago } from '../../../shared/ui/icono/logos-pago.generado';
import {
  logoAddi,
  logoAmericanExpress,
  logoBancolombia,
  logoBbva,
  logoDaviplata,
  logoMastercard,
  logoNequi,
  logoPse,
  logoSistecredito,
  logoVisa,
} from '../../../shared/ui/icono/logos-pago.generado';
import { TsLogoPago } from '../../../shared/ui/icono/ts-logo-pago';

/**
 * Un medio de pago del pie: su etiqueta y, si lo tiene, el logo con que se dibuja.
 *
 * <p><b>El logo es opcional y hoy hay exactamente un caso sin él</b>, la contraentrega. Llegó a ser
 * obligatorio durante unas horas del 28 de septiembre de 2026 —ese día la contraentrega estrenó
 * símbolo propio y la tarjeta genérica se abrió en las tres franquicias de Wompi, así que no
 * quedaba ninguno pelado— y volvió a ser opcional el mismo día, cuando el símbolo de la
 * contraentrega se quitó a pedido.
 *
 * <p><b>Sin logo, el nombre se ve; con logo, no.</b> Eso ya no es un campo que haya que acordarse de
 * marcar: la plantilla lo deduce de si hay logo, y no puede desincronizarse. Hubo una bandera
 * `nombreALaVista` para lo mismo, y con ella eran dos datos que había que mantener de acuerdo
 * —poner el nombre a la vista junto a un logo que ya dice lo mismo es el ruido que se quitó, y
 * quitarlo sin logo deja un elemento de lista vacío—.
 *
 * <p>Los logos salen de `logos-pago.generado.ts`, que produce `npm run logos-pago`. El porqué de
 * que vayan en línea y no como archivos servidos está en `ts-logo-pago`.
 */
interface MedioDePago {
  readonly clave: string;

  /** Sin logo, el medio va como texto y su nombre se ve. Hoy solo la contraentrega. */
  readonly logo?: LogoPago;

  /**
   * Caja distinta de la de la fila —`h-24 w-64`—, cuando el dibujo no aguanta la de todos.
   *
   * <p><b>La caja compartida no iguala lo que se ve, iguala lo que se reserva.</b> Cada logo se
   * contiene dentro de ella, así que lo que acaba midiendo su dibujo depende de su relación de
   * aspecto: los apaisados llegan al ancho y se quedan cortos de alto, los cuadrados al revés. Sin
   * ninguna excepción la columna tenía a American Express, PSE, Nequi y BBVA pisando los 19-23 px
   * de alto —más que Visa, que es la franquicia que la gente busca primero— y a Sistecrédito en
   * 7,7. El tamaño lo estaba decidiendo el encuadre del archivo.
   *
   * <p><b>El ancho no se toca: los diez reservan `w-64`.</b> Esa columna de 64 px más el `gap-x-16`
   * es lo único que sostiene la rejilla del `flex-wrap` —dos columnas en escritorio, tres en
   * tableta, cuatro en teléfono— y basta con que uno mida distinto para que corra a sus vecinos de
   * fila. Lo que se baja es el alto, y siempre a un escalón de la escala de espacio: nunca un píxel
   * suelto (regla dura #2).
   *
   * <p>Las cinco excepciones de hoy, todas del 28 de septiembre de 2026 y todas medidas en el
   * navegador, nunca a ojo:
   *
   * <ul>
   *   <li><b>American Express</b> y <b>PSE</b>, `h-16`. Eran los dos más altos de la columna
   *       —23,4 y 23,6 px de tinta— sin que eso lo hubiera decidido nadie: los dos son casi tan
   *       apaisados como la caja, así que la llenaban de lado a lado. Quedan en 43,8×16 y 40,3×16.
   *   <li><b>Nequi</b>, <b>BBVA</b> y <b>Addi</b>, `h-12`. Los tres se dibujaban entre 19 y 23 px de
   *       alto. Quedan en 38×12, 39,3×12 y 31,5×12.
   * </ul>
   *
   * <h3>Bancolombia no tiene excepción, y tuvo dos</h3>
   *
   * <p>Llevó `h-32 w-96` por la razón equivocada: en la caja común su letra medía 4,0 px contra los
   * 6,2 de Sistecrédito, y la culpa no era de la caja sino de su archivo, que declaraba un lienzo
   * de 217×61 con el logotipo metido en `22 19 174 23` —un tercio del alto en aire—. Ceñido el
   * `viewBox` en el generador, esa compensación sobra: **una caja a medida para tapar un lienzo mal
   * recortado es una excepción que no sabe lo que está arreglando.**
   *
   * <p>Después llevó `w-logo-pago`, 80 px, porque a 63,7×8,2 se veía pequeño —y el ancho es la
   * única palanca que le sirve: ceñido es 7,6:1, el más apaisado de los diez, así que dentro de una
   * caja de 64 de ancho topa con el ancho mucho antes que con el alto y `h-24`, `h-32` y `h-48` dan
   * los mismos píxeles—. También se quitó, y por lo que costó: **medido a 390 px, esos 16 px de
   * más corrían a sus dos vecinos de fila** —Daviplata a 176 en vez de 160, BBVA a 256 en vez de
   * 240— y el teléfono es donde más logos caben por renglón, o sea donde más se nota. En
   * escritorio no pasaba nada porque cae en la columna derecha, y por eso el defecto no apareció
   * hasta mirarlo en el ancho de un teléfono.
   *
   * <p>Queda en la caja de todos, a 63,7×8,2 px, que es del tamaño de Sistecrédito —el otro
   * logotipo largo— y no del de sus vecinos cuadrados. **Eso es lo que cuesta una rejilla que
   * alinea: el ancho reservado manda sobre el tamaño de cada dibujo.**
   */
  readonly clase?: string;
}

/**
 * Con qué se puede pagar, en el orden en que la gente los busca.
 *
 * <p><b>Es la lista del checkout, no una lista de deseos.</b> Sale de `MetodoPago` del backend
 * —tarjeta, PSE, Nequi, Bancolombia, Sistecrédito, transferencia manual y contraentrega— más Addi.
 *
 * <p><b>`TARJETA` se abre en las tres franquicias que Wompi acepta</b>: Visa, Mastercard y American
 * Express, nacionales e internacionales, según la documentación de Wompi y su centro de soporte,
 * consultados el 28 de septiembre de 2026. Diners Club no aparece en ninguna de las dos y por eso
 * no está. Antes había un icono genérico que decía "Tarjeta de crédito y débito" sin decir cuáles.
 * <b>Esta lista se queda vieja en silencio si se cambia de pasarela</b>, y publicar una franquicia
 * que el cobro rechaza es información engañosa igual que anunciar un medio que no existe.
 *
 * <p><b>Addi sigue aquí y ya no dice "próximamente" a la vista.</b> `MetodoPago` lo tuvo y lo quitó
 * a propósito (`V61__sin_addi.sql`), así que el checkout no lo ofrece; su nombre accesible sí
 * conserva el aviso, porque sale de la misma clave. Decisión tomada a sabiendas el 28 de septiembre
 * de 2026, con el riesgo de la Ley 1480 sobre la mesa.
 *
 * <p>Daviplata y BBVA aparecen como transferencia y no como botones de pago: son las cuentas a las
 * que se transfiere. <b>Hoy el checkout enseña una sola cuenta</b>, la que configuren
 * `TRANSFERENCIA_BANCO` y compañía, así que la etiqueta dice "transferencia" y no promete elegir
 * entre las tres; el día que el backend soporte varias cuentas, esto ya las nombra.
 */
export const MEDIOS_DE_PAGO: readonly MedioDePago[] = [
  { clave: 'pie.pagos.visa', logo: logoVisa },
  { clave: 'pie.pagos.mastercard', logo: logoMastercard },
  { clave: 'pie.pagos.american_express', logo: logoAmericanExpress, clase: 'h-16' },
  { clave: 'pie.pagos.pse', logo: logoPse, clase: 'h-16' },
  { clave: 'pie.pagos.nequi', logo: logoNequi, clase: 'h-12' },
  { clave: 'pie.pagos.bancolombia', logo: logoBancolombia },
  { clave: 'pie.pagos.daviplata', logo: logoDaviplata },
  { clave: 'pie.pagos.bbva', logo: logoBbva, clase: 'h-12' },
  { clave: 'pie.pagos.sistecredito', logo: logoSistecredito },
  { clave: 'pie.pagos.addi', logo: logoAddi, clase: 'h-12' },
  // Sin logo: va solo con su nombre, y por eso es la única de la columna que se lee en vez de
  // reconocerse. Tuvo símbolo propio unas horas del 28 de septiembre de 2026 y se quitó a pedido.
  { clave: 'pie.pagos.contraentrega' },
];

/**
 * La columna "Medios de pago" del pie, en su propio componente para que `pie.html` la pueda diferir
 * entera: los logos en línea son casi todo el peso del pie, y un `@defer` solo saca del bundle
 * inicial lo que vive en un componente aparte —si los datos siguieran importados en `pie.ts`, se
 * quedarían donde estaban—.
 */
@Component({
  selector: 'app-pie-pagos',
  imports: [TranslocoPipe, TsLogoPago],
  templateUrl: './pie-pagos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-col gap-8' },
})
export class PiePagos {
  protected readonly mediosDePago = MEDIOS_DE_PAGO;
}
