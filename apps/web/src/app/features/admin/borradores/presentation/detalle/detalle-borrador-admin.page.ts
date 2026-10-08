import {
  TextosSelectorColores,
  TsSelectorColores,
} from '../../../../../shared/ui/selector-colores/ts-selector-colores';
import {
  separarColores,
  unirColores,
} from '../../../../../shared/ui/muestra-color/muestra-color.model';
import { ColorParaElegir, paletaParaElegir } from '../../../../catalogo/domain/producto.model';
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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { usarIdiomaActivo, usarTraductor } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { formatearPrecio } from '../../../../../shared/ts-precio/formato-precio';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsCheckbox } from '../../../../../shared/ui/checkbox/ts-checkbox';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarOpcionesDeFormulario } from '../../../../catalogo/application/listar-opciones-filtro.consulta';
import { usarPaletaDeColores } from '../../../../catalogo/application/listar-paleta-colores.consulta';
import { escalaDeTallasDe, hojasConRuta } from '../../../../catalogo/domain/arbol-categorias';
import { tallaNormalizada } from '../../../../catalogo/domain/seleccion-variante';
import { claveDeLinea } from '../../../../catalogo/domain/filtro-productos.model';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarProveedoresAdmin } from '../../../proveedores/application/listar-proveedores.consulta';
import {
  usarAprobarBorrador,
  usarDescartarFotoBorrador,
  usarEditarBorrador,
  usarEliminarBorrador,
  usarRechazarBorrador,
  usarSubirFotoBorrador,
} from '../../application/decidir-borrador.mutacion';
import { usarVerBorrador } from '../../application/ver-borrador.consulta';
import {
  Borrador,
  borradorBorrable,
  borradorSinProducto,
  borradorEditable,
  CATEGORIA_SUGERIDA_POR_TIPO,
  EstadoBorrador,
  fotoAdmitida,
  MARCA_DE_REPLICAS,
  MAXIMO_FOTOS_POR_PRODUCTO,
  Tallas,
  TIPOS_PRODUCTO_PROVEEDOR,
  TipoDeTalla,
  TipoProductoProveedor,
} from '../../domain/borrador.model';
import {
  AsignacionDePrendas,
  elegirTonoDeFoto,
  moverFotoAPrenda,
  olvidarFotoDePrendas,
  prendasEnUso,
  problemaDePrendas,
  SIN_PRENDAS,
  tonoDeFoto,
  unaSolaPrenda,
  variantesQueSeCrean,
} from '../../domain/prendas';
import { clasesDeEstadoBorrador } from '../estado-borrador';

const TIPOS_DE_TALLA: readonly TipoDeTalla[] = ['DESCONOCIDA', 'UNICA', 'LISTA'];

/** «S, M, L» o «S M L»: la lista de tallas y los tonos se teclean separados por coma o espacio. */
function separar(texto: string): string[] {
  return texto
    .split(/[,\s]+/)
    .map((parte) => parte.trim())
    .filter((parte) => parte.length > 0);
}

