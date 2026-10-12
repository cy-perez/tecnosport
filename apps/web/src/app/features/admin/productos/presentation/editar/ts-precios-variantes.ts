import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsMuestraColor } from '../../../../../shared/ui/muestra-color/ts-muestra-color';
import { ParteDeMuestra } from '../../../../../shared/ui/muestra-color/muestra-color.model';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarCambiarPreciosAdmin } from '../../application/cambiar-precios-admin.mutacion';

/** Una variante como la pinta la lista de precios: lo que la distingue y su precio de hoy. */
export interface VarianteConPrecio {
  readonly id: string;
  readonly sku: string;
  /** «Negro · M»; vacío si la variante no tiene atributos. */
  readonly detalle: string;
  readonly muestra: readonly ParteDeMuestra[] | null;
  readonly precio: number;
}

/**
 * El precio de venta de cada variante, editable desde la edición del producto. Es por variante y
 * no por producto porque así lo guarda la API: en ropa suelen ser iguales, en tecnología la de
 * 256 GB cuesta más que la de 128. «Aplicar a todas» cubre el caso común sin quitar el otro.
 *
 * <p>«Aplicar a todas» solo copia el valor en los campos: no guarda. Así quien lo usa ve lo que va a
 * guardar antes de hacerlo, y corrige una a mano si hace falta. Al guardar viajan solo los que
 * cambiaron; si alguno no sirve, no viaja ninguno —el servidor también lo hace todo o nada—.
 */
@Component({
  selector: 'ts-precios-variantes',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsMuestraColor],
  templateUrl: './ts-precios-variantes.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsPreciosVariantes {
  readonly productoId = input.required<string>();
  readonly variantes = input.required<readonly VarianteConPrecio[]>();

  private readonly transloco = inject(TranslocoService);
  private readonly mutacion = usarCambiarPreciosAdmin();

  /** Un control por variante, en su orden. Se rehacen cuando el servidor devuelve otros precios. */
  protected readonly controles = signal<readonly FormControl<number | null>[]>([]);
  protected readonly paraTodas = new FormControl<number | null>(null);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  /** El texto de la región viva: «Precios guardados» o «Ningún precio cambió». */
  protected readonly aviso = signal<string | null>(null);

  constructor() {
    // Lo que llega del servidor manda: tras guardar, el detalle se vuelve a pedir y los campos
    // quedan con lo que quedó grabado, no con lo que se tecleó.
    effect(() => {
      this.controles.set(this.variantes().map((v) => new FormControl<number | null>(v.precio)));
    });
  }

  protected etiqueta(variante: VarianteConPrecio): string {
    return variante.detalle ? `${variante.sku} — ${variante.detalle}` : variante.sku;
  }

  protected aplicarATodas(): void {
    const valor = this.paraTodas.value;
    this.aviso.set(null);
    if (!esPrecioValido(valor)) {
      this.error.set(this.transloco.translate('admin.productos.editar.precios.invalido'));
      return;
    }
    this.error.set(null);
    for (const control of this.controles()) {
      control.setValue(valor);
    }
  }

  protected guardar(): void {
    if (this.guardando()) {
      return;
    }
    this.aviso.set(null);
    const controles = this.controles();
    if (controles.some((control) => !esPrecioValido(control.value))) {
      this.error.set(this.transloco.translate('admin.productos.editar.precios.invalido'));
      return;
    }
    this.error.set(null);
    const precios = this.variantes()
      .map((variante, i) => ({ varianteId: variante.id, precio: controles[i].value as number }))
      .filter((cambio, i) => cambio.precio !== this.variantes()[i].precio);
    if (precios.length === 0) {
      this.aviso.set(this.transloco.translate('admin.productos.editar.precios.sinCambios'));
      return;
    }
    this.guardando.set(true);
    this.mutacion.mutate(
      { productoId: this.productoId(), precios },
      {
        onSuccess: () =>
          this.aviso.set(this.transloco.translate('admin.productos.editar.precios.guardados')),
        onError: (error: unknown) =>
          this.error.set(
            mensajeDeError(error, this.transloco, 'admin.productos.editar.precios.error'),
          ),
        onSettled: () => this.guardando.set(false),
      },
    );
  }
}

/** Pesos enteros y mayores que cero: lo mismo que exige el servidor, dicho antes de llamarlo. */
function esPrecioValido(valor: number | null): valor is number {
  return valor !== null && Number.isInteger(valor) && valor > 0;
}
