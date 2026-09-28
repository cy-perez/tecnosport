import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { cn } from '../cn';
import type { LogoPago } from './logos-pago.generado';

/**
 * Dibuja el logo de un medio de pago (Visa, PSE, Nequi…) como SVG en línea.
 *
 * <p><b>Nueve de los diez son monocromos y uno no.</b> El `fill="currentColor"` del `<svg>` es quien
 * pinta, salvo donde un trazo trae su `relleno`, que lo pisa con `[attr.fill]`. Hoy solo lo trae
 * Mastercard: su dibujo son dos círculos que se solapan, y aplanado a un color se queda en dos
 * manchas con el logotipo encima borrado — se miró en el navegador el 28 de septiembre de 2026, que
 * es donde se ve, porque en jsdom un color es una cadena más. La excepción se declara logo a logo
 * en el generador, no se deduce del archivo.
 *
 * **Es un tercer componente, y no un modo de `ts-icono-marca`, por la caja.** Aquel asume
 * `viewBox="0 0 24 24"` y hasta lo verifica al generar, porque así distribuye `simple-icons` y
 * porque un logo de red social es un glifo cuadrado. Estos no: van de 24×16 —Visa— a 1000×305
 * —BBVA—, cada uno con la relación de aspecto con que su dueño lo dibujó. Meterlos todos en una
 * caja de 24 deformaría a la mayoría, y un logo deformado es un problema de marca, no de CSS. Por
 * eso cada logo trae su `vista` y aquí se ata, y por eso el dibujo se contiene con
 * `preserveAspectRatio` en vez de estirarse.
 *
 * **Y no son un `path` suelto**: Bancolombia trae catorce y Sistecrédito quince, y los dos trazados
 * de bitmap —Addi y BBVA— vienen dentro de un `<g transform>` que los coloca y los voltea.
 *
 * <h2>`xMinYMid` y no `xMidYMid`</h2>
 *
 * <p>El dibujo va pegado al borde <b>izquierdo</b> de su caja, no centrado, porque quien usa este
 * componente lo usa en lista —la columna de medios de pago del pie— y una lista se lee por su
 * borde izquierdo. Centrados, ese borde era un zigzag que no había decidido nadie: cada logo se
 * apartaba la mitad de lo que le sobraba en su caja, o sea 14 px en Visa, 17,4 en Daviplata, 12 en
 * Mastercard y 0 en BBVA, según su relación de aspecto. Medido en el navegador el 28 de septiembre
 * de 2026, que es donde se ve: en jsdom `preserveAspectRatio` es una cadena más.
 *
 * <p><b>Esto no alcanza cuando el aire viene dentro del archivo</b>, porque entonces está
 * <i>dentro</i> del dibujo y ningún encuadre lo quita. Le pasaba a Bancolombia, y se arregla donde
 * toca: ciñendo su `vista` en `generar-logos-pago.mjs`.
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
 * `currentColor`. Hornear el blanco en el archivo habría atado los diez a fondo oscuro; así siguen
 * al texto y sirven también sobre una superficie clara.
 *
 * <p>El precio es real y conviene tenerlo escrito: ~48 kB de datos de trazado que antes eran
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
      preserveAspectRatio="xMinYMid meet"
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
   * La caja es fija y el dibujo se contiene dentro —`meet`—, que es lo único que alinea diez logos
   * de diez relaciones de aspecto sin deformar ninguno. El que peor lo lleva es Sistecrédito, un
   * logotipo de 200×26 que contenido por el ancho queda en unos 8 px de alto; se acepta a sabiendas
   * porque la alternativa es un hueco el doble de ancho para los diez.
   *
   * <p>**Quien necesita otra caja la pide por `clase`**, y en el pie lo hacen seis: American Express
   * y PSE a `h-16`, Nequi, BBVA y Addi a `h-12`, y Bancolombia a `w-logo-pago` —el único que pide
   * <b>ancho</b>, porque es tan apaisado que el alto no le hace nada—. El porqué de cada uno vive
   * donde se decide, que es la tabla `MEDIOS_DE_PAGO` de `pie.ts`, y no aquí: este componente no
   * sabe qué logo es importante.
   */
  protected readonly clases = computed(() => cn('inline-block shrink-0 h-24 w-64', this.clase()));
}
