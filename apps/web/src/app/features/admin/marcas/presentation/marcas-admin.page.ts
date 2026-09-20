import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../shared/ts-migas/ts-migas';
import { Marca } from '../../../catalogo/domain/producto.model';
import { usarMigasAdmin } from '../../migas-admin';
import { usarCrearMarca } from '../application/crear-marca.mutacion';
import { usarMarcasAdmin } from '../application/listar-marcas-admin.consulta';

/**
 * Las marcas del catálogo, y el formulario para dar de alta una.
 *
 * Pantalla propia y no un formulario dentro del alta de producto, que es donde de verdad se
 * descubre que la marca falta: en línea habría que decidir qué pasa con lo ya tecleado del
 * producto, y eso es más pantalla de la que esta tarea necesita. El costo queda escrito —salir del
 * formulario de producto pierde lo escrito— y si molesta, se resuelve después.
 *
 * Sin renombrar ni borrar: lo primero cambia lo que ve el comprador en la ficha y en el filtro, lo
 * segundo tiene que decidir qué pasa con los productos que cuelgan de la marca.
 */
@Component({
  selector: 'app-marcas-admin',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsEsqueleto, TsMigas],
  templateUrl: './marcas-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarcasAdminPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.marcas.titulo' }]);

  private readonly transloco = inject(TranslocoService);

  protected readonly consulta = usarMarcasAdmin();
  private readonly mutacion = usarCrearMarca();

  protected readonly marcas = computed<readonly Marca[]>(() => this.consulta.data() ?? []);
  protected readonly enviando = computed(() => this.mutacion.isPending());

  protected readonly error = signal<string | null>(null);
  /** El nombre recién creado, para confirmarlo por su nombre y no con un "listo" genérico. */
  protected readonly creada = signal<string | null>(null);

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Se dice qué falta en vez de deshabilitar el botón: un `<button disabled>` sale del orden de
      // tabulación y quien navega con teclado no llega a enterarse de por qué no pasa nada. Mismo
      // criterio que la pantalla de variantes sin medir.
      this.error.set(this.transloco.translate('admin.marcas.faltaNombre'));
      return;
    }
    this.error.set(null);
    this.creada.set(null);

    this.mutacion.mutate(this.form.getRawValue().nombre, {
      onSuccess: (resultado) => {
        if (resultado.tipo === 'YA_EXISTE') {
          this.error.set(this.transloco.translate('admin.marcas.yaExiste'));
          return;
        }
        this.creada.set(resultado.marca.nombre);
        this.form.reset();
      },
      onError: () => this.error.set(this.transloco.translate('admin.marcas.error')),
    });
  }
}
