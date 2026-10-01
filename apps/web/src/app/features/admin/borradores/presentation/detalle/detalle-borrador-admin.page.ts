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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { formatearPrecio } from '../../../../../shared/ts-precio/formato-precio';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsCheckbox } from '../../../../../shared/ui/checkbox/ts-checkbox';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarOpcionesFiltro } from '../../../../catalogo/application/listar-opciones-filtro.consulta';
import { hojasConRuta } from '../../../../catalogo/domain/arbol-categorias';
import { claveDeLinea } from '../../../../catalogo/domain/filtro-productos.model';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarProveedoresAdmin } from '../../../proveedores/application/listar-proveedores.consulta';
import {
  usarAprobarBorrador,
  usarEditarBorrador,
  usarRechazarBorrador,
} from '../../application/decidir-borrador.mutacion';
import { usarVerBorrador } from '../../application/ver-borrador.consulta';
import {
  Borrador,
  borradorEditable,
  EstadoBorrador,
  MAXIMO_FOTOS_POR_PRODUCTO,
  Tallas,
  TIPOS_PRODUCTO_PROVEEDOR,
  TipoDeTalla,
  TipoProductoProveedor,
} from '../../domain/borrador.model';
import { clasesDeEstadoBorrador } from '../estado-borrador';

const TIPOS_DE_TALLA: readonly TipoDeTalla[] = ['DESCONOCIDA', 'UNICA', 'LISTA'];

/** «S, M, L» o «S M L»: la lista de tallas y los tonos se teclean separados por coma o espacio. */
function separar(texto: string): string[] {
  return texto
    .split(/[,\s]+/)
    .map((parte) => parte.trim())
    .filter((parte) => parte.length > 0);
}

/** Una característica por línea: es lo que mejor se lee al corregir lo que sacó la extracción. */
function separarLineas(texto: string): string[] {
  return texto
    .split('\n')
    .map((linea) => linea.trim())
    .filter((linea) => linea.length > 0);
}

function enteroPositivo(texto: string): number | null {
  const valor = Number(texto.replace(/[.\s]/g, ''));
  return Number.isInteger(valor) && valor > 0 ? valor : null;
}

/**
 * La revisión de un borrador: lo que el proveedor escribió, las fotos, lo que la extracción
 * entendió, y las tres salidas —corregir, aprobar, rechazar—.
 *
 * <p>Aprobar es lo que crea el producto: pide la marca, la categoría hoja, el precio de venta
 * definitivo, los textos alternativos de las fotos y cuántas unidades entran por variante. El
 * tono de cada foto se elige aquí y no en el formulario de datos, porque es una decisión sobre la
 * foto y no sobre el producto: sin tono, la foto vale para todos.
 *
 * <p>Un borrador que ya no está en revisión se muestra de solo lectura, con el enlace al producto
 * que creó o el motivo del rechazo.
 */
