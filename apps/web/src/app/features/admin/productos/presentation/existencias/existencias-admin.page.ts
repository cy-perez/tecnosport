import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarAjustarExistencia } from '../../application/ajustar-existencia.mutacion';
import { usarExistencias } from '../../application/listar-existencias.consulta';
import {
  EstadoProducto,
  ExistenciaAjustada,
  ExistenciaDeVariante,
} from '../../domain/producto-admin.model';

const CLAVE_ETIQUETA_ESTADO: Record<EstadoProducto, string> = {
  BORRADOR: 'admin.productos.estados.borrador',
  PUBLICADO: 'admin.productos.estados.publicado',
};

/**
 * Las existencias del catálogo y el formulario para contarlas, en la misma pantalla y con el mismo
 * criterio que la de sin-medir: contar es recorrer la bodega con la lista delante, y sacar a quien
 * cuenta de la lista para devolverlo después alarga la única tarea que esta pantalla tiene.
 *
 * <p>Las tres cifras van separadas —lo que declara el catálogo, lo que dice el libro y lo
 * disponible— porque son tres cosas distintas y la primera es la única que ve quien compra. Que la
 * primera y la segunda puedan diferir es el defecto que `ADR-0049` documenta: se marca, no se
 * disimula.
 */
@Component({
  selector: 'app-existencias-admin',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsEsqueleto, TsMigas],
  templateUrl: './existencias-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExistenciasAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'admin.productos.existencias.titulo' },
  ]);

  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly consulta = usarExistencias();
  private readonly mutacion = usarAjustarExistencia();

  protected readonly variantes = computed<readonly ExistenciaDeVariante[]>(
    () => this.consulta.data()?.items ?? [],
  );
  protected readonly total = computed(() => this.consulta.data()?.total ?? 0);
  protected readonly totalSinExistencia = computed(
    () => this.consulta.data()?.totalSinExistencia ?? 0,
  );

  /** La fila con el formulario abierto. `null` = ninguna. */
  protected readonly contando = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  /** El resultado del último conteo, para poder decir cuál de las tres cosas pasó. */
  protected readonly ajuste = signal<ExistenciaAjustada | null>(null);

  protected readonly enviando = computed(() => this.mutacion.isPending());

  protected readonly form = new FormGroup({
    // Sin valor por omisión: un cero heredado de un formulario en blanco sería un conteo
    // inventado, y este es justo el dato que no se puede inventar. Cero sí es un conteo válido
    // cuando alguien lo escribe — se acabó—, por eso el mínimo es 0 y no 1, al revés que en las
    // medidas del paquete.
    cantidadContada: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0)],
    }),
    motivo: new FormControl<string>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  protected etiquetaEstado(estado: EstadoProducto): string {
    return this.traducir()(CLAVE_ETIQUETA_ESTADO[estado]);
  }

  protected abrir(varianteId: string): void {
    this.form.reset({ cantidadContada: null, motivo: '' });
    this.error.set(null);
    this.ajuste.set(null);
    this.contando.set(varianteId);
  }

  protected cancelar(): void {
    this.contando.set(null);
    this.error.set(null);
  }

  protected enviar(varianteId: string): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Decir qué falta en vez de solo marcar, y sin deshabilitar el botón: un `<button disabled>`
      // sale del orden de tabulación y quien navega con teclado no llega a enterarse de por qué no
      // pasa nada. Mismo criterio que la pantalla de sin-medir.
      this.error.set(this.transloco.translate('admin.productos.existencias.faltanCampos'));
      return;
    }
    this.error.set(null);

    const valores = this.form.getRawValue();
    this.mutacion.mutate(
      {
        varianteId,
        cantidadContada: valores.cantidadContada ?? 0,
        motivo: valores.motivo.trim(),
      },
      {
        onSuccess: (resultado) => {
          this.contando.set(null);
          this.ajuste.set(resultado);
        },
        onError: () =>
          this.error.set(this.transloco.translate('admin.productos.existencias.error')),
      },
    );
  }
}
