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
import { usarPaletaDeColores } from '../../../../catalogo/application/listar-paleta-colores.consulta';
import { escalaDeTallasDe, hojasConRuta } from '../../../../catalogo/domain/arbol-categorias';
import { claveDeLinea } from '../../../../catalogo/domain/filtro-productos.model';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarProveedoresAdmin } from '../../../proveedores/application/listar-proveedores.consulta';
import {
  usarAprobarBorrador,
  usarDescartarFotoBorrador,
  usarEditarBorrador,
  usarEliminarBorrador,
  usarRechazarBorrador,
} from '../../application/decidir-borrador.mutacion';
import { usarVerBorrador } from '../../application/ver-borrador.consulta';
import {
  Borrador,
  borradorBorrable,
  borradorEditable,
  CATEGORIA_SUGERIDA_POR_TIPO,
  EstadoBorrador,
  MARCA_DE_REPLICAS,
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

/** «Genérica», «GENERICA» y «generica» son la misma marca. */
function sinTildes(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
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
  private readonly opciones = usarOpcionesFiltro();
  private readonly paleta = usarPaletaDeColores();
  private readonly editar = usarEditarBorrador();
  private readonly aprobar = usarAprobarBorrador();
  private readonly rechazar = usarRechazarBorrador();
  private readonly eliminar = usarEliminarBorrador();
  private readonly descartarFoto = usarDescartarFotoBorrador();

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

  /** El tono elegido por foto, por `mensajeId`. Vacío = la foto vale para todos los tonos. */
  protected readonly tonoPorFoto = signal<Readonly<Record<string, string>>>({});

  /**
   * Las fotos que NO entran al producto, por `mensajeId`. Se guardan las excluidas y no las
   * elegidas para que una foto nueva en una revalidación entre por omisión. Al cargar, las que
   * sobrepasan el máximo quedan fuera: una publicación de ropa trae doce o catorce.
   */
  protected readonly fotosExcluidas = signal<ReadonlySet<string>>(new Set());
  protected readonly maximoFotos = MAXIMO_FOTOS_POR_PRODUCTO;

  /**
   * La foto que la persona marcó como principal, por `mensajeId`; nula si no marcó ninguna, y
   * entonces manda la primera elegida. En un borrador con `FOTOS_COMPARTIDAS` marcarla es
   * obligatorio: el mensaje anunciaba varios productos con las mismas fotos, y de la principal sale
   * la huella visual con que después se reconoce este y no el otro.
   */
  protected readonly principalMarcada = signal<string | null>(null);

  /** Las elegidas, en el orden de la publicación salvo la principal marcada, que va primero. */
  protected readonly fotosElegidas = computed(() => {
    const elegidas = this.fotos().filter((foto) => !this.fotosExcluidas().has(foto.mensajeId));
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
   * Los colores que se le pueden asignar a una foto: los de la paleta, con su nombre en español
   * —que es el valor del atributo Color— y, en el panel en inglés, el inglés al lado. Su HEX viaja
   * con la aprobación y es lo que pinta la muestra en la tarjeta y en la ficha.
   */
  protected readonly opcionesTono = computed<OpcionSelect[]>(() => {
    const ingles = this.idioma() === 'en';
    return (this.paleta.data() ?? []).map((color) => ({
      valor: color.nombre,
      etiqueta: ingles ? `${color.nombreEn} (${color.nombre})` : color.nombre,
    }));
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

  protected readonly tallasMarcadas = computed(() => new Set(separar(this.tallasEscritas())));

  /** Lo que la extracción dejó y la escala de la categoría no tiene: se avisa, no se borra. */
  protected readonly tallasFueraDeEscala = computed(() => {
    const escala = new Set(this.escalaTallas());
    if (escala.size === 0) {
      return [];
    }
    return [...this.tallasMarcadas()].filter((talla) => !escala.has(talla));
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
      this.tonoPorFoto.set({});
      this.principalMarcada.set(null);
      this.fotosExcluidas.set(
        new Set(
          this.fotos()
            .slice(MAXIMO_FOTOS_POR_PRODUCTO)
            .map((foto) => foto.mensajeId),
        ),
      );
      this.aprobado.set(null);
      this.rechazado.set(false);
      this.confirmandoBorrar.set(false);
      this.errorBorrar.set(null);
      this.confirmandoEliminarFoto.set(null);
      this.errorEliminarFoto.set(null);
      this.fotoEliminada.set(false);
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
    return valor === null ? '—' : formatearPrecio(valor, 'COP', this.transloco.activeLang());
  }

  /** Marcar o desmarcar una talla escribe la lista en el orden de la escala, y lo de fuera al final. */
  protected marcarTalla(talla: string, marcada: boolean): void {
    const actuales = new Set(this.tallasMarcadas());
    if (marcada) {
      actuales.add(talla);
    } else {
      actuales.delete(talla);
    }
    const escala = this.escalaTallas();
    const ordenadas = [
      ...escala.filter((valor) => actuales.has(valor)),
      ...[...actuales].filter((valor) => !escala.includes(valor)),
    ];
    this.formDatos.controls.tallas.setValue(ordenadas.join(', '));
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
    if (!incluir && this.principalMarcada() === mensajeId) {
      this.principalMarcada.set(null);
    }
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
          this.incluirFoto(mensajeId, true);
          this.tonoPorFoto.update((actual) =>
            Object.fromEntries(Object.entries(actual).filter(([id]) => id !== mensajeId)),
          );
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
    const datos = this.formDatos.getRawValue();
    if (!datos.descripcion.trim()) {
      this.formDatos.controls.descripcion.markAsTouched();
      this.errorDecision.set(this.transloco.translate('admin.borradores.aprobar.faltaDescripcion'));
      return;
    }
    this.errorDecision.set(null);

    const tonos = this.tonoPorFoto();
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
          fotos: elegidas.map((foto) => ({
            mensajeId: foto.mensajeId,
            tono: tonos[foto.mensajeId] || null,
            colorHex: this.hexDe(tonos[foto.mensajeId]),
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

  private hexDe(tono: string | undefined): string | null {
    if (!tono) {
      return null;
    }
    return (this.paleta.data() ?? []).find((color) => color.nombre === tono)?.hex ?? null;
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