@Component({
  selector: 'app-detalle-borrador-admin',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsCheckbox,
    TsEsqueleto,
    TsMigas,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './detalle-borrador-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleBorradorAdminPage {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  private readonly paramMap = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  protected readonly id = computed(() => this.paramMap().get('id') ?? '');

  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.borradores.titulo', ruta: ['borradores'] },
    { clave: 'admin.borradores.detalle.miga' },
  ]);

  protected readonly consulta = usarVerBorrador(this.id);
  private readonly proveedores = usarProveedoresAdmin();
  private readonly opciones = usarOpcionesFiltro();
  private readonly editar = usarEditarBorrador();
  private readonly aprobar = usarAprobarBorrador();
  private readonly rechazar = usarRechazarBorrador();

  protected readonly borrador = computed<Borrador | null>(
    () => this.consulta.data()?.borrador ?? null,
  );
  protected readonly fotos = computed(() => this.consulta.data()?.fotos ?? []);
  protected readonly textos = computed(() => this.consulta.data()?.textos ?? []);
  protected readonly editable = computed(() => {
    const borrador = this.borrador();
    return borrador !== null && borradorEditable(borrador);
  });
  protected readonly idioma = computed(() => this.transloco.activeLang());

  protected readonly nombreDelProveedor = computed(() => {
    const borrador = this.borrador();
    return (
      (this.proveedores.data() ?? []).find((p) => p.id === borrador?.proveedorId)?.nombre ?? ''
    );
  });

  protected readonly guardando = computed(() => this.editar.isPending());
  protected readonly aprobando = computed(() => this.aprobar.isPending());
  protected readonly rechazando = computed(() => this.rechazar.isPending());

  protected readonly errorDatos = signal<string | null>(null);
  protected readonly avisoDatos = signal<string | null>(null);
  protected readonly errorDecision = signal<string | null>(null);
  /** Aparte del de aprobar: cada error se pinta dentro de la tarjeta del formulario que lo causó. */
  protected readonly errorRechazo = signal<string | null>(null);
  /** El producto recién creado, para enlazarlo desde el aviso. */
  protected readonly aprobado = signal<string | null>(null);
  protected readonly rechazado = signal(false);

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly avisoDatosRef = viewChild<ElementRef<HTMLElement>>('avisoDatosRef');
  private readonly avisoDecisionRef = viewChild<ElementRef<HTMLElement>>('avisoDecisionRef');

  protected readonly formDatos = new FormGroup({
    titulo: new FormControl('', { nonNullable: true }),
    tipo: new FormControl<TipoProductoProveedor>('OTRO', { nonNullable: true }),
    precioVentaSugerido: new FormControl('', { nonNullable: true }),
    tipoDeTalla: new FormControl<TipoDeTalla>('DESCONOCIDA', { nonNullable: true }),
    sirveHasta: new FormControl('', { nonNullable: true }),
    tallas: new FormControl('', { nonNullable: true }),
    cantidadTonos: new FormControl('0', { nonNullable: true }),
    tonosNombrados: new FormControl('', { nonNullable: true }),
    material: new FormControl('', { nonNullable: true }),
    caracteristicas: new FormControl('', { nonNullable: true }),
  });

  protected readonly formAprobar = new FormGroup({
    marcaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    categoriaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    precioVenta: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    existenciaInicial: new FormControl('1', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    descripcion: new FormControl('', { nonNullable: true }),
    altEs: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    altEn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly motivoRechazo = new FormControl('', { nonNullable: true });

  /** El tono elegido por foto, por `mensajeId`. Vacío = la foto vale para todos los tonos. */
  protected readonly tonoPorFoto = signal<Readonly<Record<string, string>>>({});

  /**
   * Las fotos que NO entran al producto, por `mensajeId`. Se guardan las excluidas y no las
   * elegidas para que una foto nueva en una revalidación entre por omisión. Al cargar, las que
   * sobrepasan el máximo quedan fuera: una publicación de ropa trae doce o catorce.
   */
  protected readonly fotosExcluidas = signal<ReadonlySet<string>>(new Set());
  protected readonly maximoFotos = MAXIMO_FOTOS_POR_PRODUCTO;

  protected readonly fotosElegidas = computed(() =>
    this.fotos().filter((foto) => !this.fotosExcluidas().has(foto.mensajeId)),
  );

  protected readonly opcionesTipo = computed<OpcionSelect[]>(() =>
    TIPOS_PRODUCTO_PROVEEDOR.map((tipo) => ({
      valor: tipo,
      etiqueta: this.traducir()('admin.borradores.tipos.' + tipo),
    })),
  );

  protected readonly opcionesTipoDeTalla = computed<OpcionSelect[]>(() =>
    TIPOS_DE_TALLA.map((tipo) => ({
      valor: tipo,
      etiqueta: this.traducir()('admin.borradores.tallas.' + tipo),
    })),
  );

  protected readonly opcionesMarca = computed<OpcionSelect[]>(() =>
    (this.opciones.marcas.data() ?? []).map((marca) => ({
      valor: marca.id,
      etiqueta: marca.nombre,
    })),
  );

  /** Solo las hojas, con su ruta: mismo criterio que el alta de producto. */
  protected readonly opcionesCategoria = computed<OpcionSelect[]>(() => {
    const traducir = this.traducir();
    return hojasConRuta(this.opciones.categorias.data() ?? [], (linea) =>
      traducir(claveDeLinea(linea)),
    ).map((hoja) => ({ valor: hoja.categoria.id, etiqueta: hoja.ruta }));
  });

  /** Los tonos que se pueden asignar a una foto: los que el borrador nombra. */
  protected readonly opcionesTono = computed<OpcionSelect[]>(() =>
    (this.borrador()?.tonosNombrados ?? []).map((tono) => ({ valor: tono, etiqueta: tono })),
  );

  private cargado: string | null = null;

  constructor() {
    effect(() => {
      const borrador = this.borrador();
      if (!borrador || this.cargado === borrador.id) {
        return;
      }
      this.cargado = borrador.id;
      // Todo lo que es de la decisión anterior se va con ella: navegar de un borrador a otro por
      // la URL reutiliza el componente.
      this.tonoPorFoto.set({});
      this.fotosExcluidas.set(
        new Set(
          this.fotos()
            .slice(MAXIMO_FOTOS_POR_PRODUCTO)
            .map((foto) => foto.mensajeId),
        ),
      );
      this.aprobado.set(null);
      this.rechazado.set(false);
      this.avisoDatos.set(null);
      this.errorDatos.set(null);
      this.errorDecision.set(null);
      this.errorRechazo.set(null);
      this.motivoRechazo.reset();
      this.formDatos.reset(this.aFormularioDatos(borrador));
      // El alt en inglés no se prellena con el título en español: obligatorio y vacío, para que
      // alguien lo escriba y la vitrina en inglés no herede un alt en castellano.
      this.formAprobar.reset({
        precioVenta:
          borrador.precioVentaSugerido === null ? '' : String(borrador.precioVentaSugerido),
        existenciaInicial: '1',
        altEs: borrador.titulo,
        altEn: '',
      });
      // Un borrador decidido se lee, no se corrige: los campos quedan deshabilitados y no solo
      // sin botón.
      if (borradorEditable(borrador)) {
        this.formDatos.enable({ emitEvent: false });
      } else {
        this.formDatos.disable({ emitEvent: false });
      }
    });
  }

  private aFormularioDatos(borrador: Borrador) {
    return {
      titulo: borrador.titulo,
      tipo: borrador.tipo,
      precioVentaSugerido:
        borrador.precioVentaSugerido === null ? '' : String(borrador.precioVentaSugerido),
      tipoDeTalla: borrador.tallas.tipo,
      sirveHasta: borrador.tallas.sirveHasta ?? '',
      tallas: borrador.tallas.valores.join(', '),
      cantidadTonos: String(borrador.cantidadTonos),
      tonosNombrados: borrador.tonosNombrados.join(', '),
      material: borrador.material ?? '',
      caracteristicas: borrador.caracteristicas.join('\n'),
    };
  }

  private tallasDelFormulario(): Tallas {
    const valores = this.formDatos.getRawValue();
    switch (valores.tipoDeTalla) {
      case 'UNICA':
        return { tipo: 'UNICA', sirveHasta: valores.sirveHasta.trim() || null, valores: [] };
      case 'LISTA':
        return { tipo: 'LISTA', sirveHasta: null, valores: separar(valores.tallas) };
      default:
        return { tipo: 'DESCONOCIDA', sirveHasta: null, valores: [] };
    }
  }

  protected etiquetaEstado(estado: EstadoBorrador): string {
    return this.traducir()('admin.borradores.estados.' + estado);
  }

  protected clasesEstado(estado: EstadoBorrador): string {
    return clasesDeEstadoBorrador(estado);
  }

  protected etiquetaAlerta(alerta: string): string {
    return this.traducir()('admin.borradores.alertas.' + alerta);
  }

  protected precio(valor: number | null): string {
    return valor === null ? '—' : formatearPrecio(valor, 'COP', this.transloco.activeLang());
  }

  protected tonoDe(mensajeId: string): string {
    return this.tonoPorFoto()[mensajeId] ?? '';
  }

  protected elegirTono(mensajeId: string, tono: string): void {
    this.tonoPorFoto.update((actual) => ({ ...actual, [mensajeId]: tono }));
  }

  protected fotoIncluida(mensajeId: string): boolean {
    return !this.fotosExcluidas().has(mensajeId);
  }

  protected incluirFoto(mensajeId: string, incluir: boolean): void {
    this.fotosExcluidas.update((actual) => {
      const siguiente = new Set(actual);
      if (incluir) {
        siguiente.delete(mensajeId);
      } else {
        siguiente.add(mensajeId);
      }
      return siguiente;
    });
  }

  /** Cuál es la principal: la primera de las elegidas, en el orden de la publicación. */
  protected esPrincipal(mensajeId: string): boolean {
    return this.fotosElegidas()[0]?.mensajeId === mensajeId;
  }

  protected guardarDatos(): void {
    if (this.guardando()) {
      return;
    }
    const valores = this.formDatos.getRawValue();
    const precio = valores.precioVentaSugerido.trim();
    const precioSugerido = precio === '' ? null : enteroPositivo(precio);
    const cantidadTonos = Number(valores.cantidadTonos);
    if ((precio !== '' && precioSugerido === null) || !Number.isInteger(cantidadTonos)) {
      this.errorDatos.set(this.transloco.translate('admin.borradores.datos.invalidos'));
      return;
    }
    this.errorDatos.set(null);
    this.avisoDatos.set(null);

    this.editar.mutate(
      {
        id: this.id(),
        cambios: {
          titulo: valores.titulo.trim(),
          tipo: valores.tipo,
          ...(precioSugerido !== null ? { precioVentaSugerido: precioSugerido } : {}),
          tallas: this.tallasDelFormulario(),
          cantidadTonos,
          tonosNombrados: separar(valores.tonosNombrados),
          material: valores.material.trim(),
          caracteristicas: separarLineas(valores.caracteristicas),
        },
      },
      {
        onSuccess: (borrador) => {
          this.avisoDatos.set(borrador.titulo);
          // Lo que se acaba de corregir alimenta la aprobación: el precio y el alt parten de ahí.
          this.formAprobar.patchValue({
            precioVenta:
              borrador.precioVentaSugerido === null ? '' : String(borrador.precioVentaSugerido),
          });
          this.enfocarDespuesDePintar(() => this.avisoDatosRef()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.errorDatos.set(
            mensajeDeError(error, this.transloco, 'admin.borradores.datos.error'),
          ),
      },
    );
  }

  protected aprobarBorrador(): void {
    if (this.aprobando() || this.rechazando()) {
      return;
    }
    const valores = this.formAprobar.getRawValue();
    const precioVenta = enteroPositivo(valores.precioVenta);
    const existencia = Number(valores.existenciaInicial);
    if (
      this.formAprobar.invalid ||
      precioVenta === null ||
      !Number.isInteger(existencia) ||
      existencia < 0
    ) {
      this.formAprobar.markAllAsTouched();
      this.errorDecision.set(this.transloco.translate('admin.borradores.aprobar.faltanCampos'));
      return;
    }
    const elegidas = this.fotosElegidas();
    if (elegidas.length === 0) {
      this.errorDecision.set(this.transloco.translate('admin.borradores.aprobar.sinFotosElegidas'));
      return;
    }
    if (elegidas.length > MAXIMO_FOTOS_POR_PRODUCTO) {
      this.errorDecision.set(
        this.transloco.translate('admin.borradores.aprobar.demasiadasFotos', {
          maximo: MAXIMO_FOTOS_POR_PRODUCTO,
        }),
      );
      return;
    }
    this.errorDecision.set(null);

    const tonos = this.tonoPorFoto();
    const datos = this.formDatos.getRawValue();
    this.aprobar.mutate(
      {
        id: this.id(),
        aprobacion: {
          // Lo que está escrito en "Datos extraídos" manda, se haya guardado o no: la explicación
          // de esa tarjeta promete que la aprobación parte de ahí.
          ...(datos.titulo.trim() ? { titulo: datos.titulo.trim() } : {}),
          tallas: this.tallasDelFormulario(),
          marcaId: valores.marcaId,
          categoriaId: valores.categoriaId,
          precioVenta,
          existenciaInicial: existencia,
          ...(valores.descripcion.trim() ? { descripcion: valores.descripcion.trim() } : {}),
          altEs: valores.altEs.trim(),
          altEn: valores.altEn.trim(),
          fotos: elegidas.map((foto) => ({
            mensajeId: foto.mensajeId,
            tono: tonos[foto.mensajeId] || null,
            colorHex: null,
          })),
        },
      },
      {
        onSuccess: (borrador) => {
          this.aprobado.set(borrador.productoId);
          this.enfocarDespuesDePintar(() => this.avisoDecisionRef()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.errorDecision.set(
            mensajeDeError(error, this.transloco, 'admin.borradores.aprobar.error'),
          ),
      },
    );
  }

  protected rechazarBorrador(): void {
    if (this.aprobando() || this.rechazando()) {
      return;
    }
    const motivo = this.motivoRechazo.value.trim();
    if (!motivo) {
      this.errorRechazo.set(this.transloco.translate('admin.borradores.rechazar.faltaMotivo'));
      return;
    }
    this.errorRechazo.set(null);

    this.rechazar.mutate(
      { id: this.id(), motivo },
      {
        onSuccess: () => {
          this.rechazado.set(true);
          this.enfocarDespuesDePintar(() => this.avisoDecisionRef()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.errorRechazo.set(
            mensajeDeError(error, this.transloco, 'admin.borradores.rechazar.error'),
          ),
      },
    );
  }
}
