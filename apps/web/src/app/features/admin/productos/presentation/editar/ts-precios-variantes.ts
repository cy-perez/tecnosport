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

/** El campo de una variante y el precio del servidor con que se llenó por última vez. */
interface CampoDePrecio {
  readonly delServidor: number;
  readonly control: FormControl<string | null>;
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

  /** Por id de variante. */
  protected readonly campos = signal<ReadonlyMap<string, CampoDePrecio>>(new Map());
  protected readonly paraTodas = new FormControl<string | null>(null);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Las variantes cuyo precio no sirve: su campo lo dice, no solo el aviso general. */
  protected readonly invalidas = signal<ReadonlySet<string>>(new Set());
  /** El texto de la región viva: «Precios guardados» o «Ningún precio cambió». */
  protected readonly aviso = signal<string | null>(null);

  constructor() {
    // El detalle se vuelve a pedir tras cualquier cambio de la página —un color en una foto, una
    // talla—, no solo tras guardar precios. Rehacer todos los campos borraba en silencio lo que se
    // había tecleado sin guardar. Un campo se rehace solo si el servidor trae otro precio para esa
    // variante: ahí sí manda lo grabado.
    effect(() => {
      const variantes = this.variantes();
      this.campos.update((anteriores) => {
        const nuevos = new Map<string, CampoDePrecio>();
        for (const variante of variantes) {
          const anterior = anteriores.get(variante.id);
          nuevos.set(
            variante.id,
            anterior && anterior.delServidor === variante.precio
              ? anterior
              : {
                  delServidor: variante.precio,
                  control: new FormControl<string | null>(String(variante.precio)),
                },
          );
        }
        return nuevos;
      });
    });
  }

  protected etiqueta(variante: VarianteConPrecio): string {
    return variante.detalle ? `${variante.sku} — ${variante.detalle}` : variante.sku;
  }

  protected aplicarATodas(): void {
    const valor = leerPrecio(this.paraTodas.value);
    this.aviso.set(null);
    if (valor === null) {
      this.error.set(this.transloco.translate('admin.productos.editar.precios.invalido'));
      return;
    }
    this.error.set(null);
    this.invalidas.set(new Set());
    for (const campo of this.campos().values()) {
      campo.control.setValue(String(valor));
    }
  }

  protected guardar(): void {
    if (this.guardando()) {
      return;
    }
    this.aviso.set(null);
    const leidos = this.variantes().map((variante) => ({
      variante,
      precio: leerPrecio(this.campos().get(variante.id)?.control.value ?? null),
    }));
    const invalidas = new Set(leidos.filter((l) => l.precio === null).map((l) => l.variante.id));
    this.invalidas.set(invalidas);
    if (invalidas.size > 0) {
      this.error.set(this.transloco.translate('admin.productos.editar.precios.invalido'));
      return;
    }
    this.error.set(null);
    const precios = leidos
      .filter((l) => l.precio !== l.variante.precio)
      .map((l) => ({ varianteId: l.variante.id, precio: l.precio as number }));
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

/**
 * Pesos enteros mayores que cero, escritos como se escriben en Colombia: «79000» o «79.000». El
 * campo es de texto y no `number` a propósito: en uno numérico «79.000» es setenta y nueve, y el
 * producto se publicaba a 79 pesos sin que nada se quejara. Un punto solo vale como separador de
 * miles —seguido de tres cifras—; «65000.5» o «65,000» no son un precio y se rechazan.
 */
export function leerPrecio(valor: string | null): number | null {
  const texto = (valor ?? '').trim();
  if (!/^\d+$/.test(texto) && !/^\d{1,3}(\.\d{3})+$/.test(texto)) {
    return null;
  }
  const precio = Number(texto.replaceAll('.', ''));
  return Number.isSafeInteger(precio) && precio > 0 ? precio : null;
}
