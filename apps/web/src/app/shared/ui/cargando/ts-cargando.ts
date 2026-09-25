import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { cn } from '../cn';

/**
 * El anillo que gira mientras algo carga.
 *
 * <p>Es el indicador de carga del sistema desde el 25 de septiembre de 2026, y antes no había
 * ninguno: el proyecto tenía `ts-esqueleto` —un marcador con la forma del contenido que viene— y
 * texto pelado ("Cargando…"). Las dos cosas son distintas y ahora conviven a propósito. El
 * esqueleto dice <b>qué</b> va a aparecer y se usa donde se conoce la forma: una rejilla de
 * tarjetas, una tabla. El anillo dice que <b>algo está pasando ahora</b> y se usa donde no hay
 * forma que anticipar: un botón que acaba de pulsarse, un párrafo de estado.
 *
 * <p>Sale del "Spinner 4" de TailAdmin —el segundo de los dos botones de esa tarjeta—, con una
 * diferencia deliberada: <b>el original parte el anillo en dos colores</b>, la pista en un gris
 * fijo y el arco en el color de marca. Aquí los dos salen de `currentColor`, y la pista va al
 * 25 %. El motivo es que este componente se pinta sobre cinco fondos distintos —el grafito del
 * botón primario, el ámbar del de acento, el rojo del de peligro, el transparente del secundario y
 * el fondo de la página—, y un arco de color fijo desaparece sobre el suyo: el ámbar sobre ámbar
 * no se ve. Heredando el color del texto, el anillo contrasta exactamente igual que la etiqueta
 * que tiene al lado, en los dos temas y sin una regla por variante. Quien quiera el ámbar lo pide
 * con `clase="text-ts-acento"`, como se hace con `ts-icono`.
 *
 * <p>El giro dura `--mov-giro` (1000 ms), que entró al kit para esto. No se reutilizó `--mov-lenta`
 * —los 1400 ms del brillo de carga— porque son cosas distintas: aquel es ambiental y este responde
 * a una acción, y a 1400 ms el anillo se arrastra y parece que la aplicación se colgó.
 *
 * <p>Con menos movimiento pedido <b>no gira</b>, y eso lo resuelve `src/tailwind.css` apagando la
 * animación igual que la del esqueleto. No basta con acortar la duración: un anillo animado a
 * 0,01 ms es un parpadeo, que es peor que un anillo quieto. Quieto sigue diciendo lo mismo, porque
 * lo que informa de verdad es el texto que lo acompaña.
 */
@Component({
  selector: 'ts-cargando',
  templateUrl: './ts-cargando.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // `inline-flex` para que el elemento propio no herede el `display: inline`, que le sumaría el
  // interlineado al lado de una etiqueta. Mismo host que los dos alternadores.
  host: { class: 'inline-flex' },
})
export class TsCargando {
  /** Para cambiar el tamaño o el color: `size-24`, `text-ts-acento`. Nunca un píxel suelto. */
  readonly clase = input('');

  protected readonly clases = computed(() =>
    cn('girando inline-block size-16 shrink-0', this.clase()),
  );
}
