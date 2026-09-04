import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsBoton } from '../../../../shared/ts-boton/ts-boton';
import { TsCampo } from '../../../../shared/ts-campo/ts-campo';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { OpcionSelect, TsSelect } from '../../../../shared/ts-select/ts-select';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { CheckoutStore } from '../../application/checkout.store';
import { DEPARTAMENTOS, municipiosDeDepartamento } from '../../domain/geografia-co';
import { Direccion, TipoEntrega } from '../../domain/pedido.model';
import { requiereDireccion as tipoEntregaRequiereDireccion } from '../../domain/reglas-pedido';

interface ValoresDireccion {
  codigoDaneDepartamento: string;
  codigoDaneCiudad: string;
  direccion: string;
  indicaciones: string;
}

/**
 * Primer paso del checkout (Fase 3, `docs/09-plan-de-arranque.md`): reconcilia
 * el carrito ya existente (`CarritoStore`, con su excepción documentada a
 * "siempre precargar en el resolver" — el id vive en `localStorage`) y recoge
 * correo, tipo de entrega y dirección. El resumen es una vista, no la verdad:
 * precio y existencia los revalida el servidor al crear el pedido
 * (`apps/web/CLAUDE.md`).
 */
@Component({
  selector: 'app-resumen',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    NgOptimizedImage,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsEsqueleto,
    TsPrecio,
    TsSelect,
  ],
  templateUrl: './resumen.page.html',
  styleUrl: './resumen.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResumenPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  protected readonly carrito = inject(CarritoStore);
  private readonly checkout = inject(CheckoutStore);

  protected readonly subtotal = computed(() => {
    const datos = this.carrito.consulta.data();
    if (!datos) {
      return 0;
    }
    return datos.lineas.reduce((suma, linea) => {
      const snapshot = this.carrito.snapshotDeLinea(linea.varianteId);
      return suma + (snapshot ? snapshot.precioValor * linea.cantidad : 0);
    }, 0);
  });

  protected readonly form = new FormGroup({
    correo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    tipoEntrega: new FormControl<TipoEntrega>('ENVIO_A_DOMICILIO', { nonNullable: true }),
    direccion: new FormGroup({
      codigoDaneDepartamento: new FormControl('', { nonNullable: true }),
      codigoDaneCiudad: new FormControl('', { nonNullable: true }),
      direccion: new FormControl('', { nonNullable: true }),
      indicaciones: new FormControl('', { nonNullable: true }),
    }),
  });

  private readonly tipoEntregaElegido = toSignal(this.form.controls.tipoEntrega.valueChanges, {
    initialValue: this.form.controls.tipoEntrega.value,
  });

  protected readonly requiereDireccion = computed(() => tipoEntregaRequiereDireccion(this.tipoEntregaElegido()));

  protected readonly opcionesTipoEntrega = computed<OpcionSelect[]>(() => [
    { valor: 'ENVIO_A_DOMICILIO', etiqueta: this.transloco.translate('checkout.resumen.envio_a_domicilio') },
    { valor: 'RETIRO_EN_PUNTO', etiqueta: this.transloco.translate('checkout.resumen.retiro_en_punto') },
  ]);

  protected readonly opcionesDepartamento = computed<OpcionSelect[]>(() =>
    DEPARTAMENTOS.map((departamento) => ({ valor: departamento.codigo, etiqueta: departamento.nombre })),
  );

  private readonly departamentoElegido = toSignal(
    this.form.controls.direccion.controls.codigoDaneDepartamento.valueChanges,
    { initialValue: this.form.controls.direccion.controls.codigoDaneDepartamento.value },
  );

  protected readonly opcionesCiudad = computed<OpcionSelect[]>(() =>
    municipiosDeDepartamento(this.departamentoElegido()).map((municipio) => ({
      valor: municipio.codigo,
      etiqueta: municipio.nombre,
    })),
  );

  private readonly tickCorreo = toSignal(this.form.controls.correo.events, { initialValue: null });
  protected readonly errorCorreo = computed(() => {
    this.tickCorreo();
    const control = this.form.controls.correo;
    if (!control.touched || control.valid) {
      return null;
    }
    return control.hasError('required')
      ? this.transloco.translate('checkout.resumen.errores.correo_requerido')
      : this.transloco.translate('checkout.resumen.errores.correo_invalido');
  });

  private readonly tickDepartamento = toSignal(
    this.form.controls.direccion.controls.codigoDaneDepartamento.events,
    { initialValue: null },
  );
  protected readonly errorDepartamento = computed(() => {
    this.tickDepartamento();
    const control = this.form.controls.direccion.controls.codigoDaneDepartamento;
    return control.touched && control.invalid
      ? this.transloco.translate('checkout.resumen.errores.departamento_requerido')
      : null;
  });

  private readonly tickCiudad = toSignal(this.form.controls.direccion.controls.codigoDaneCiudad.events, {
    initialValue: null,
  });
  protected readonly errorCiudad = computed(() => {
    this.tickCiudad();
    const control = this.form.controls.direccion.controls.codigoDaneCiudad;
    return control.touched && control.invalid
      ? this.transloco.translate('checkout.resumen.errores.ciudad_requerida')
      : null;
  });

  private readonly tickDireccion = toSignal(this.form.controls.direccion.controls.direccion.events, {
    initialValue: null,
  });
  protected readonly errorDireccion = computed(() => {
    this.tickDireccion();
    const control = this.form.controls.direccion.controls.direccion;
    return control.touched && control.invalid
      ? this.transloco.translate('checkout.resumen.errores.direccion_requerida')
      : null;
  });

  constructor() {
    // La ciudad depende del departamento elegido: si cambia el departamento,
    // la ciudad ya elegida puede no pertenecerle.
    this.form.controls.direccion.controls.codigoDaneDepartamento.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.direccion.controls.codigoDaneCiudad.setValue(''));

    // Retiro en punto no lleva dirección (`Pedido.java`): los campos solo son
    // obligatorios cuando el tipo de entrega los exige.
    effect(() => {
      const necesitaDireccion = this.requiereDireccion();
      const controles = this.form.controls.direccion.controls;
      for (const control of [controles.codigoDaneDepartamento, controles.codigoDaneCiudad, controles.direccion]) {
        control.setValidators(necesitaDireccion ? [Validators.required] : []);
        control.updateValueAndValidity({ emitEvent: false });
      }
    });
  }

  protected enviar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    const valores = this.form.getRawValue();
    const direccion = this.direccionDesdeFormulario(valores.direccion);

    this.checkout.guardarDatosEntrega({
      correo: valores.correo,
      tipoEntrega: valores.tipoEntrega,
      direccion,
    });

    // Método de pago es el paso 4b, todavía sin construir.
    void this.router.navigate(['../metodo-pago'], { relativeTo: this.route });
  }

  private direccionDesdeFormulario(valores: ValoresDireccion): Direccion | null {
    if (!this.requiereDireccion()) {
      return null;
    }
    const departamento = DEPARTAMENTOS.find((d) => d.codigo === valores.codigoDaneDepartamento);
    const ciudad = municipiosDeDepartamento(valores.codigoDaneDepartamento).find(
      (m) => m.codigo === valores.codigoDaneCiudad,
    );
    return {
      codigoDaneDepartamento: valores.codigoDaneDepartamento,
      departamento: departamento?.nombre ?? '',
      codigoDaneCiudad: valores.codigoDaneCiudad,
      ciudad: ciudad?.nombre ?? '',
      direccion: valores.direccion,
      indicaciones: valores.indicaciones || null,
    };
  }
}
