import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarOpcionesFiltro } from '../../../../catalogo/application/listar-opciones-filtro.consulta';
import { TsBoton } from '../../../../../shared/ts-boton/ts-boton';
import { TsCampo } from '../../../../../shared/ts-campo/ts-campo';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { OpcionSelect, TsSelect } from '../../../../../shared/ts-select/ts-select';
import { usarEditarProductoAdmin } from '../../application/editar-producto-admin.mutacion';
import { usarVerProductoAdmin } from '../../application/ver-producto-admin.consulta';

@Component({
  selector: 'app-editar-producto-admin',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsEsqueleto, TsSelect],
  templateUrl: './editar-producto-admin.page.html',
  styleUrl: './editar-producto-admin.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditarProductoAdminPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly opciones = usarOpcionesFiltro();
  private readonly mutacion = usarEditarProductoAdmin();

  private readonly paramMap = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  private readonly id = computed(() => this.paramMap().get('id') ?? '');

  protected readonly consulta = usarVerProductoAdmin(this.id);

  protected readonly error = signal<string | null>(null);
  private prefilled = false;

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    descripcion: new FormControl('', { nonNullable: true }),
    marcaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    categoriaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly valorFormulario = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly formularioInvalido = computed(() => {
    this.valorFormulario();
    return this.form.invalid;
  });

  protected readonly enviando = computed(() => this.mutacion.isPending());

  protected readonly opcionesMarca = computed<OpcionSelect[]>(() =>
    (this.opciones.marcas.data() ?? []).map((marca) => ({ valor: marca.id, etiqueta: marca.nombre })),
  );

  protected readonly opcionesCategoria = computed<OpcionSelect[]>(() =>
    (this.opciones.categorias.data() ?? []).map((categoria) => ({
      valor: categoria.id,
      etiqueta: categoria.nombre,
    })),
  );

  constructor() {
    effect(() => {
      const producto = this.consulta.data();
      if (producto && !this.prefilled) {
        this.prefilled = true;
        this.form.patchValue({
          nombre: producto.nombre,
          descripcion: producto.descripcion,
          marcaId: producto.marca.id,
          categoriaId: producto.categoria.id,
        });
      }
    });
  }

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);

    const valores = this.form.getRawValue();
    this.mutacion.mutate(
      {
        id: this.id(),
        comando: {
          nombre: valores.nombre,
          descripcion: valores.descripcion,
          marcaId: valores.marcaId,
          categoriaId: valores.categoriaId,
        },
      },
      {
        onSuccess: () => void this.router.navigate(['/' + this.transloco.activeLang(), 'admin', 'productos']),
        onError: () => this.error.set(this.transloco.translate('admin.productos.editar.error')),
      },
    );
  }
}
