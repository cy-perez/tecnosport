import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
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

/**
 * Las cuatro medidas van juntas o no van. Es la misma regla que el constructor compacto del DTO y
 * la restricción `check` de la V55 — tres capas para una regla porque una carga a medias deja una
 * fila que revienta al leerse, que es el peor momento para enterarse.
 */
function paqueteCompletoOAusente(control: AbstractControl): ValidationErrors | null {
  const medidas = ['pesoGramos', 'largoCm', 'anchoCm', 'altoCm'].map(
    (nombre) => control.get(nombre)?.value,
  );
  const puestas = medidas.filter((medida) => medida !== null && medida !== '').length;
  return puestas === 0 || puestas === 4 ? null : { paqueteIncompleto: true };
}

@Component({
  selector: 'app-agregar-variante-admin',
  imports: [
    TsPaginaFormulario,
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsMigas,
    TsSelect,
    TsSelectControl,
  ],
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

  protected readonly form = new FormGroup(
    {
      sku: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      precio: new FormControl<number | null>(null, {
        validators: [Validators.required, Validators.min(0)],
      }),
      // Cero, y no 0.19, porque el negocio no es responsable de IVA (par. 3 del art. 437 del
      // Estatuto Tributario). El campo se queda —la calidad se pierde al cruzar los topes y ese día
      // vuelve a hacer falta—, pero el servidor rechaza cualquier tasa distinta de cero mientras
      // NEGOCIO_RESPONSABLE_IVA siga en false. Ver adr/0041.
      tasaIva: new FormControl(0, {
        nonNullable: true,
        validators: [Validators.required, Validators.min(0)],
      }),
      codigoBarras: new FormControl('', { nonNullable: true }),
      existenciaInicial: new FormControl(0, { nonNullable: true, validators: [Validators.min(0)] }),
      // El paquete es OPCIONAL desde adr/0046: sin él la variante se vende, pero solo con recogida en
      // el punto. Sin valor por omisión y con mínimo 1, que es lo que no cambió — un cero heredado de
      // un formulario en blanco sería un peso inventado, y esa es justo la diferencia que hay que
      // conservar entre "no lo sé todavía" y "mide cero".
      //
      // Sin `Validators.required`, pero con la regla de las cuatro o ninguna en el grupo: tres
      // medidas y un peso vacío no es "a medio medir", es una carga rota, y el servidor la rechaza
      // con 422. Vale más decirlo aquí que dejar que el panel mande algo que ya se sabe que falla.
      pesoGramos: new FormControl<number | null>(null, { validators: [Validators.min(1)] }),
      largoCm: new FormControl<number | null>(null, { validators: [Validators.min(1)] }),
      anchoCm: new FormControl<number | null>(null, { validators: [Validators.min(1)] }),
      altoCm: new FormControl<number | null>(null, { validators: [Validators.min(1)] }),
      atributos: new FormArray<GrupoAtributo>([]),
    },
    { validators: [paqueteCompletoOAusente] },
  );

  private readonly valorFormulario = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  /**
   * El tope más bajo de las seis transportadoras de la cuenta, medido contra el sandbox
   * (`docs/13` §6, "el peso va en kilos"): 8, 25, 60, 150, 200 y 500 kg. Por encima del primero ya
   * hay tarifas que dejan de cotizar.
   *
   * <p>Avisa, no bloquea: puede haber un producto que de verdad pese eso, y el retiro en punto no
   * necesita transportadora. Lo que de verdad atrapa es el error de unidad — 18 kg tecleados donde
   * iban 1,8 —, que es el que se paga en cada flete.
   *
   * <p>Va como `[ayuda]` del propio campo y no como un `<p role="status">` aparte, que es como
   * nació. Dos motivos, y los dos los levantó la auditoría de accesibilidad: una región viva creada
   * por un `@if` **ya poblada** no tiene región que vigilar y varios lectores no la anuncian; y el
   * aviso quedaba fuera del `aria-describedby` del campo, así que quien volvía a enfocar "Peso
   * (gramos)" oía el número y nada más — justo la advertencia que explica por qué ese número está
   * mal. Como ayuda queda atada al control, y de paso deja de haber una región viva que interrumpa
   * al lector en mitad de una palabra mientras se teclea.
   */
  private static readonly TOPE_MAS_BAJO_GRAMOS = 8_000;

  protected readonly pesoAlto = computed(() => {
    const peso = this.valorFormulario().pesoGramos;
    return peso != null && peso > AgregarVarianteAdminPage.TOPE_MAS_BAJO_GRAMOS;
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
      // Decir qué falta, no solo marcar. El botón dejó de ir deshabilitado —un `<button disabled>`
      // sale del orden de tabulación, así que quien navega con teclado ni siquiera llega a
      // enfocarlo para enterarse de por qué no pasa nada— y el corte vive aquí, igual que en el
      // resumen del checkout. Son nueve campos obligatorios: sin este mensaje, pulsar "Crear
      // variante" no producía absolutamente nada. Lo levantó la auditoría de accesibilidad.
      this.error.set(this.transloco.translate('admin.productos.agregarVariante.faltanCampos'));
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
        // Sin `?? 0`: ese cero convertía "todavía no lo sé" en "mide cero", que es justo la
        // distinción que ADR-0046 existe para conservar, y el servidor lo rechazaría con 422.
        pesoGramos: valores.pesoGramos ?? null,
        largoCm: valores.largoCm ?? null,
        anchoCm: valores.anchoCm ?? null,
        altoCm: valores.altoCm ?? null,
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
