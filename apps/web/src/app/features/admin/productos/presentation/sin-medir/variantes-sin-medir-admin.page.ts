import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarVariantesSinMedir } from '../../application/listar-variantes-sin-medir.consulta';
import { usarMedirVariante } from '../../application/medir-variante.mutacion';
import { EstadoProducto, VarianteSinMedir } from '../../domain/producto-admin.model';

const CLAVE_ETIQUETA_ESTADO: Record<EstadoProducto, string> = {
  BORRADOR: 'admin.productos.estados.borrador',
  PUBLICADO: 'admin.productos.estados.publicado',
};

/**
 * Lo que falta por medir, y el formulario para medirlo, en la misma pantalla.
 *
 * <p>Sin paginador a propósito, al contrario que la lista de productos: esto no es el catálogo, es
 * una cuenta pendiente que tiene que llegar a cero. Si alguna vez no cabe en una pantalla, el
 * problema que hay que resolver no es paginarla.
 *
 * <p>El formulario se abre dentro de la fila y no en una pantalla aparte porque medir es teclear
 * cuatro números con la báscula y el metro en la mano: sacar a quien mide de la lista para
 * devolverlo después alarga la única tarea que esta pantalla tiene.
 */
@Component({
  selector: 'app-variantes-sin-medir-admin',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsEsqueleto, TsMigas],
  templateUrl: './variantes-sin-medir-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VariantesSinMedirAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'admin.productos.sinMedir.titulo' },
  ]);

  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly consulta = usarVariantesSinMedir();
  private readonly mutacion = usarMedirVariante();

  protected readonly variantes = computed<readonly VarianteSinMedir[]>(
    () => this.consulta.data()?.items ?? [],
  );
  protected readonly total = computed(() => this.consulta.data()?.total ?? 0);
  protected readonly totalEnPublicados = computed(
    () => this.consulta.data()?.totalEnPublicados ?? 0,
  );

  /** La fila con el formulario abierto. `null` = ninguna. */
  protected readonly midiendo = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  /** El SKU que se acaba de medir, para confirmarlo cuando su fila ya desapareció de la lista. */
  protected readonly medida = signal<string | null>(null);

  protected readonly enviando = computed(() => this.mutacion.isPending());

  protected readonly form = new FormGroup({
    // Mínimo 1 y sin valor por omisión, igual que en el alta: un cero heredado de un formulario en
    // blanco sería un peso inventado, y el servidor lo rechaza con 422 de todas formas.
    pesoGramos: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
    largoCm: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
    anchoCm: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
    altoCm: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
  });

  protected etiquetaEstado(estado: EstadoProducto): string {
    return this.traducir()(CLAVE_ETIQUETA_ESTADO[estado]);
  }

  protected abrir(varianteId: string): void {
    this.form.reset();
    this.error.set(null);
    this.medida.set(null);
    this.midiendo.set(varianteId);
  }

  protected cancelar(): void {
    this.midiendo.set(null);
    this.error.set(null);
  }

  protected enviar(varianteId: string): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Decir qué falta en vez de solo marcar, y sin deshabilitar el botón: un `<button disabled>`
      // sale del orden de tabulación y quien navega con teclado no llega a enterarse de por qué no
      // pasa nada. Mismo criterio que el alta de variante.
      this.error.set(this.transloco.translate('admin.productos.sinMedir.faltanCampos'));
      return;
    }
    this.error.set(null);

    const valores = this.form.getRawValue();
    this.mutacion.mutate(
      {
        varianteId,
        pesoGramos: valores.pesoGramos ?? 0,
        largoCm: valores.largoCm ?? 0,
        anchoCm: valores.anchoCm ?? 0,
        altoCm: valores.altoCm ?? 0,
      },
      {
        onSuccess: (resultado) => {
          this.midiendo.set(null);
          this.medida.set(resultado.sku);
        },
        onError: () => this.error.set(this.transloco.translate('admin.productos.sinMedir.error')),
      },
    );
  }
}
