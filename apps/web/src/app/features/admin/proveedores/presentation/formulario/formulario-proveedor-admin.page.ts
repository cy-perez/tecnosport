import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsCheckbox } from '../../../../../shared/ui/checkbox/ts-checkbox';
import { TsPaginaFormulario } from '../../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarMigasAdmin } from '../../../migas-admin';
import {
  usarCrearProveedor,
  usarEditarProveedor,
  usarEliminarProveedor,
} from '../../application/guardar-proveedor.mutacion';
import { usarVerProveedorAdmin } from '../../application/ver-proveedor.consulta';
import {
  DatosProveedor,
  LINEAS_PROVEEDOR,
  LineaProveedor,
  Proveedor,
} from '../../domain/proveedor.model';

/**
 * El alta y la edición de un proveedor, en una sola pantalla: los campos son los mismos y lo
 * único que cambia es si hay un `id` en la ruta.
 *
 * El campo que más cuesta explicar es `nombreEnExportacion`: es como el teléfono guardó al
 * contacto, tal cual sale en cada línea del chat exportado. Si no coincide, la ingesta ignora
 * todos los mensajes del proveedor y el lote termina con cero publicaciones —sin error, porque
 * técnicamente no hubo ninguno—. La ayuda del campo lo dice.
 *
 * Al editar, debajo va eliminarlo, con la misma confirmación en dos pasos que borrar un borrador.
 * El servidor lo rechaza si algún producto del catálogo salió de él, y la pantalla dice cuántos.
 */
@Component({
  selector: 'app-formulario-proveedor-admin',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsCheckbox,
    TsEsqueleto,
    TsMigas,
    TsPaginaFormulario,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './formulario-proveedor-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioProveedorAdminPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  private readonly paramMap = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  protected readonly id = computed(() => this.paramMap().get('id') ?? '');
  protected readonly editando = computed(() => this.id() !== '');

  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.proveedores.titulo', ruta: ['proveedores'] },
    { clave: 'admin.proveedores.formulario.miga' },
  ]);

  protected readonly consulta = usarVerProveedorAdmin(this.id);
  private readonly crear = usarCrearProveedor();
  private readonly editar = usarEditarProveedor();
  private readonly eliminar = usarEliminarProveedor();

  protected readonly enviando = computed(() => this.crear.isPending() || this.editar.isPending());
  protected readonly error = signal<string | null>(null);
  protected readonly guardado = signal<string | null>(null);

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly avisoGuardado = viewChild<ElementRef<HTMLElement>>('avisoGuardado');
  private readonly botonEliminar = viewChild('botonEliminar', { read: ElementRef });
  private readonly cajaEliminar = viewChild<ElementRef<HTMLElement>>('cajaEliminar');

  protected readonly confirmandoEliminar = signal(false);
  protected readonly errorEliminar = signal<string | null>(null);
  protected readonly eliminando = computed(() => this.eliminar.isPending());

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    nombreEnExportacion: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    telefonoWhatsApp: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    linea: new FormControl<LineaProveedor | ''>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    factorDeMargen: new FormControl('1.30', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    publicacionAutomatica: new FormControl(false, { nonNullable: true }),
    activo: new FormControl(true, { nonNullable: true }),
  });

  protected readonly opcionesLinea = computed<OpcionSelect[]>(() =>
    LINEAS_PROVEEDOR.map((linea) => ({
      valor: linea,
      etiqueta: this.traducir()('admin.proveedores.lineas.' + linea),
    })),
  );

  /** Al editar, el formulario se llena con lo que haya en el servidor, una vez por proveedor. */
  private cargado: string | null = null;

  constructor() {
    effect(() => {
      const proveedor = this.consulta.data();
      if (!proveedor || this.cargado === proveedor.id) {
        return;
      }
      this.cargado = proveedor.id;
      this.form.reset(this.aFormulario(proveedor));
    });
  }

  private aFormulario(proveedor: Proveedor) {
    return {
      nombre: proveedor.nombre,
      nombreEnExportacion: proveedor.nombreEnExportacion,
      telefonoWhatsApp: proveedor.telefonoWhatsApp,
      linea: proveedor.linea,
      factorDeMargen: String(proveedor.factorDeMargen),
      publicacionAutomatica: proveedor.publicacionAutomatica,
      activo: proveedor.activo,
    };
  }

  protected enviar(): void {
    if (this.enviando()) {
      return;
    }
    const factor = Number(this.form.controls.factorDeMargen.value.replace(',', '.'));
    if (this.form.invalid || !Number.isFinite(factor) || factor < 1) {
      this.form.markAllAsTouched();
      this.error.set(this.transloco.translate('admin.proveedores.formulario.faltanCampos'));
      return;
    }
    this.error.set(null);
    this.guardado.set(null);

    const valores = this.form.getRawValue();
    const datos: DatosProveedor = {
      nombre: valores.nombre.trim(),
      nombreEnExportacion: valores.nombreEnExportacion.trim(),
      telefonoWhatsApp: valores.telefonoWhatsApp.trim(),
      linea: valores.linea as LineaProveedor,
      factorDeMargen: factor,
      publicacionAutomatica: valores.publicacionAutomatica,
      activo: valores.activo,
    };

    const manejadores = {
      onSuccess: (proveedor: Proveedor) => {
        if (this.editando()) {
          this.guardado.set(proveedor.nombre);
          this.enfocarDespuesDePintar(() => this.avisoGuardado()?.nativeElement);
          return;
        }
        void this.router.navigate(['..'], { relativeTo: this.route });
      },
      onError: (error: unknown) =>
        this.error.set(mensajeDeError(error, this.transloco, 'admin.proveedores.formulario.error')),
    };

    if (this.editando()) {
      this.editar.mutate({ id: this.id(), datos }, manejadores);
    } else {
      this.crear.mutate(datos, manejadores);
    }
  }

  protected preguntarSiEliminar(): void {
    this.errorEliminar.set(null);
    this.confirmandoEliminar.set(true);
    // Sin esto, tabular desde "Eliminar" salta directo a "Sí, eliminar" y se confirma sin haber
    // pasado por la advertencia de que no hay vuelta atrás.
    this.enfocarDespuesDePintar(() => this.cajaEliminar()?.nativeElement);
  }

  protected cancelarEliminar(): void {
    this.confirmandoEliminar.set(false);
    this.errorEliminar.set(null);
    this.enfocarDespuesDePintar(() => this.botonEliminar()?.nativeElement.querySelector('button'));
  }

  /** Lo eliminado ya no tiene ficha: se vuelve a la lista. */
  protected eliminarProveedor(): void {
    if (this.eliminando()) {
      return;
    }
    this.errorEliminar.set(null);
    this.eliminar.mutate(this.id(), {
      onSuccess: () => void this.router.navigate(['..'], { relativeTo: this.route }),
      onError: (error: unknown) => this.errorEliminar.set(this.mensajeAlEliminar(error)),
    });
  }

  /** Con productos, cuántos: es lo que dice que la salida es desactivarlo y no reintentar. */
  private mensajeAlEliminar(error: unknown): string {
    if (error instanceof ErrorHttp && error.codigo === 'PROVEEDOR_CON_PRODUCTOS') {
      return this.transloco.translate('admin.proveedores.eliminar.conProductos', {
        productos: error.datos['productos'] ?? '',
      });
    }
    return mensajeDeError(error, this.transloco, 'admin.proveedores.eliminar.error');
  }
}