/** «Genérica», «GENERICA» y «generica» son la misma marca. */
function sinTildes(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

function enteroPositivo(texto: string): number | null {
  const valor = Number(texto.replace(/[.\s]/g, ''));
  return Number.isInteger(valor) && valor > 0 ? valor : null;
}

/** Una copia del conjunto con la foto dentro o fuera: las señales no se mutan en su sitio. */
function conSinFoto(fotos: ReadonlySet<string>, mensajeId: string, dentro: boolean): Set<string> {
  const siguiente = new Set(fotos);
  if (dentro) {
    siguiente.add(mensajeId);
  } else {
    siguiente.delete(mensajeId);
  }
  return siguiente;
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
    TsSelectorColores,
  ],
  templateUrl: './detalle-borrador-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleBorradorAdminPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
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
  private readonly opciones = usarOpcionesDeFormulario();
  private readonly paleta = usarPaletaDeColores();
  private readonly editar = usarEditarBorrador();
  private readonly aprobar = usarAprobarBorrador();
  private readonly rechazar = usarRechazarBorrador();
  private readonly eliminar = usarEliminarBorrador();
  private readonly descartarFoto = usarDescartarFotoBorrador();
  private readonly subirFoto = usarSubirFotoBorrador();

  protected readonly borrador = computed<Borrador | null>(
    () => this.consulta.data()?.borrador ?? null,
  );
  protected readonly fotos = computed(() => this.consulta.data()?.fotos ?? []);
  protected readonly textos = computed(() => this.consulta.data()?.textos ?? []);
  protected readonly editable = computed(() => {
    const borrador = this.borrador();
    return borrador !== null && borradorEditable(borrador);
  });
  protected readonly borrable = computed(() => {
    const borrador = this.borrador();
    return borrador !== null && borradorBorrable(borrador);
  });
  protected readonly sinProducto = computed(() => {
    const borrador = this.borrador();
    return borrador !== null && borradorSinProducto(borrador);
  });
  // `usarIdiomaActivo` y no un `computed` sobre `activeLang()`, que no lee ninguna señal y no se
  // vuelve a calcular al cambiar de idioma (apps/web/CLAUDE.md).
  protected readonly idioma = usarIdiomaActivo();

  protected readonly nombreDelProveedor = computed(() => {
    const borrador = this.borrador();
    return (
      (this.proveedores.data() ?? []).find((p) => p.id === borrador?.proveedorId)?.nombre ?? ''
    );
  });

  protected readonly guardando = computed(() => this.editar.isPending());
  protected readonly aprobando = computed(() => this.aprobar.isPending());
  protected readonly rechazando = computed(() => this.rechazar.isPending());
  protected readonly borrando = computed(() => this.eliminar.isPending());
  protected readonly descartandoFoto = computed(() => this.descartarFoto.isPending());
  /** Cualquiera de las tres en vuelo bloquea las otras dos: el servidor solo admite una. */
  private readonly decidiendo = computed(
    () => this.aprobando() || this.rechazando() || this.borrando(),
  );

  protected readonly errorDatos = signal<string | null>(null);
  protected readonly avisoDatos = signal<string | null>(null);
  protected readonly errorDecision = signal<string | null>(null);
  /** Aparte del de aprobar: cada error se pinta dentro de la tarjeta del formulario que lo causó. */
  protected readonly errorRechazo = signal<string | null>(null);
  /** El producto recién creado, para enlazarlo desde el aviso. */
  protected readonly aprobado = signal<string | null>(null);
  protected readonly rechazado = signal(false);
  protected readonly confirmandoBorrar = signal(false);
  /** La foto que se está por eliminar, por `mensajeId`; nula si no se pregunta por ninguna. */
  protected readonly confirmandoEliminarFoto = signal<string | null>(null);
  protected readonly errorEliminarFoto = signal<string | null>(null);
  protected readonly fotoEliminada = signal(false);
  protected readonly errorBorrar = signal<string | null>(null);
  /** El avance de la subida en curso; nulo si no se está subiendo nada. */
  protected readonly subiendoFotos = signal<{ hechas: number; total: number } | null>(null);
  /** Cuántas entraron en la última subida; nulo antes de la primera. */
  protected readonly fotosSubidas = signal<number | null>(null);
  /** Un mensaje por archivo que no entró, con su nombre: de varios, puede fallar uno solo. */
  protected readonly erroresSubida = signal<readonly string[]>([]);

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly avisoDatosRef = viewChild<ElementRef<HTMLElement>>('avisoDatosRef');
  private readonly avisoDecisionRef = viewChild<ElementRef<HTMLElement>>('avisoDecisionRef');
  private readonly cajaBorrar = viewChild<ElementRef<HTMLElement>>('cajaBorrar');
  private readonly cajaEliminarFoto = viewChild<ElementRef<HTMLElement>>('cajaEliminarFoto');
  private readonly avisoFotosRef = viewChild<ElementRef<HTMLElement>>('avisoFotosRef');
  private readonly botonBorrar = viewChild('botonBorrar', { read: ElementRef });

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
    // Obligatoria desde el 3 de octubre de 2026: es la descripción de la ficha, y reemplazó a la
    // lista de características.
    descripcion: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly formAprobar = new FormGroup({
    marcaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    categoriaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    precioVenta: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    existenciaInicial: new FormControl('1', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    altEs: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    altEn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly motivoRechazo = new FormControl('', { nonNullable: true });

  /**
   * Qué fotos son la misma prenda y de qué color es cada una (`prendas.ts`). Una foto sin prenda
   * vale para todos los tonos; marcarle un color la vuelve una prenda ella sola.
   */
  protected readonly prendas = signal<AsignacionDePrendas>(SIN_PRENDAS);

  /**
   * Las fotos que alguien desmarcó, por `mensajeId`. Se guardan las excluidas y no las elegidas
   * para que una foto nueva en una revalidación entre por omisión.
   */
  protected readonly fotosExcluidas = signal<ReadonlySet<string>>(new Set());

  /** Las que alguien marcó a mano: entran aunque pasen del tope. */
  protected readonly fotosIncluidasAMano = signal<ReadonlySet<string>>(new Set());
  protected readonly maximoFotos = MAXIMO_FOTOS_POR_PRODUCTO;

  /**
   * Las que entran, por `mensajeId`: las marcadas a mano y, hasta llenar el tope, las demás no
   * desmarcadas en el orden de la publicación. Una publicación de ropa trae doce o catorce.
   *
   * <p><b>El tope se aplica sobre la lista de ahora, no sobre la del momento de cargar</b>, y hasta
   * el 7 de octubre de 2026 no era así: las que pasaban de nueve se apuntaban como excluidas al
   * abrir el borrador, y eliminar una foto después no las devolvía. Un borrador de diez fotos al
   * que se le borró una se aprobó con ocho, y la novena —con su color marcado— no llegó al
   * producto.
   */
  private readonly fotosQueEntran = computed(() => {
    const aMano = this.fotosIncluidasAMano();
    const candidatas = this.fotos().filter((foto) => !this.fotosExcluidas().has(foto.mensajeId));
    let libres =
      MAXIMO_FOTOS_POR_PRODUCTO - candidatas.filter((foto) => aMano.has(foto.mensajeId)).length;
    const entran = new Set<string>();
    for (const foto of candidatas) {
      if (aMano.has(foto.mensajeId)) {
        entran.add(foto.mensajeId);
      } else if (libres > 0) {
        entran.add(foto.mensajeId);
        libres--;
      }
    }
    return entran;
  });

  /**
   * La foto que la persona marcó como principal, por `mensajeId`; nula si no marcó ninguna, y
   * entonces manda la primera elegida. En un borrador con `FOTOS_COMPARTIDAS` marcarla es
   * obligatorio: el mensaje anunciaba varios productos con las mismas fotos, y de la principal sale
   * la huella visual con que después se reconoce este y no el otro.
   */
  protected readonly principalMarcada = signal<string | null>(null);

  /** Las elegidas, en el orden de la publicación salvo la principal marcada, que va primero. */
  protected readonly fotosElegidas = computed(() => {
    const elegidas = this.fotos().filter((foto) => this.fotosQueEntran().has(foto.mensajeId));
    const marcada = this.principalMarcada();
    const principal = elegidas.find((foto) => foto.mensajeId === marcada);
    return principal ? [principal, ...elegidas.filter((foto) => foto !== principal)] : elegidas;
  });

  protected readonly fotosCompartidas = computed(
    () => this.borrador()?.alertas.includes('FOTOS_COMPARTIDAS') ?? false,
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

  /**
   * Los colores que se le pueden marcar a una foto: los de la paleta, con su nombre en español
   * —que es el valor del atributo Color— y, en el panel en inglés, el inglés al lado; en orden
   * alfabético de lo que se lee. Se marcan hasta tres y el orden de marcado es el de la
   * combinación: «Negro / Rojo» (4 de octubre de 2026). La muestra la arma el servidor con la
   * paleta; aquí solo se elige el nombre.
   */
  protected readonly opcionesTono = computed<ColorParaElegir[]>(() =>
    paletaParaElegir(this.paleta.data() ?? [], this.idioma()),
  );

  protected readonly textosColores = computed<TextosSelectorColores>(() => {
    const traducir = this.traducir();
    return {
      ninguno: traducir('admin.borradores.aprobar.todosLosTonos'),
      buscar: traducir('admin.colores.buscar'),
      maximo: traducir('admin.colores.maximo'),
      sinResultados: traducir('admin.colores.sinResultados'),
    };
  });

  private readonly categoriaElegida = toSignal(this.formAprobar.controls.categoriaId.valueChanges, {
    initialValue: '',
  });
  private readonly tallasEscritas = toSignal(this.formDatos.controls.tallas.valueChanges, {
    initialValue: '',
  });

  /**
   * La escala de tallas de la categoría elegida —la suya o la de su rama—: las casillas que la
   * revisión ofrece en vez de escribir las tallas a mano. Vacía mientras no haya categoría, o si la
   * categoría no talla.
   */
  protected readonly escalaTallas = computed(() => {
    const categorias = this.opciones.categorias.data() ?? [];
    const elegida = categorias.find((categoria) => categoria.id === this.categoriaElegida());
    return escalaDeTallasDe(elegida, categorias);
  });

  private readonly descripcionEscrita = toSignal(
    this.formDatos.controls.descripcion.statusChanges,
    { initialValue: this.formDatos.controls.descripcion.status },
  );

  /** El error del campo, enganchado al `markAsTouched()` de aprobar: las dos mitades juntas. */
  protected readonly errorDescripcion = computed(() => {
    this.descripcionEscrita();
    this.tocadoDescripcion();
    const control = this.formDatos.controls.descripcion;
    return control.invalid && control.touched
      ? this.traducir()('admin.borradores.aprobar.faltaDescripcion')
      : null;
  });

  /** `markAsTouched()` no emite: se avisa a mano para que el error se pinte. */
  private readonly tocadoDescripcion = signal(0);

  /** Las tallas escritas, normalizadas: «m» marca la casilla «M» y «2XL» la de «XXL». */
  protected readonly tallasMarcadas = computed(
    () => new Set(separar(this.tallasEscritas()).map(tallaNormalizada)),
  );

  protected tallaMarcada(talla: string): boolean {
    return this.tallasMarcadas().has(tallaNormalizada(talla));
  }

  /** Lo que la extracción dejó y la escala de la categoría no tiene: se avisa, no se borra. */
  protected readonly tallasFueraDeEscala = computed(() => {
    const escala = new Set(this.escalaTallas().map(tallaNormalizada));
    if (escala.size === 0) {
      return [];
    }
    return separar(this.tallasEscritas()).filter((talla) => !escala.has(tallaNormalizada(talla)));
  });

  protected readonly esReplica = computed(
    () => this.borrador()?.alertas.includes('REPLICA') ?? false,
  );

  private cargado: string | null = null;
  private sugeridaAplicada: string | null = null;
  private marcaDeReplicaAplicada: string | null = null;

  constructor() {
    effect(() => {
      const borrador = this.borrador();
      if (!borrador || this.cargado === borrador.id) {
        return;
      }
      this.cargado = borrador.id;
      // Todo lo que es de la decisión anterior se va con ella: navegar de un borrador a otro por
      // la URL reutiliza el componente.
      this.prendas.set(SIN_PRENDAS);
      this.principalMarcada.set(null);
      this.fotosExcluidas.set(new Set());
      this.fotosIncluidasAMano.set(new Set());
      this.aprobado.set(null);
      this.rechazado.set(false);
      this.confirmandoBorrar.set(false);
      this.errorBorrar.set(null);
      this.confirmandoEliminarFoto.set(null);
      this.errorEliminarFoto.set(null);
      this.fotoEliminada.set(false);
      this.subiendoFotos.set(null);
      this.fotosSubidas.set(null);
      this.erroresSubida.set([]);
      this.avisoDatos.set(null);
      this.errorDatos.set(null);
      this.errorDecision.set(null);
      this.errorRechazo.set(null);
      this.motivoRechazo.reset();
      this.formDatos.reset(this.aFormularioDatos(borrador));
      // El alt en inglés parte del título en inglés que propuso la extracción, con el nombre
      // comercial del artículo; nunca del título en español, para que la vitrina en inglés no
      // herede un alt en castellano. Si la extracción no lo dio, queda vacío y obligatorio.
      this.formAprobar.reset({
        precioVenta:
          borrador.precioVentaSugerido === null ? '' : String(borrador.precioVentaSugerido),
        existenciaInicial: '1',
        altEs: borrador.titulo,
        altEn: borrador.altEn ?? '',
      });
      // Un borrador decidido se lee, no se corrige: los campos quedan deshabilitados y no solo
      // sin botón.
      if (borradorEditable(borrador)) {
        this.formDatos.enable({ emitEvent: false });
      } else {
        this.formDatos.disable({ emitEvent: false });
      }
    });

    // Las categorías llegan por su lado, antes o después del borrador; la sugerida se pone cuando
    // están las dos cosas, una vez por borrador y solo si nadie eligió otra. Va después del efecto
    // de arriba, que reinicia el formulario: los efectos corren en el orden en que se crean.
    effect(() => {
      const borrador = this.borrador();
      const categorias = this.opciones.categorias.data();
      if (!borrador || !categorias || this.sugeridaAplicada === borrador.id) {
        return;
      }
      this.sugeridaAplicada = borrador.id;
      const slug = CATEGORIA_SUGERIDA_POR_TIPO[borrador.tipo];
      const sugerida = slug ? categorias.find((categoria) => categoria.slug === slug) : undefined;
      const control = this.formAprobar.controls.categoriaId;
      if (sugerida && borradorEditable(borrador) && control.value === '') {
        control.setValue(sugerida.id);
      }
    });

    // Una réplica se publica con la marca Genérica: se propone en cuanto llegan las marcas, una
    // vez por borrador y solo si nadie eligió otra.
    effect(() => {
      const borrador = this.borrador();
      const marcas = this.opciones.marcas.data();
      if (!borrador || !marcas || this.marcaDeReplicaAplicada === borrador.id) {
        return;
      }
      this.marcaDeReplicaAplicada = borrador.id;
      const generica = marcas.find((marca) => sinTildes(marca.nombre) === MARCA_DE_REPLICAS);
      const control = this.formAprobar.controls.marcaId;
      if (
        generica &&
        borrador.alertas.includes('REPLICA') &&
        borradorEditable(borrador) &&
        control.value === ''
      ) {
        control.setValue(generica.id);
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
      descripcion: borrador.descripcion ?? '',
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
    return valor === null ? '-' : formatearPrecio(valor, 'COP', this.transloco.activeLang());
  }

  /** Marcar o desmarcar una talla escribe la lista en el orden de la escala, y lo de fuera al final. */
  protected marcarTalla(talla: string, marcada: boolean): void {
    const actuales = new Set(this.tallasMarcadas());
    if (marcada) {
      actuales.add(tallaNormalizada(talla));
    } else {
      actuales.delete(tallaNormalizada(talla));
    }
    // Las de la escala, escritas como la escala y en su orden; las de fuera, como se escribieron.
    const escala = this.escalaTallas();
    const deLaEscala = new Set(escala.map(tallaNormalizada));
    const ordenadas = [
      ...escala.filter((valor) => actuales.has(tallaNormalizada(valor))),
      ...separar(this.tallasEscritas()).filter(
        (valor) =>
          !deLaEscala.has(tallaNormalizada(valor)) && actuales.has(tallaNormalizada(valor)),
      ),
    ];
    this.formDatos.controls.tallas.setValue(ordenadas.join(', '));
  }

  /** Los colores marcados de una foto —los de su prenda—, en orden: «Negro / Rojo» → ['Negro', 'Rojo']. */
  protected coloresDe(mensajeId: string): string[] {
    return separarColores(tonoDeFoto(this.prendas(), mensajeId));
  }

  /** En una foto de una prenda el color lo cambia la prenda entera: son la misma. */
  protected elegirColores(mensajeId: string, colores: readonly string[]): void {
    this.prendas.update((actual) => elegirTonoDeFoto(actual, mensajeId, unirColores(colores)));
  }

  /** El valor del selector de prenda de una foto: su número, o vacío si vale para todas. */
  protected prendaDe(mensajeId: string): string {
    const prenda = this.prendas().prendaPorFoto[mensajeId];
    return prenda === undefined ? '' : String(prenda);
  }

  /**
   * Las prendas a las que se puede pasar cada foto, por `mensajeId`: las que ya existen, con su
   * color si lo tienen, y una nueva. La nueva no se ofrece a la foto que ya está sola en la suya,
   * porque sacarla de ahí para meterla en otra vacía no cambia nada.
   */
  protected readonly opcionesPrenda = computed<Readonly<Record<string, OpcionSelect[]>>>(() => {
    const traducir = this.traducir();
    const asignacion = this.prendas();
    const existentes = prendasEnUso(asignacion).map((numero) => ({
      valor: String(numero),
      etiqueta: asignacion.tonoPorPrenda[numero]
        ? traducir('admin.borradores.aprobar.prendaConColor', {
            numero,
            color: asignacion.tonoPorPrenda[numero],
          })
        : traducir('admin.borradores.aprobar.prenda', { numero }),
    }));
    const nueva = { valor: 'nueva', etiqueta: traducir('admin.borradores.aprobar.prendaNueva') };
    const porFoto: Record<string, OpcionSelect[]> = {};
    for (const foto of this.fotos()) {
      const suya = asignacion.prendaPorFoto[foto.mensajeId];
      const sola =
        suya !== undefined &&
        Object.values(asignacion.prendaPorFoto).filter((p) => p === suya).length === 1;
      porFoto[foto.mensajeId] = sola ? existentes : [...existentes, nueva];
    }
    return porFoto;
  });

  protected moverAPrenda(mensajeId: string, valor: string): void {
    const destino = valor === '' ? null : valor === 'nueva' ? 'nueva' : Number(valor);
    this.prendas.update((actual) => moverFotoAPrenda(actual, mensajeId, destino));
  }

  /** El atajo del producto que viene en una sola prenda fotografiada desde varios ángulos. */
  protected unaSolaPrenda(): void {
    const elegidas = this.fotosElegidas().map((foto) => foto.mensajeId);
    this.prendas.update((actual) => unaSolaPrenda(actual, elegidas));
  }

  /**
   * Las variantes de color que va a crear la aprobación, con el número de cada foto como se ve en
   * la lista: lo que permite revisar la agrupación antes de que exista.
   */
  protected readonly variantesQueSeCrean = computed(() => {
    const numeroDe = new Map(this.fotos().map((foto, i) => [foto.mensajeId, i + 1]));
    return variantesQueSeCrean(
      this.prendas(),
      this.fotosElegidas().map((foto) => foto.mensajeId),
    ).map((variante) => ({
      ...variante,
      numeros: variante.fotos
        .map((id) => numeroDe.get(id) ?? 0)
        .sort((a, b) => a - b)
        .join(', '),
    }));
  });

  protected fotoIncluida(mensajeId: string): boolean {
    return this.fotosQueEntran().has(mensajeId);
  }

  /**
   * Marcar una foto que pasa del tope la mete y saca la última de las que entraban solo por él;
   * desmarcar una deja entrar a la siguiente que esperaba.
   */
  protected incluirFoto(mensajeId: string, incluir: boolean): void {
    this.fotosExcluidas.update((actual) => conSinFoto(actual, mensajeId, !incluir));
    this.fotosIncluidasAMano.update((actual) => conSinFoto(actual, mensajeId, incluir));
    if (!incluir && this.principalMarcada() === mensajeId) {
      this.principalMarcada.set(null);
    }
  }

  /** Una foto eliminada no deja rastro en ninguna de las dos listas. */
  private olvidarFoto(mensajeId: string): void {
    this.fotosExcluidas.update((actual) => conSinFoto(actual, mensajeId, false));
    this.fotosIncluidasAMano.update((actual) => conSinFoto(actual, mensajeId, false));
  }

  /**
   * Cuál es la principal: la marcada o, sin marca, la primera de las elegidas. Con fotos
   * compartidas no hay principal hasta que alguien la marque: suponerla sería justo el error que la
   * marca existe para evitar.
   */
  protected esPrincipal(mensajeId: string): boolean {
    if (this.fotosCompartidas() && this.principalMarcada() === null) {
      return false;
    }
    return this.fotosElegidas()[0]?.mensajeId === mensajeId;
  }

  /**
   * El botón desaparece al pulsarlo —la foto ya es la principal—, y sin moverlo el foco caería en
   * `<body>` y devolvería al principio de la página a quien navega con teclado. Va a la casilla de
   * la misma foto, que ahora dice que es la principal.
   */
  protected marcarPrincipal(mensajeId: string, indice: number): void {
    // Elegirla es elegirla a mano: si no, marcar después otra que pasa del tope podría sacarla.
    this.incluirFoto(mensajeId, true);
    this.principalMarcada.set(mensajeId);
    this.enfocarDespuesDePintar(() =>
      this.host.nativeElement.querySelector<HTMLElement>('#incluir-foto-' + indice),
    );
  }

  protected preguntarSiEliminarFoto(mensajeId: string): void {
    this.errorEliminarFoto.set(null);
    this.confirmandoEliminarFoto.set(mensajeId);
    this.enfocarDespuesDePintar(() => this.cajaEliminarFoto()?.nativeElement);
  }

  protected cancelarEliminarFoto(indice: number): void {
    this.confirmandoEliminarFoto.set(null);
    this.errorEliminarFoto.set(null);
    this.enfocarDespuesDePintar(() =>
      this.host.nativeElement.querySelector<HTMLElement>('#eliminar-foto-' + indice + ' button'),
    );
  }

  /**
   * La foto desaparece con su fila y el botón pulsado con ella: el foco va al aviso de que se
   * eliminó, que vive siempre en la página.
   */
  protected eliminarFoto(mensajeId: string): void {
    if (this.descartandoFoto()) {
      return;
    }
    this.errorEliminarFoto.set(null);
    this.descartarFoto.mutate(
      { id: this.id(), mensajeId },
      {
        onSuccess: () => {
          this.confirmandoEliminarFoto.set(null);
          this.olvidarFoto(mensajeId);
          this.prendas.update((actual) => olvidarFotoDePrendas(actual, mensajeId));
          if (this.principalMarcada() === mensajeId) {
            this.principalMarcada.set(null);
          }
          this.fotoEliminada.set(true);
          this.enfocarDespuesDePintar(() => this.avisoFotosRef()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.errorEliminarFoto.set(
            mensajeDeError(error, this.transloco, 'admin.borradores.eliminarFoto.error'),
          ),
      },
    );
  }

  /**
   * Sube los archivos elegidos uno tras otro, en el orden en que llegaron, para poder decir cuál
   * falló: de cinco, puede fallar uno. Lo que no es JPEG ni PNG ni sale del navegador, porque la
   * API lo rechazaría igual.
   *
   * <p>Las fotos nuevas entran incluidas mientras quepan: `fotosExcluidas` guarda las que se sacan,
   * no las que se eligen. El foco se queda en el selector de archivos, que no se va de la página.
   *
   * <p>El id se fija al empezar: navegar a otro borrador a mitad de la subida colgaría las fotos
   * que faltan del borrador nuevo. Si cambia, se para, y lo de esta pantalla ya no se pinta.
   */
  protected async subirFotos(evento: Event): Promise<void> {
    const selector = evento.target as HTMLInputElement;
    const archivos = Array.from(selector.files ?? []);
    if (archivos.length === 0 || this.subiendoFotos() !== null) {
      return;
    }
    const id = this.id();
    this.fotosSubidas.set(null);
    this.erroresSubida.set([]);
    const errores: string[] = [];
    let subidas = 0;
    this.subiendoFotos.set({ hechas: 0, total: archivos.length });
    for (const archivo of archivos) {
      if (this.id() !== id) {
        return;
      }
      if (!fotoAdmitida(archivo)) {
        errores.push(
          this.transloco.translate('admin.borradores.subirFotos.tipoNoAdmitido', {
            nombre: archivo.name,
          }),
        );
      } else {
        try {
          await this.subirFoto.mutateAsync({ id, archivo });
          subidas++;
        } catch (error: unknown) {
          errores.push(this.mensajeDeSubidaFallida(error, archivo.name));
        }
      }
      this.subiendoFotos.update((avance) => avance && { ...avance, hechas: avance.hechas + 1 });
    }
    // Vacío, para que elegir otra vez el mismo archivo vuelva a disparar `change`.
    selector.value = '';
    if (this.id() !== id) {
      return;
    }
    this.subiendoFotos.set(null);
    this.fotosSubidas.set(subidas);
    this.erroresSubida.set(errores);
  }

  /** Por qué no entró, con el nombre del archivo: de varios, la persona tiene que saber cuál. */
  private mensajeDeSubidaFallida(error: unknown, nombre: string): string {
    const codigo = error instanceof ErrorHttp ? error.codigo : undefined;
    const clave =
      codigo === 'IMAGEN_DE_PROVEEDOR_ILEGIBLE'
        ? 'admin.borradores.subirFotos.ilegible'
        : codigo === 'FOTO_DEMASIADO_GRANDE'
          ? 'admin.borradores.subirFotos.demasiadoGrande'
          : 'admin.borradores.subirFotos.error';
    return this.transloco.translate(clave, { nombre });
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
          descripcion: valores.descripcion.trim(),
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
    if (this.decidiendo()) {
      return;
    }
    // Las fotos que faltan por confirmar no entrarían, y la que se confirme después encontraría el
    // borrador ya aprobado.
    if (this.subiendoFotos() !== null) {
      this.errorDecision.set(this.transloco.translate('admin.borradores.aprobar.esperaSubida'));
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
    if (this.fotosCompartidas() && elegidas[0].mensajeId !== this.principalMarcada()) {
      this.errorDecision.set(this.transloco.translate('admin.borradores.aprobar.marcaLaPrincipal'));
      return;
    }
    // Una prenda con color cuyas fotos no entran es una variante que no se crea: quien la armó
    // cuenta con ese tono en la ficha. Y una prenda sin color no tiene variante que crear. En los
    // dos casos se para y se dice cuál, en vez de aprobar sin ella.
    const prendas = this.prendas();
    const problema = problemaDePrendas(
      prendas,
      elegidas.map((foto) => foto.mensajeId),
    );
    if (problema?.tipo === 'FUERA') {
      this.errorDecision.set(
        this.transloco.translate('admin.borradores.aprobar.fotoConTonoFuera', {
          numero: this.fotos().findIndex((foto) => foto.mensajeId === problema.mensajeId) + 1,
        }),
      );
      return;
    }
    if (problema?.tipo === 'SIN_COLOR') {
      this.errorDecision.set(
        this.transloco.translate('admin.borradores.aprobar.prendaSinColor', {
          prenda: problema.prenda,
        }),
      );
      return;
    }
    const datos = this.formDatos.getRawValue();
    if (!datos.descripcion.trim()) {
      this.formDatos.controls.descripcion.markAsTouched();
      this.tocadoDescripcion.update((n) => n + 1);
      this.errorDecision.set(this.transloco.translate('admin.borradores.aprobar.faltaDescripcion'));
      return;
    }
    this.errorDecision.set(null);

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
          descripcion: datos.descripcion.trim(),
          altEs: valores.altEs.trim(),
          altEn: valores.altEn.trim(),
          fotos: elegidas.map((foto) => {
            const tono = tonoDeFoto(prendas, foto.mensajeId) || null;
            return {
              mensajeId: foto.mensajeId,
              tono,
              colorHex: this.hexDe(tono),
              prenda: prendas.prendaPorFoto[foto.mensajeId] ?? null,
            };
          }),
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

  /** El HEX del primer color de la combinación. El servidor arma la muestra entera con la paleta. */
  private hexDe(tono: string | null): string | null {
    const primero = tono ? separarColores(tono)[0] : undefined;
    if (!primero) {
      return null;
    }
    return (this.paleta.data() ?? []).find((color) => color.nombre === primero)?.hex ?? null;
  }

  protected rechazarBorrador(): void {
    if (this.decidiendo()) {
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

  protected preguntarSiBorrar(): void {
    this.errorBorrar.set(null);
    this.confirmandoBorrar.set(true);
    // Como en la galería del producto: sin esto, tabular desde "Borrar" salta directo a "Sí,
    // borrar" y se confirma sin haber pasado por la advertencia de que no hay vuelta atrás.
    this.enfocarDespuesDePintar(() => this.cajaBorrar()?.nativeElement);
  }

  protected cancelarBorrar(): void {
    this.confirmandoBorrar.set(false);
    this.errorBorrar.set(null);
    this.enfocarDespuesDePintar(() => this.botonBorrar()?.nativeElement.querySelector('button'));
  }

  /** Lo borrado ya no tiene pantalla: se vuelve a la bandeja. */
  protected borrarBorrador(): void {
    if (this.decidiendo()) {
      return;
    }
    this.errorBorrar.set(null);
    this.eliminar.mutate(this.id(), {
      onSuccess: () =>
        void this.router.navigate(['/' + this.transloco.activeLang(), 'admin', 'borradores']),
      onError: (error: unknown) =>
        this.errorBorrar.set(
          mensajeDeError(error, this.transloco, 'admin.borradores.borrar.error'),
        ),
    });
  }
}
