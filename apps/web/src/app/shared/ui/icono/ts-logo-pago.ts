import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { cn } from '../cn';
import type { LogoPago } from './logos-pago.generado';

/**
 * Dibuja el logo de un medio de pago (Visa, PSE, Nequi…) como SVG en línea.
 *
 * <p><b>Diez de los once son monocromos y uno no.</b> El `fill="currentColor"` del `<svg>` es quien
 * pinta, salvo donde un trazo trae su `relleno`, que lo pisa con `[attr.fill]`. Hoy solo lo trae
 * Mastercard: su dibujo son dos círculos que se solapan, y aplanado a un color se queda en dos
 * manchas con el logotipo encima borrado — se miró en el navegador el 28 de septiembre de 2026, que
 * es donde se ve, porque en jsdom un color es una cadena más. La excepción se declara logo a logo
 * en el generador, no se deduce del archivo.
 *
 * **Es un tercer componente, y no un modo de `ts-icono-marca`, por la caja.** Aquel asume
 * `viewBox="0 0 24 24"` y hasta lo verifica al generar, porque así distribuye `simple-icons` y
 * porque un logo de red social es un glifo cuadrado. Estos no: van de 24×24 —las tres franquicias,
 * que sí salen de `simple-icons`— a 1000×305 —BBVA—, pasando por el cuadrado de 512 de la
 * contraentrega, cada uno con la relación de aspecto con que su dueño lo dibujó. Meterlos todos en
 * una caja de 24 deformaría a la mayoría, y un logo deformado es un problema de marca, no de CSS.
 * Por eso cada logo trae su `vista` y aquí se ata, y por eso el dibujo se contiene con
 * `preserveAspectRatio` en vez de estirarse.
 *
 * **Y no son un `path` suelto**: Bancolombia trae catorce y Sistecrédito quince, y los dos trazados
 * de bitmap —Addi y BBVA— vienen dentro de un `<g transform>` que los coloca y los voltea.
 *
 * Lo que sí comparte con `ts-icono-marca`, porque no es decoración: `currentColor` para heredar el
 * color del texto en los dos temas, tamaño desde la escala de espacio, y `aria-hidden` con
 * `focusable="false"` — **el logo nunca es el nombre accesible**. En el pie eso le toca al texto
 * que va al lado, que es el que nombra la fila y el que además dice lo que el logo no puede decir,
 * como que Addi todavía no está disponible.
 *
 * <h2>Por qué en línea y no un `<img>`</h2>
 *
 * <p>Fueron archivos en `assets/pagos/` hasta el 28 de septiembre de 2026, cada uno con sus
 * colores sobre una pastilla blanca, porque tres de ellos —Addi, BBVA y Bancolombia— son negro puro
 * o casi y sobre `--color-marca` no se veían. Al pasarlos a monocromo para quitar esa pastilla, el
 * `<img>` dejó de servir: **un `<img>` es opaco al CSS de la página** y no puede heredar
 * `currentColor`. Hornear el blanco en el archivo habría atado los siete a fondo oscuro; así siguen
 * al texto y sirven también sobre una superficie clara.
 *
 * <p>El precio es real y conviene tenerlo escrito: ~50 kB de datos de trazado que antes eran
 * archivos estáticos cacheables y ahora viajan en el paquete. Fueron ~41 hasta que Mastercard y
 * American Express dejaron `simple-icons` por el dibujo completo: aquellos glifos de 24×24 pesaban
 * 0,7 kB cada uno y estos 7,5 y 5,7. El generador los redondea a un
 * decimal por eso, y `logos-pago.generado.ts` no crece salvo que se agregue un medio de pago.
 *
 * Los datos salen de `logos-pago.generado.ts`, que produce `npm run logos-pago` desde
 * `apps/web/logos-pago/`.
 */
@Component({
  selector: 'ts-logo-pago',
  template: `
    <svg
      [class]="clases()"
      [attr.viewBox]="logo().vista"
      fill="currentColor"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
      focusable="false"
    >
      <svg:g [attr.transform]="logo().transformacion">
        @for (trazo of logo().trazos; track $index) {
          <svg:path
            [attr.d]="trazo.d"
            [attr.fill]="trazo.relleno"
            [attr.fill-rule]="trazo.reglaDeRelleno"
            [attr.clip-rule]="trazo.reglaDeRecorte"
            [attr.stroke]="trazo.trazo"
            [attr.stroke-width]="trazo.grosorDeTrazo"
            [attr.stroke-linejoin]="trazo.unionDeTrazo"
          />
        }
      </svg:g>
    </svg>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsLogoPago {
  /** El logo, importado de `logos-pago.generado.ts`. */
  readonly logo = input.required<LogoPago>();

  /** Para cambiar el hueco: `h-24 w-64`. Nunca un píxel suelto. */
  readonly clase = input('');

  /**
   * La caja es fija y el dibujo se contiene dentro, que es lo único que alinea siete logos de siete
   * relaciones de aspecto sin deformar ninguno. El que peor lo lleva es Sistecrédito, un logotipo
   * de 200×26 que contenido por el ancho queda en unos 8 px de alto; se acepta a sabiendas porque la
   * alternativa es un hueco el doble de ancho para los once.
   *
   * <p>**Quien necesita otro alto lo pide por `clase`**, y hoy solo lo hace la contraentrega: su
   * símbolo es el único cuadrado y detallado, y a 24 px de alto tiene una fracción del área que un
   * logotipo ancho y se vuelve una mancha. Medido en el navegador a 24, 32, 40, 64 y 96 px.
   */
  protected readonly clases = computed(() => cn('inline-block shrink-0 h-24 w-64', this.clase()));
}
