import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import {
  usarDifundirProducto,
  usarHistorialDeDifusion,
  usarPropuestaDePie,
} from '../application/difundir-producto.mutacion';
import { REDES, RedSocial } from '../domain/difusion.model';
import { fechaConHora } from '../../../../core/i18n/fecha-colombia';

/**
 * Difundir un producto en Facebook e Instagram desde su ficha.
 *
 * <h2>Por qué el pie es editable y no un texto fijo</h2>
 *
 * Lo que el servidor propone es una propuesta: lleva el nombre, el precio vigente, el primer
 * párrafo de la descripción, los atributos que haya y las etiquetas de la categoría. Nadie tiene
 * que copiar un precio a mano —de ahí salen los errores caros— pero el criterio sigue siendo de
 * quien publica.
 *
 * <h2>Una red a la vez</h2>
 *
 * El backend acepta las dos de una y las trata por separado, pero la pantalla ofrece una: el pie
 * **no es el mismo** en las dos —en Facebook lleva el enlace y en Instagram remite a la
 * biografía— y una sola caja de texto para dos destinos obligaría a elegir cuál de los dos textos
 * se edita. Publicar en las dos son dos pulsaciones, y entre ellas se puede ajustar el texto.
 */
@Component({
  selector: 'app-panel-de-difusion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo],
  templateUrl: './panel-de-difusion.html',
})
export class PanelDeDifusion {
  readonly productoId = input.required<string>();

  private readonly transloco = inject(TranslocoService);

  /**
   * Fecha y hora en el idioma de quien lee y en hora de Colombia. Era `DatePipe` con `'short'`: sin
   * `LOCALE_ID` registrado cae a `en-US` —"9/29/26, 3:00 PM" también en español— y sin zona usa la
   * del entorno. Mismo formateador que la bandeja de envíos (`core/i18n/fecha-colombia.ts`).
   */
  protected fechaConHora(iso: string): string {
    return fechaConHora(iso, this.transloco.activeLang());
  }

  protected readonly redes = REDES;
  protected readonly red = signal<RedSocial>('INSTAGRAM');

  protected readonly pie = new FormControl('', { nonNullable: true });

  protected readonly historial = usarHistorialDeDifusion(() => this.productoId());
  protected readonly propuesta = usarPropuestaDePie(
    () => this.productoId(),
    () => this.red(),
  );
  protected readonly difusion = usarDifundirProducto();

  protected readonly aviso = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Si quien publica ya tocó el texto, cambiar de red no se lo puede pisar. */
  private readonly editadoAMano = signal(false);

  constructor() {
    this.pie.valueChanges.subscribe(() => this.editadoAMano.set(true));

    // La propuesta llega asíncrona y cambia al cambiar de red. Se vuelca en la caja **salvo** que
    // la persona ya haya escrito algo: sobrescribirle el texto porque llegó una respuesta de red
    // es de las cosas que hacen desconfiar de una herramienta para siempre.
    effect(() => {
      const propuesta = this.propuesta.data();
      if (propuesta !== undefined && !this.editadoAMano()) {
        this.pie.setValue(propuesta, { emitEvent: false });
      }
    });
  }

  /** La última difusión de cada red, para avisar antes de repetir. */
  protected readonly ultimaPorRed = computed(() => {
    const publicaciones = this.historial.data() ?? [];
    return REDES.map((red) => ({
      red,
      ultima: publicaciones.find((p) => p.red === red) ?? null,
    }));
  });

  protected elegirRed(red: RedSocial): void {
    if (this.red() === red) {
      return;
    }
    this.red.set(red);
    // Volver a proponer: el pie de Facebook y el de Instagram no son el mismo texto. Solo si nadie
    // lo había tocado — si lo tocó, manda lo suyo.
    if (!this.editadoAMano()) {
      this.aviso.set(null);
    }
  }

  protected restaurarPropuesta(): void {
    const propuesta = this.propuesta.data();
    if (propuesta !== undefined) {
      this.pie.setValue(propuesta, { emitEvent: false });
      this.editadoAMano.set(false);
    }
  }

  protected difundir(): void {
    // Guarda de reentrada en vez de deshabilitar el botón: un botón que se apaga bajo el dedo manda
    // el foco a `<body>`. Mismo criterio que el resto del panel.
    if (this.difusion.isPending()) {
      return;
    }
    if (this.pie.value.trim() === '') {
      this.error.set(this.transloco.translate('admin.difusion.faltaPie'));
      return;
    }

    this.error.set(null);
    this.aviso.set(null);

    this.difusion.mutate(
      { productoId: this.productoId(), redes: [this.red()], pieDeFoto: this.pie.value },
      {
        onSuccess: (resultado) => {
          if (resultado.tipo === 'YA_EN_MARCHA') {
            this.error.set(this.transloco.translate('admin.difusion.yaEnMarcha'));
            return;
          }
          if (resultado.tipo === 'NO_DIFUNDIBLE') {
            // Por el código y traducido. Era el `detail` del backend tal cual: en español siempre,
            // con el UUID del producto dentro.
            this.error.set(this.transloco.translate('admin.difusion.noDifundible'));
            return;
          }
          const fallida = resultado.publicaciones.find((p) => p.estado === 'FALLIDA');
          if (fallida) {
            this.error.set(
              this.transloco.translate('admin.difusion.fallo', {
                motivo: fallida.detalleDelFallo ?? '',
              }),
            );
            return;
          }
          this.aviso.set(this.transloco.translate('admin.difusion.publicado', { red: this.red() }));
        },
        onError: () => this.error.set(this.transloco.translate('admin.difusion.error')),
      },
    );
  }
}
