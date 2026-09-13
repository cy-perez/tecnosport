import { NgOptimizedImage } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsCheckbox } from '../../../../shared/ui/checkbox/ts-checkbox';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { formatearPrecio } from '../../../../shared/ts-precio/formato-precio';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { OpcionSelect, TsSelect } from '../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../shared/ui/select/ts-select-control';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { CheckoutStore } from '../../application/checkout.store';
import { usarCotizacionEnvio } from '../../application/cotizacion-envio.consulta';
import { CotizacionEnvio, CotizarEnvioComando } from '../../domain/envio.model';
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
    NgOptimizedImage,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsCheckbox,
    TsEsqueleto,
    TsPrecio,
    TsSelect,
    TsSelectControl,
    RouterLink,
  ],
  templateUrl: './resumen.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResumenPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);

  /** Mismo patrón que el pie: las rutas viven bajo /:lang. */
  protected readonly idioma = this.transloco.activeLang;
  private readonly traducir = usarTraductor();
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

  /**
   * La cotización de envío se pide aquí y no en un paso aparte porque aquí es
   * donde el comprador escribe la ciudad. El artículo 50 de la Ley 1480 de 2011
   * exige mostrar el costo del envío separado y el total antes de finalizar la
   * compra, y esta es la pantalla donde se finaliza.
   */
  protected readonly cotizacion = usarCotizacionEnvio(() => this.criteriosCotizacion());

  /**
   * La última cotización que sí salió, para poder decir cuánto se ahorra quien
   * cambia a recogida en el punto. Sin esto, "te ahorras X" no tendría de dónde
   * sacar la X — y un ahorro inventado es peor que no mostrarlo.
   */
  private readonly ultimaCotizacion = signal<CotizacionEnvio | null>(null);

  protected readonly form = new FormGroup({
    // Quien recibe: va en la guía de la transportadora y es a quien llama el mensajero. El
    // patrón del teléfono es laxo a propósito —dígitos, espacios, paréntesis, guiones y un `+`
    // opcional—: el servidor lo normaliza y es quien decide (`Contacto.java`); aquí solo se evita
    // que una letra llegue hasta allá.
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    telefono: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\+?[0-9 ()\-.]{7,20}$/)],
    }),
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
    // requiredTrue, y arranca en false: la casilla nunca puede venir premarcada — sin acción del
    // titular no hay autorización válida (Ley 1581 de 2012).
    autorizaDatos: new FormControl(false, {
      nonNullable: true,
      validators: [Validators.requiredTrue],
    }),
  });

  private readonly tipoEntregaElegido = toSignal(this.form.controls.tipoEntrega.valueChanges, {
    initialValue: this.form.controls.tipoEntrega.value,
  });

  protected readonly requiereDireccion = computed(() =>
    tipoEntregaRequiereDireccion(this.tipoEntregaElegido()),
  );

  protected readonly opcionesTipoEntrega = computed<OpcionSelect[]>(() => [
    { valor: 'ENVIO_A_DOMICILIO', etiqueta: this.traducir()('checkout.resumen.envio_a_domicilio') },
    { valor: 'RETIRO_EN_PUNTO', etiqueta: this.traducir()('checkout.resumen.retiro_en_punto') },
  ]);

  protected readonly opcionesDepartamento = computed<OpcionSelect[]>(() =>
    DEPARTAMENTOS.map((departamento) => ({
      valor: departamento.codigo,
      etiqueta: departamento.nombre,
    })),
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

  private readonly tickNombre = toSignal(this.form.controls.nombre.events, { initialValue: null });
  protected readonly errorNombre = computed(() => {
    this.tickNombre();
    const control = this.form.controls.nombre;
    return control.touched && control.invalid
      ? this.transloco.translate('checkout.resumen.errores.nombre_requerido')
      : null;
  });

  private readonly tickTelefono = toSignal(this.form.controls.telefono.events, {
    initialValue: null,
  });
  protected readonly errorTelefono = computed(() => {
    this.tickTelefono();
    const control = this.form.controls.telefono;
    if (!control.touched || control.valid) {
      return null;
    }
    return control.hasError('required')
      ? this.transloco.translate('checkout.resumen.errores.telefono_requerido')
      : this.transloco.translate('checkout.resumen.errores.telefono_invalido');
  });

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

  /**
   * Sin esto, quien no marca la casilla pulsa «Continuar» y no pasa absolutamente nada: el
   * formulario es inválido, `enviar()` marca todo como tocado y vuelve, pero la casilla no tenía
   * dónde mostrar su error. Lo encontró el recorrido de Playwright, no las pruebas unitarias —esas
   * comprobaban que no se guardara el borrador, que es cierto, pero no que se le dijera al
   * comprador por qué.
   */
  private readonly tickAutorizacion = toSignal(this.form.controls.autorizaDatos.events, {
    initialValue: null,
  });
  protected readonly errorAutorizacion = computed(() => {
    this.tickAutorizacion();
    const control = this.form.controls.autorizaDatos;
    return control.touched && control.invalid
      ? this.transloco.translate('checkout.resumen.errores.autorizacion_requerida')
      : null;
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

  private readonly tickCiudad = toSignal(
    this.form.controls.direccion.controls.codigoDaneCiudad.events,
    {
      initialValue: null,
    },
  );
  protected readonly errorCiudad = computed(() => {
    this.tickCiudad();
    const control = this.form.controls.direccion.controls.codigoDaneCiudad;
    return control.touched && control.invalid
      ? this.transloco.translate('checkout.resumen.errores.ciudad_requerida')
      : null;
  });

  private readonly tickDireccion = toSignal(
    this.form.controls.direccion.controls.direccion.events,
    {
      initialValue: null,
    },
  );
  protected readonly errorDireccion = computed(() => {
    this.tickDireccion();
    const control = this.form.controls.direccion.controls.direccion;
    return control.touched && control.invalid
      ? this.transloco.translate('checkout.resumen.errores.direccion_requerida')
      : null;
  });

  /** Mismo patrón de tick que los errores de campo: se relee el control. */
  private readonly tickGrupoDireccion = toSignal(this.form.controls.direccion.valueChanges, {
    initialValue: null,
  });

  /**
   * `null` deshabilita la consulta. Pasa en tres casos y en los tres es
   * correcto no llamar al proveedor: recogida en el punto (no hay a dónde
   * despachar), carrito vacío, o dirección todavía a medio llenar.
   */
  protected readonly criteriosCotizacion = computed<CotizarEnvioComando | null>(() => {
    if (!this.requiereDireccion()) {
      return null;
    }
    const lineas = (this.carrito.consulta.data()?.lineas ?? []).map((linea) => ({
      varianteId: linea.varianteId,
      cantidad: linea.cantidad,
    }));
    if (lineas.length === 0) {
      return null;
    }
    this.tickGrupoDireccion();
    const direccion = this.direccionDesdeValores(this.form.controls.direccion.getRawValue());
    if (!direccion.codigoDaneCiudad || !direccion.direccion.trim()) {
      return null;
    }
    return { lineas, direccion };
  });

  protected readonly cotizando = computed(
    () => this.criteriosCotizacion() !== null && this.cotizacion.isFetching(),
  );

  /**
   * Sin cobertura es un dato, no un error: la consulta terminó bien y la
   * respuesta fue "nadie llega ahí". Se distingue de `isError()`, que es "no se
   * pudo preguntar" — mandar a cambiar una dirección que estaba bien por una
   * caída nuestra sería culpar al comprador.
   */
  protected readonly sinCobertura = computed(
    () =>
      this.criteriosCotizacion() !== null &&
      this.cotizacion.isSuccess() &&
      this.cotizacion.data() === null,
  );

  /**
   * "No se pudo preguntar", el otro lado de `sinCobertura`. Tenía que existir
   * aquí y no solo en el texto: sin él, una cotización caída no pintaba nada en
   * absoluto —ni costo ni motivo— y el total de abajo se quedaba en el subtotal,
   * que es un total que no es el total. Sigue adelante nadie: el flete que se
   * cobra sale del servidor, y sin tarifa no hay pedido que crear.
   */
  protected readonly errorCotizacion = computed(
    () => this.criteriosCotizacion() !== null && this.cotizacion.isError(),
  );

  /**
   * Solo hay total que mostrar cuando hay con qué sumarlo. A domicilio y sin
   * tarifa —sin cobertura, o la consulta caída— el subtotal no es el total, y
   * pintarlo como si lo fuera es justo lo que el artículo 50 de la Ley 1480 de
   * 2011 no permite antes de finalizar la transacción.
   *
   * `!= null` y no `!== null`, y la diferencia no es de estilo: TanStack
   * devuelve `undefined` mientras no hay datos y `null` es aquí un dato de
   * verdad ("nadie llega ahí"). Con `!==` esto valía `true` mientras la
   * cotización iba en vuelo y con la consulta caída, y el total falso volvía a
   * aparecer en los dos casos. Lo encontró el recorrido en el navegador contra
   * el backend real, no las pruebas: en jsdom la cotización responde al
   * instante y ese estado intermedio casi no existe.
   */
  protected readonly totalConocido = computed(
    () => !this.requiereDireccion() || this.cotizacion.data() != null,
  );

  protected readonly costoEnvio = computed(() => this.cotizacion.data()?.costoEnvio ?? 0);

  protected readonly total = computed(() => this.subtotal() + this.costoEnvio());

  /**
   * Solo si se llegó a cotizar: un ahorro que nadie calculó no se muestra. Va
   * ya formateado porque se interpola dentro de una frase, no se pinta como
   * elemento aparte.
   */
  protected readonly ahorroPorRecoger = computed(() => {
    const cotizada = this.ultimaCotizacion();
    if (!cotizada || this.requiereDireccion()) {
      return null;
    }
    return formatearPrecio(cotizada.costoEnvio, cotizada.moneda, this.idioma());
  });

  /**
   * Sin tarifa no se puede confirmar un envío a domicilio: el pedido respondería el mismo 409 dos
   * pantallas después. No deshabilita el botón —eso lo sacaría del orden de tabulación— sino que
   * corta en `enviar()`, con el motivo ya visible y anunciado.
   *
   * <p>Está escrito como **"exijo una tarifa"** y no como "sé que no hay tarifa", y esa diferencia
   * es un segundo error que solo apareció en el navegador. La versión anterior era `sinCobertura()
   * || errorCotizacion()`, y las dos miran un estado terminal de la consulta: entre el momento en
   * que la dirección queda completa y el momento en que TanStack arranca la petición —su registro
   * ocurre en un `effect()`, agendado async, el mismo detalle que documenta `apps/web/CLAUDE.md`
   * para SSR— no hay ni éxito, ni error, ni `isFetching`. Un clic que caiga en esa rendija veía
   * las dos señales en `false` y pasaba. Preguntar por la tarifa no tiene rendijas: o está, o no
   * se sigue.
   *
   * <p>Los dos motivos por los que puede faltar se distinguen solo para **decirlos**, con textos
   * distintos, en la plantilla.
   */
  protected readonly bloqueadoPorCobertura = computed(() => !this.totalConocido());

  /**
   * Si la consulta ya llegó a un desenlace. Es lo que `enviar()` espera, y por lo mismo de arriba
   * no puede ser `isFetching()`: hay un instante en que la petición todavía no ha arrancado y
   * `isFetching` es `false` sin que haya nada resuelto.
   */
  protected readonly cotizacionResuelta = computed(
    () => !this.requiereDireccion() || this.cotizacion.isSuccess() || this.cotizacion.isError(),
  );

  /**
   * El desenlace de la cotización, como observable, para poder esperarlo en `enviar()`.
   * Se crea aquí —contexto de inyección— y no dentro del método.
   */
  private readonly cotizacionResuelta$ = toObservable(this.cotizacionResuelta);

  /**
   * Solo mientras `enviar()` espera la cotización, no cada vez que hay una en
   * vuelo: el botón no tiene por qué ponerse a cargar mientras el comprador
   * todavía está llenando el formulario.
   */
  protected readonly esperandoCotizacion = signal(false);

  constructor() {
    // Recordar la última cotización buena para poder decir cuánto se ahorra
    // quien cambia a recogida. Se guarda al llegar, no se recalcula después:
    // al cambiar a retiro la consulta se deshabilita y `data()` se vacía.
    //
    // Y se **olvida** cuando la dirección nueva no tiene cobertura. Sin eso, el
    // ahorro de la ciudad anterior sobrevivía al cambio de ciudad: quien cotizó
    // Medellín en 9.540, cambió a Bogotá —sin transporte— y eligió recoger, leía
    // "te ahorras $ 9.540" sin ahorrarse nada, porque a Bogotá no había cómo
    // enviarlo. Lo encontró el recorrido en el navegador, no las pruebas.
    effect(() => {
      if (this.sinCobertura()) {
        this.ultimaCotizacion.set(null);
        return;
      }
      const cotizada = this.cotizacion.data();
      if (cotizada) {
        this.ultimaCotizacion.set(cotizada);
      }
    });

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
      for (const control of [
        controles.codigoDaneDepartamento,
        controles.codigoDaneCiudad,
        controles.direccion,
      ]) {
        control.setValidators(necesitaDireccion ? [Validators.required] : []);
        control.updateValueAndValidity({ emitEvent: false });
      }
    });
  }

  /**
   * Espera la cotización antes de decidir, y esa espera es el arreglo de una
   * carrera real: `bloqueadoPorCobertura` mira `isSuccess()`, así que mientras
   * la consulta iba en vuelo valía `false` y el formulario pasaba. Quien llenaba
   * la dirección y daba clic enseguida —o el recorrido de Playwright, que tarda
   * milisegundos— se saltaba el bloqueo y llegaba a confirmar, donde el pedido
   * respondía 409 y la pantalla le decía "revisa tus datos": no había nada que
   * revisar, la dirección estaba bien.
   *
   * <p>Se espera en vez de bloquear en seco porque bloquear mientras se cotiza
   * es un botón que no hace nada sin decir por qué. Aquí el botón se pone a
   * cargar, y al llegar la respuesta o continúa o explica.
   */
  protected async enviar(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    if (!this.cotizacionResuelta()) {
      this.esperandoCotizacion.set(true);
      try {
        await firstValueFrom(this.cotizacionResuelta$.pipe(filter((resuelta) => resuelta)));
      } finally {
        this.esperandoCotizacion.set(false);
      }
    }

    if (this.bloqueadoPorCobertura()) {
      return;
    }

    const valores = this.form.getRawValue();
    const direccion = this.direccionDesdeFormulario(valores.direccion);

    this.checkout.guardarDatosEntrega({
      correo: valores.correo,
      contacto: { nombre: valores.nombre.trim(), telefono: valores.telefono.trim() },
      tipoEntrega: valores.tipoEntrega,
      direccion,
      autorizaDatos: valores.autorizaDatos,
    });

    // Método de pago es el paso 4b, todavía sin construir.
    void this.router.navigate(['../metodo-pago'], { relativeTo: this.route });
  }

  private direccionDesdeFormulario(valores: ValoresDireccion): Direccion | null {
    return this.requiereDireccion() ? this.direccionDesdeValores(valores) : null;
  }

  private direccionDesdeValores(valores: ValoresDireccion): Direccion {
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
