import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarAtributos } from '../../../../catalogo/application/listar-atributos.consulta';
import { Atributo } from '../../../../catalogo/domain/producto.model';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarAgregarVarianteAdmin } from '../../application/agregar-variante-admin.mutacion';

type GrupoAtributo = FormGroup<{
  atributoId: FormControl<string>;
  valor: FormControl<string>;
  colorHex: FormControl<string>;
}>;

function grupoAtributo(): GrupoAtributo {
  return new FormGroup({
    atributoId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    valor: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    colorHex: new FormControl('', { nonNullable: true }),
  });
}

@Component({
  selector: 'app-agregar-variante-admin',
  imports: [TsPaginaFormulario, ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsMigas, TsSelect, TsSelectControl],
  templateUrl: './agregar-variante-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgregarVarianteAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'admin.productos.agregarVariante.titulo' },
  ]);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly atributos = usarAtributos();
  private readonly mutacion = usarAgregarVarianteAdmin();

  private readonly paramMap = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  protected readonly productoId = computed(() => this.paramMap().get('productoId') ?? '');

  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    sku: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    precio: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0)],
    }),
    tasaIva: new FormControl(0.19, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0)],
    }),
    codigoBarras: new FormControl('', { nonNullable: true }),
    existenciaInicial: new FormControl(0, { nonNullable: true, validators: [Validators.min(0)] }),
    // El paquete es obligatorio y no tiene valor por omisión: un cero heredado de un formulario en
    // blanco sería un peso inventado, y el servidor lo rechazaría igual (adr/0021).
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
    atributos: new FormArray<GrupoAtributo>([]),
  });

  private readonly valorFormulario = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  protected readonly formularioInvalido = computed(() => {
    this.valorFormulario();
    return this.form.invalid;
  });

  protected readonly enviando = computed(() => this.mutacion.isPending());

  protected readonly opcionesAtributo = computed<OpcionSelect[]>(() =>
    (this.atributos.data() ?? []).map((atributo) => ({
      valor: atributo.id,
      etiqueta: atributo.nombre,
    })),
  );

  protected esAtributoDeColor(atributoId: string): boolean {
    const atributo = (this.atributos.data() ?? []).find((a: Atributo) => a.id === atributoId);
    return atributo?.tipo === 'COLOR';
  }

  protected agregarFilaAtributo(): void {
    this.form.controls.atributos.push(grupoAtributo());
  }

  protected quitarFilaAtributo(indice: number): void {
    this.form.controls.atributos.removeAt(indice);
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
        productoId: this.productoId(),
        sku: valores.sku,
        precio: valores.precio ?? 0,
        tasaIva: valores.tasaIva,
        codigoBarras: valores.codigoBarras || null,
        existenciaInicial: valores.existenciaInicial,
        pesoGramos: valores.pesoGramos ?? 0,
        largoCm: valores.largoCm ?? 0,
        anchoCm: valores.anchoCm ?? 0,
        altoCm: valores.altoCm ?? 0,
        atributos: valores.atributos.map((a) => ({
          atributoId: a.atributoId,
          valor: a.valor,
          colorHex: this.esAtributoDeColor(a.atributoId) ? a.colorHex || null : null,
        })),
      },
      {
        onSuccess: () =>
          void this.router.navigate([
            '/' + this.transloco.activeLang(),
            'admin',
            'productos',
            this.productoId(),
            'editar',
          ]),
        onError: () =>
          this.error.set(this.transloco.translate('admin.productos.agregarVariante.error')),
      },
    );
  }
}
