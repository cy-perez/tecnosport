import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarOpcionesFiltro } from '../../../../catalogo/application/listar-opciones-filtro.consulta';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarCrearProductoAdmin } from '../../application/crear-producto-admin.mutacion';

@Component({
  selector: 'app-crear-producto-admin',
  imports: [TsPaginaFormulario, ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsMigas, TsSelect],
  templateUrl: './crear-producto-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CrearProductoAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'admin.productos.crear.titulo' },
  ]);

  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly opciones = usarOpcionesFiltro();
  private readonly mutacion = usarCrearProductoAdmin();

  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    descripcion: new FormControl('', { nonNullable: true }),
    marcaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    categoriaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  // valueChanges, no statusChanges: mismo motivo que registro-cliente.page.ts — el estado del
  // formulario puede quedarse INVALID de punta a punta mientras cambia, así que statusChanges no
  // emitiría de nuevo tras la carga inicial de las opciones.
  private readonly valorFormulario = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  protected readonly formularioInvalido = computed(() => {
    this.valorFormulario();
    return this.form.invalid;
  });

  protected readonly enviando = computed(() => this.mutacion.isPending());

  protected readonly opcionesMarca = computed<OpcionSelect[]>(() =>
    (this.opciones.marcas.data() ?? []).map((marca) => ({
      valor: marca.id,
      etiqueta: marca.nombre,
    })),
  );

  protected readonly opcionesCategoria = computed<OpcionSelect[]>(() =>
    (this.opciones.categorias.data() ?? []).map((categoria) => ({
      valor: categoria.id,
      etiqueta: categoria.nombre,
    })),
  );

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);

    const valores = this.form.getRawValue();
    this.mutacion.mutate(
      {
        nombre: valores.nombre,
        descripcion: valores.descripcion,
        marcaId: valores.marcaId,
        categoriaId: valores.categoriaId,
      },
      {
        onSuccess: () =>
          void this.router.navigate(['/' + this.transloco.activeLang(), 'admin', 'productos']),
        onError: () => this.error.set(this.transloco.translate('admin.productos.crear.error')),
      },
    );
  }
}
