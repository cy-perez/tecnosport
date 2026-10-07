import { isPlatformBrowser, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  PLATFORM_ID,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarIdiomaActivo } from '../../../../../core/i18n/traductor';
import { usarOpcionesDeFormulario } from '../../../../catalogo/application/listar-opciones-filtro.consulta';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsCheckbox } from '../../../../../shared/ui/checkbox/ts-checkbox';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { hojasConRuta } from '../../../../catalogo/domain/arbol-categorias';
import { claveDeLinea } from '../../../../catalogo/domain/filtro-productos.model';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { ordenarPorEtiqueta } from '../../../../../shared/ui/select/ordenar-opciones';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarEditarProductoAdmin } from '../../application/editar-producto-admin.mutacion';
import { usarSubirImagenPrincipalAdmin } from '../../application/subir-imagen-principal-admin.mutacion';
import { usarSubirImagenDeGaleriaAdmin } from '../../application/subir-imagen-de-galeria-admin.mutacion';
import { usarQuitarImagenDeGaleriaAdmin } from '../../application/quitar-imagen-de-galeria-admin.mutacion';
import { usarReordenarGaleriaAdmin } from '../../application/reordenar-galeria-admin.mutacion';
import { usarFoco } from '../../../../../shared/foco/foco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { ImagenDeGaleriaAdmin } from '../../domain/producto-admin.model';
import { esEjeDeTalla, esTallaUnica } from '../../../../catalogo/domain/seleccion-variante';
import { usarAsignarColorAImagenAdmin } from '../../application/asignar-color-imagen-admin.mutacion';
import { usarVerProductoAdmin } from '../../application/ver-producto-admin.consulta';
import { usarUsarImagenComoPrincipalAdmin } from '../../application/usar-imagen-como-principal-admin.mutacion';
import {
  usarDespublicarProducto,
  usarPublicarProducto,
} from '../../application/publicar-producto.mutacion';
import { usarEliminarProducto } from '../../application/eliminar-producto.mutacion';
import { TsMuestraColor } from '../../../../../shared/ui/muestra-color/ts-muestra-color';
import {
  ParteDeMuestra,
  separarColores,
} from '../../../../../shared/ui/muestra-color/muestra-color.model';
import { parteDeColor } from '../../../../catalogo/domain/producto.model';
import { usarPaletaDeColores } from '../../../../catalogo/application/listar-paleta-colores.consulta';
import {
  AccionDeProducto,
  CLAVE_ERROR,
  CLAVE_ETIQUETA_ESTADO,
  clasesDeEstadoProducto,
  TEXTOS_DE_CONFIRMACION,
} from '../estado-producto';

const TIPOS_DE_IMAGEN_SOPORTADOS = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * El mismo tope que `Producto.TOPE_DE_GALERIA` en el backend, que es quien manda: esto solo sirve
 * para decirlo en la ayuda y para no ofrecer un formulario que va a responder 409. Si un día
 * divergen, el que decide sigue siendo el servidor.
 */
const TOPE_DE_GALERIA = 8;

import { PanelDeDifusion } from '../../../difusion/presentation/panel-de-difusion';
@Component({
  selector: 'app-editar-producto-admin',
  imports: [
    NgTemplateOutlet,
    PanelDeDifusion,
    TsPaginaFormulario,
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsCheckbox,
    TsEsqueleto,
    TsMigas,
    TsMuestraColor,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './editar-producto-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditarProductoAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'admin.productos.editar.titulo' },
  ]);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly opciones = usarOpcionesDeFormulario();
  private readonly mutacion = usarEditarProductoAdmin();
  private readonly mutacionImagen = usarSubirImagenPrincipalAdmin();
  private readonly mutacionGaleria = usarSubirImagenDeGaleriaAdmin();
  private readonly mutacionQuitarDeGaleria = usarQuitarImagenDeGaleriaAdmin();
  private readonly mutacionReordenarGaleria = usarReordenarGaleriaAdmin();
  private readonly mutacionColor = usarAsignarColorAImagenAdmin();
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));
  protected readonly idioma = usarIdiomaActivo();

  private readonly paramMap = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  protected readonly id = computed(() => this.paramMap().get('id') ?? '');

  /**
   * Absoluto, no relativo. La ruta de esta pantalla es `:id/editar`, dos
   * segmentos, así que un `..` sube uno solo y deja `productos/:id`: el enlace
   * relativo generaba `productos/{id}/{id}/variantes/crear`, que no existe, y
   * al hacer clic la aplicación caía en la portada. Mismo criterio que
   * `usarMigasAdmin` y `ts-tarjeta-producto`, y misma familia de error que ya
   * apareció en el paso 2 de Track B con un path sin prefijo de idioma.
   */
  protected readonly enlaceAgregarVariante = computed(() => [
    '/',
    this.transloco.activeLang(),
    'admin',
    'productos',
    this.id(),
    'variantes',
    'crear',
  ]);

  protected readonly consulta = usarVerProductoAdmin(this.id);

  protected readonly error = signal<string | null>(null);
  private prefilled = false;

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    // Obligatoria, como en la revisión de un borrador: es lo que la ficha dice del producto.
    descripcion: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    marcaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    categoriaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    tallaSirveHasta: new FormControl('', { nonNullable: true }),
    fotosGeneralesEnCadaColor: new FormControl(true, { nonNullable: true }),
  });

  /**
   * Una prenda de talla única: la única que dice hasta dónde sirve. Con las mismas reglas que la
   * vitrina —`esEjeDeTalla` y `TALLA_UNICA`—, para que el panel y la ficha no difieran.
   */
  protected readonly esTallaUnica = computed(() =>
    (this.consulta.data()?.variantes ?? []).some((variante) =>
      variante.atributos.some(
        (atributo) => esEjeDeTalla(atributo.nombre) && esTallaUnica(atributo.valor),
      ),
    ),
  );

  private readonly descripcionEstado = toSignal(this.form.controls.descripcion.statusChanges, {
    initialValue: this.form.controls.descripcion.status,
  });
  private readonly intentosDeGuardar = signal(0);

  /** El error del campo enganchado al `markAllAsTouched()` de guardar. */
  protected readonly errorDescripcion = computed(() => {
    this.descripcionEstado();
    this.intentosDeGuardar();
    const control = this.form.controls.descripcion;
    return control.invalid && control.touched
      ? this.traducir()('admin.productos.editar.descripcionObligatoria')
      : null;
  });

  protected readonly avisoColor = signal(false);
  /**
   * Sube cuando guardar un color falla. El `<select>` mostraba lo que la persona eligió aunque no
   * se hubiera guardado: el valor que le llega no cambió y nada lo volvía a sincronizar. La
   * plantilla lo vuelve a crear con esta clave.
   */
  protected readonly versionDeColores = signal(0);

  /**
   * Las variantes con lo que las distingue: «PRV-1A2B — Negro · M», y la muestra de su color, como
   * la que ve quien revisa un borrador al elegir el tono.
   */
  protected readonly variantes = computed(() =>
    (this.consulta.data()?.variantes ?? []).map((variante) => ({
      id: variante.id,
      sku: variante.sku,
      detalle: variante.atributos.map((atributo) => atributo.valor).join(' · '),
      muestra: this.muestraDeVariante(variante.atributos),
    })),
  );

  private readonly paleta = usarPaletaDeColores();

  /**
   * Con la paleta, como la tarjeta: «Negro / Rojo» se pinta en dos porciones y un estampado con su
   * patrón. Si algún nombre no está en la paleta, el HEX del atributo, que es lo que se guardó.
   */
  private muestraDeVariante(
    atributos: readonly { readonly valor: string; readonly colorHex: string | null }[],
  ): readonly ParteDeMuestra[] | null {
    const color = atributos.find((atributo) => atributo.colorHex);
    if (!color?.colorHex) {
      return null;
    }
    const nombres = separarColores(color.valor);
    const paleta = this.paleta.data() ?? [];
    const partes = nombres
      .map((nombre) => paleta.find((deLaPaleta) => deLaPaleta.nombre === nombre))
      .filter((deLaPaleta) => deLaPaleta !== undefined)
      .map(parteDeColor);
    return partes.length === nombres.length && partes.length > 0
      ? partes
      : [{ patron: null, colores: [color.colorHex] }];
  }

  // --- Estado, publicación y borrado: lo que la revisión de un borrador resuelve con aprobar,
  // rechazar y borrar, y que en un producto solo se podía hacer desde la lista. ---

  private readonly mutacionPublicar = usarPublicarProducto();
  private readonly mutacionRetirar = usarDespublicarProducto();
  private readonly mutacionEliminar = usarEliminarProducto();

  protected readonly estado = computed(() => this.consulta.data()?.estado ?? null);
  protected readonly publicado = computed(() => this.estado() === 'PUBLICADO');

  protected etiquetaEstado(): string {
    const estado = this.estado();
    return estado ? this.traducir()(CLAVE_ETIQUETA_ESTADO[estado]) : '';
  }

  protected clasesEstado(): string {
    const estado = this.estado();
    return estado ? clasesDeEstadoProducto(estado) : '';
  }

  /** La acción que está preguntando: una sola a la vez, como en la lista. */
  protected readonly confirmandoAccion = signal<AccionDeProducto | null>(null);
  private accionEnVuelo = false;
  protected readonly errorAccion = signal<string | null>(null);
  /** El acuse de publicar o retirar; guarda la clave, no el texto. */
  protected readonly avisoAccion = signal<string | null>(null);
  protected readonly ocupadoAccion = computed(
    () =>
      this.mutacionPublicar.isPending() ||
      this.mutacionRetirar.isPending() ||
      this.mutacionEliminar.isPending(),
  );
  protected readonly textosAccion = computed(
    () => TEXTOS_DE_CONFIRMACION[this.confirmandoAccion() ?? 'publicar'],
  );
  protected readonly nombreProducto = computed(() => this.consulta.data()?.nombre ?? '');

  private readonly cajaAccion = viewChild<ElementRef<HTMLElement>>('cajaAccion');
  private readonly avisoAccionRef = viewChild<ElementRef<HTMLElement>>('avisoAccionRef');
  private readonly botonPublicacion = viewChild<string, ElementRef<HTMLElement>>(
    'botonPublicacion',
    {
      read: ElementRef,
    },
  );
  private readonly botonEliminar = viewChild<string, ElementRef<HTMLElement>>('botonEliminar', {
    read: ElementRef,
  });

  protected preguntarPublicacion(): void {
    this.preguntar(this.publicado() ? 'retirar' : 'publicar');
  }

  protected preguntarEliminar(): void {
    this.preguntar('eliminar');
  }

  private preguntar(accion: AccionDeProducto): void {
    this.errorAccion.set(null);
    this.avisoAccion.set(null);
    this.confirmandoAccion.set(accion);
    this.enfocarDespuesDePintar(() => this.cajaAccion()?.nativeElement);
  }

  protected cancelarAccion(): void {
    const accion = this.confirmandoAccion();
    this.confirmandoAccion.set(null);
    this.errorAccion.set(null);
    // De vuelta al botón que abrió la caja; está dentro de `ts-boton`, así que hasta su `<button>`.
    const origen = accion === 'eliminar' ? this.botonEliminar() : this.botonPublicacion();
    this.enfocarDespuesDePintar(
      () => origen?.nativeElement.querySelector<HTMLElement>('button') ?? null,
    );
  }

  /** Guarda de reentrada: el botón usa `[ocupado]`, no `[cargando]`, y sigue siendo pulsable. */
  protected confirmarAccion(): void {
    const accion = this.confirmandoAccion();
    // Una marca propia además de `ocupadoAccion()`, que no cambia en el mismo tic del `mutate`.
    if (!accion || this.accionEnVuelo || this.ocupadoAccion()) {
      return;
    }
    this.accionEnVuelo = true;
    const alTerminar = () => (this.accionEnVuelo = false);
    this.errorAccion.set(null);
    const alFallar = (error: unknown) =>
      this.errorAccion.set(mensajeDeError(error, this.transloco, CLAVE_ERROR[accion]));
    if (accion === 'eliminar') {
      this.mutacionEliminar.mutate(this.id(), {
        onSettled: alTerminar,
        // Como al borrar un borrador: la pantalla ya no tiene producto que enseñar.
        onSuccess: () =>
          void this.router.navigate(['/' + this.transloco.activeLang(), 'admin', 'productos']),
        onError: alFallar,
      });
      return;
    }
    const mutacion = accion === 'publicar' ? this.mutacionPublicar : this.mutacionRetirar;
    mutacion.mutate(this.id(), {
      onSettled: alTerminar,
      onSuccess: () => {
        this.confirmandoAccion.set(null);
        this.avisoAccion.set(TEXTOS_DE_CONFIRMACION[accion].hecho);
        this.enfocarDespuesDePintar(() => this.avisoAccionRef()?.nativeElement);
      },
      onError: alFallar,
    });
  }

  // --- La foto principal elegida entre las de la galería, como al revisar un borrador. ---

  private readonly mutacionPrincipal = usarUsarImagenComoPrincipalAdmin();
  protected readonly cambiandoPrincipal = computed(() => this.mutacionPrincipal.isPending());
  /** La foto cuya petición va en vuelo: solo su botón se anuncia ocupado. */
  protected readonly principalEnVuelo = signal<string | null>(null);
  protected readonly errorPrincipal = signal<string | null>(null);

  /**
   * No mientras se sube otra principal, y la subida tampoco mientras va esto: las dos reemplazan la
   * principal, y la subida barre los objetos de la anterior con una foto del producto que el
   * intercambio deja vieja.
   */
  protected usarComoPrincipal(imagen: ImagenDeGaleriaAdmin): void {
    // `principalEnVuelo` y no `cambiandoPrincipal()`: `isPending` de TanStack no cambia en el mismo
    // tic del `mutate`, y un doble clic pasaba los dos.
    if (this.principalEnVuelo() !== null || this.subiendoImagen()) {
      return;
    }
    this.principalEnVuelo.set(imagen.id);
    this.errorPrincipal.set(null);
    this.aviso.set(null);
    this.mutacionPrincipal.mutate(
      { productoId: this.id(), imagenId: imagen.id },
      {
        onSettled: () => this.principalEnVuelo.set(null),
        onSuccess: () => {
          this.aviso.set('admin.productos.editar.galeria.principalCambiada');
          // La fila cambia de foto —o desaparece, si no había principal—: el foco va al aviso.
          this.enfocarDespuesDePintar(() => this.avisoGaleria()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.errorPrincipal.set(
            mensajeDeError(error, this.transloco, 'admin.productos.editar.galeria.errorPrincipal'),
          ),
      },
    );
  }

  /**
   * Los colores del producto, uno por valor, con la primera variante de cada uno: marcar una foto
   * como «Negro» la cuelga de esa variante, que es lo que la tarjeta y la ficha leen para cambiar
   * de foto al elegir el color. En orden alfabético, como el selector de la revisión de borradores.
   */
  protected readonly opcionesColor = computed<OpcionSelect[]>(() => {
    const vistos = new Map<string, string>();
    for (const variante of this.consulta.data()?.variantes ?? []) {
      for (const atributo of variante.atributos) {
        if (atributo.colorHex && !vistos.has(atributo.valor)) {
          vistos.set(atributo.valor, variante.id);
        }
      }
    }
    return ordenarPorEtiqueta(
      [...vistos.entries()].map(([valor, varianteId]) => ({ valor: varianteId, etiqueta: valor })),
      this.idioma(),
    );
  });

  protected readonly errorColor = signal<string | null>(null);

  private readonly valorFormulario = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  protected readonly enviando = computed(() => this.mutacion.isPending());

  protected readonly opcionesMarca = computed<OpcionSelect[]>(() =>
    (this.opciones.marcas.data() ?? []).map((marca) => ({
      valor: marca.id,
      etiqueta: marca.nombre,
    })),
  );

  /**
   * Solo las **hojas** del árbol, etiquetadas con su ruta ("Ropa › Dama › Camisas").
   *
   * Las hojas, porque un producto no cuelga de una rama: si "Camisas" tuviera productos y también
   * subcategorías, "lo que hay en Camisas" tendría dos respuestas distintas. Lo rechaza el backend
   * (`CategoriaNoEsHojaException`) y ofrecerlo aquí sería proponer lo que se va a rechazar.
   *
   * Y la ruta, porque sin ella el desplegable tiene entradas que no se distinguen: "Busos" aparece
   * bajo Dama y bajo Caballero, y "Dama" en tres líneas.
   */
  private readonly traducir = usarTraductor();

  protected readonly opcionesCategoria = computed<OpcionSelect[]>(() => {
    const traducir = this.traducir();
    return hojasConRuta(this.opciones.categorias.data() ?? [], (linea) =>
      traducir(claveDeLinea(linea)),
    ).map((hoja) => ({ valor: hoja.categoria.id, etiqueta: hoja.ruta }));
  });

  protected readonly formularioImagen = new FormGroup({
    altEs: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    altEn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly valorFormularioImagen = toSignal(this.formularioImagen.valueChanges, {
    initialValue: this.formularioImagen.getRawValue(),
  });

  protected readonly archivoSeleccionado = signal<File | null>(null);
  protected readonly previsualizacionUrl = signal<string | null>(null);
  private dimensionesArchivo: { ancho: number; alto: number } | null = null;
  protected readonly errorImagen = signal<string | null>(null);

  protected readonly subiendoImagen = computed(() => this.mutacionImagen.isPending());

  protected readonly topeDeGaleria = TOPE_DE_GALERIA;

  protected readonly formularioGaleria = new FormGroup({
    altEs: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    altEn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly valorFormularioGaleria = toSignal(this.formularioGaleria.valueChanges, {
    initialValue: this.formularioGaleria.getRawValue(),
  });

  protected readonly archivoDeGaleria = signal<File | null>(null);
  protected readonly previsualizacionGaleria = signal<string | null>(null);
  private dimensionesGaleria: { ancho: number; alto: number } | null = null;
  protected readonly errorGaleria = signal<string | null>(null);

  /**
   * El fallo de quitar tiene su propia señal, y no es cosmética: `errorGaleria` solo se pinta
   * dentro del formulario de agregar, así que "no se pudo quitar la imagen" salía a varios cientos
   * de píxeles de la fila que falló y colocado como si fuera un error de la subida.
   */
  protected readonly errorQuitar = signal<string | null>(null);

  /** Una sola región viva para los dos anuncios; guarda la clave, no el texto. */
  protected readonly aviso = signal<string | null>(null);
  /** Aparte del de la galeria: son dos secciones distintas y cada acuse se pinta donde paso. */
  protected readonly avisoImagen = signal<string | null>(null);

  /** El id de la imagen cuya fila está preguntando, como en la lista de productos. */
  protected readonly confirmandoQuitar = signal<string | null>(null);

  /**
   * El foco no puede quedarse donde estaba: al confirmar desaparece la fila entera con su botón
   * dentro, y al cancelar desaparece la caja. En los dos casos el navegador lo manda a `<body>` y
   * quien navega con teclado vuelve al principio del documento.
   */
  private readonly cajaConfirmacion = viewChild<ElementRef<HTMLElement>>('cajaConfirmacion');
  private readonly avisoGaleria = viewChild<ElementRef<HTMLElement>>('avisoGaleria');
  private readonly avisoImagenPrincipal =
    viewChild<ElementRef<HTMLElement>>('avisoImagenPrincipal');
  private readonly filas = viewChildren<ElementRef<HTMLElement>>('filaDeGaleria');

  protected readonly galeria = computed<readonly ImagenDeGaleriaAdmin[]>(
    () => this.consulta.data()?.galeria ?? [],
  );

  protected readonly galeriaLlena = computed(() => this.galeria().length >= TOPE_DE_GALERIA);

  protected readonly agregandoAGaleria = computed(() => this.mutacionGaleria.isPending());
  protected readonly quitandoDeGaleria = computed(() => this.mutacionQuitarDeGaleria.isPending());
  protected readonly reordenandoGaleria = computed(() => this.mutacionReordenarGaleria.isPending());

  /**
   * La imagen cuyo botón hay que enfocar **cuando la galería vuelva a pintarse**, no antes.
   *
   * <p>Reordenar invalida la consulta del producto, así que la lista se repinta con lo que
   * responda el servidor. Enfocar en el siguiente cuadro —como hacen quitar y cancelar— agarra el
   * botón viejo, que el repintado destruye un instante después: el foco acaba en `<body>` y quien
   * navega con teclado vuelve al principio del documento. Se vio en el navegador; en jsdom no
   * pasa.
   */
  private readonly enfocarAlRepintar = signal<{
    id: string;
    desplazamiento: -1 | 1;
    ordenEsperado: readonly string[];
  } | null>(null);

  /**
   * El fallo de reordenar va aparte del de quitar por el mismo motivo por el que aquel se separó
   * del de agregar: se pinta donde pasó.
   */
  protected readonly errorReordenar = signal<string | null>(null);

  constructor() {
    effect(() => {
      // Depende de la galería a propósito: es el cambio que hay que esperar.
      const galeria = this.galeria();
      const pendiente = this.enfocarAlRepintar();
      // Se espera al **orden** pedido, no a que la imagen esté: está desde antes de mover, así
      // que preguntar por ella daba por repintada la lista vieja y volvía a enfocar el botón que
      // estaba a punto de desaparecer.
      const yaSeRepinto =
        pendiente !== null &&
        galeria.length === pendiente.ordenEsperado.length &&
        galeria.every((imagen, i) => imagen.id === pendiente.ordenEsperado[i]);
      if (!pendiente || !yaSeRepinto) {
        return;
      }
      this.enfocarAlRepintar.set(null);
      this.enfocarDespuesDePintar(() =>
        this.botonDeMoverDe(pendiente.id, pendiente.desplazamiento),
      );
    });

    effect(() => {
      const producto = this.consulta.data();
      if (producto && !this.prefilled) {
        this.prefilled = true;
        this.form.patchValue({
          nombre: producto.nombre,
          descripcion: producto.descripcion,
          marcaId: producto.marca.id,
          categoriaId: producto.categoria.id,
          tallaSirveHasta: producto.tallaSirveHasta ?? '',
          fotosGeneralesEnCadaColor: producto.fotosGeneralesEnCadaColor,
        });
      }
    });
  }

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.intentosDeGuardar.update((n) => n + 1);
      // Se dice qué falta en vez de deshabilitar el botón: un `<button disabled>` sale del orden
      // de tabulación, así que quien borre el nombre no encuentra "Guardar" en ninguna parte y
      // nada le explica por qué. Mismo criterio que marcas, medidas y existencias.
      this.error.set(this.transloco.translate('admin.productos.editar.faltanCampos'));
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
          ...(this.esTallaUnica() ? { tallaSirveHasta: valores.tallaSirveHasta.trim() } : {}),
          fotosGeneralesEnCadaColor: valores.fotosGeneralesEnCadaColor,
        },
      },
      {
        onSuccess: () =>
          void this.router.navigate(['/' + this.transloco.activeLang(), 'admin', 'productos']),
        onError: () => this.error.set(this.transloco.translate('admin.productos.editar.error')),
      },
    );
  }

  /**
   * El color que muestra una foto: el de la variante de la que cuelga. Se compara por color y no
   * por variante, porque la opción del selector es la primera variante de ese color.
   */
  protected colorDeImagen(imagen: ImagenDeGaleriaAdmin): string {
    if (!imagen.varianteId) {
      return '';
    }
    const variantes = this.consulta.data()?.variantes ?? [];
    const color = variantes
      .find((variante) => variante.id === imagen.varianteId)
      ?.atributos.find((atributo) => atributo.colorHex)?.valor;
    return this.opcionesColor().find((opcion) => opcion.etiqueta === color)?.valor ?? '';
  }

  protected asignarColor(imagen: ImagenDeGaleriaAdmin, varianteId: string): void {
    this.errorColor.set(null);
    this.avisoColor.set(false);
    this.mutacionColor.mutate(
      { productoId: this.id(), imagenId: imagen.id, varianteId: varianteId || null },
      {
        onSuccess: () => this.avisoColor.set(true),
        onError: (error: unknown) => {
          this.errorColor.set(
            mensajeDeError(error, this.transloco, 'admin.productos.editar.galeria.errorColor'),
          );
          this.versionDeColores.update((n) => n + 1);
        },
      },
    );
  }

  protected async onArchivoSeleccionado(evento: Event): Promise<void> {
    if (!this.esNavegador) {
      return;
    }
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;
    this.errorImagen.set(null);
    this.limpiarPrevisualizacion();
    if (!archivo) {
      this.archivoSeleccionado.set(null);
      return;
    }
    if (!TIPOS_DE_IMAGEN_SOPORTADOS.includes(archivo.type)) {
      this.errorImagen.set(
        this.transloco.translate('admin.productos.editar.imagenPrincipal.tipoNoSoportado'),
      );
      input.value = '';
      return;
    }

    const url = URL.createObjectURL(archivo);
    try {
      this.dimensionesArchivo = await this.leerDimensiones(url);
      this.previsualizacionUrl.set(url);
      this.archivoSeleccionado.set(archivo);
    } catch {
      URL.revokeObjectURL(url);
      this.errorImagen.set(
        this.transloco.translate('admin.productos.editar.imagenPrincipal.error'),
      );
    }
  }

  private leerDimensiones(url: string): Promise<{ ancho: number; alto: number }> {
    return new Promise((resolve, reject) => {
      const imagen = new Image();
      imagen.onload = () => resolve({ ancho: imagen.naturalWidth, alto: imagen.naturalHeight });
      imagen.onerror = () => reject(new Error('No se pudo leer la imagen.'));
      imagen.src = url;
    });
  }

  private limpiarPrevisualizacion(): void {
    const anterior = this.previsualizacionUrl();
    if (anterior) {
      URL.revokeObjectURL(anterior);
    }
    this.previsualizacionUrl.set(null);
  }

  protected async onArchivoDeGaleriaSeleccionado(evento: Event): Promise<void> {
    if (!this.esNavegador) {
      return;
    }
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;
    this.errorGaleria.set(null);
    this.aviso.set(null);
    this.limpiarPrevisualizacionGaleria();
    if (!archivo) {
      this.archivoDeGaleria.set(null);
      return;
    }
    if (!TIPOS_DE_IMAGEN_SOPORTADOS.includes(archivo.type)) {
      this.errorGaleria.set(
        this.transloco.translate('admin.productos.editar.galeria.tipoNoSoportado'),
      );
      input.value = '';
      return;
    }

    const url = URL.createObjectURL(archivo);
    try {
      this.dimensionesGaleria = await this.leerDimensiones(url);
      this.previsualizacionGaleria.set(url);
      this.archivoDeGaleria.set(archivo);
    } catch {
      URL.revokeObjectURL(url);
      this.errorGaleria.set(this.transloco.translate('admin.productos.editar.galeria.error'));
    }
  }

  private limpiarPrevisualizacionGaleria(): void {
    const anterior = this.previsualizacionGaleria();
    if (anterior) {
      URL.revokeObjectURL(anterior);
    }
    this.previsualizacionGaleria.set(null);
  }

  protected agregarAGaleria(): void {
    const archivo = this.archivoDeGaleria();
    if (!archivo || !this.dimensionesGaleria || this.formularioGaleria.invalid) {
      this.formularioGaleria.markAllAsTouched();
      this.errorGaleria.set(
        this.transloco.translate('admin.productos.editar.galeria.faltanCampos'),
      );
      return;
    }
    this.errorGaleria.set(null);

    const { altEs, altEn } = this.formularioGaleria.getRawValue();
    this.mutacionGaleria.mutate(
      {
        productoId: this.id(),
        archivo,
        ancho: this.dimensionesGaleria.ancho,
        alto: this.dimensionesGaleria.alto,
        altEs,
        altEn,
      },
      {
        onSuccess: () => {
          this.limpiarPrevisualizacionGaleria();
          this.archivoDeGaleria.set(null);
          this.dimensionesGaleria = null;
          this.formularioGaleria.reset();
          // Un formulario que se vacía se lee igual como "funcionó" que como "nunca se envió".
          this.aviso.set('admin.productos.editar.galeria.agregada');
        },
        // El 409 de la foto repetida y el de la galería llena son accionables, y `mensajeDeError`
        // los dice con sus palabras: "no se pudo" mandaría a mirar el sitio equivocado.
        onError: (error) =>
          this.errorGaleria.set(
            mensajeDeError(error, this.transloco, 'admin.productos.editar.galeria.error'),
          ),
      },
    );
  }

  protected preguntarSiQuitar(imagen: ImagenDeGaleriaAdmin): void {
    // Solo lo suyo: abrir una pregunta no es razón para borrar el error de una subida que falló.
    this.errorQuitar.set(null);
    this.aviso.set(null);
    this.confirmandoQuitar.set(imagen.id);
    // Sin esto la advertencia de que el archivo se borra queda detrás del foco: tabular desde
    // "Quitar" salta directo a "Sí, quitar" y se puede confirmar sin haberla encontrado nunca.
    this.enfocarDespuesDePintar(() => this.cajaConfirmacion()?.nativeElement);
  }

  protected cancelarQuitar(): void {
    const id = this.confirmandoQuitar();
    this.confirmandoQuitar.set(null);
    this.errorQuitar.set(null);
    this.enfocarDespuesDePintar(() => this.botonQuitarDe(id));
  }

  /**
   * El elemento se enfoca en el siguiente cuadro: en el momento de la llamada todavía no existe
   * —lo acaba de crear un `@if`— o está a punto de dejar de existir.
   *
   * <p>Es `shared/foco/foco.ts`, que **nació de este código**: las otras tres pantallas del panel
   * copiaron la interacción y no el arreglo, así que se extrajo para que la siguiente tuviera a
   * mano la solución. Esta se quedó con su copia, que es exactamente la forma de que las dos se
   * separen con el tiempo.
   */
  private readonly enfocarDespuesDePintar = usarFoco();

  private botonQuitarDe(imagenId: string | null): HTMLElement | null {
    if (!imagenId) {
      return null;
    }
    const fila = this.filas().find((f) => f.nativeElement.dataset['imagenId'] === imagenId);
    // Por su marca y no por `querySelector('button')`: la fila tiene ahora tres botones —subir,
    // bajar y quitar— y el primero dejó de ser este.
    return fila?.nativeElement.querySelector<HTMLElement>('[data-quitar] button') ?? null;
  }

  /** Borra el archivo además de la fila, así que pregunta antes: no hay vuelta. */
  protected quitarDeGaleria(imagen: ImagenDeGaleriaAdmin): void {
    this.errorQuitar.set(null);
    this.mutacionQuitarDeGaleria.mutate(
      { productoId: this.id(), imagenId: imagen.id },
      {
        onSuccess: () => {
          this.confirmandoQuitar.set(null);
          this.aviso.set('admin.productos.editar.galeria.quitada');
          // La fila y su botón ya no existen; el aviso sí, y dice lo que pasó.
          this.enfocarDespuesDePintar(() => this.avisoGaleria()?.nativeElement);
        },
        onError: (error) =>
          this.errorQuitar.set(
            mensajeDeError(error, this.transloco, 'admin.productos.editar.galeria.errorQuitar'),
          ),
      },
    );
  }

  /**
   * Mueve una imagen un puesto arriba o abajo y manda la galería entera con el orden resultante.
   *
   * <p>Un puesto a la vez, con botones, y no arrastrando: el arrastre no existe para quien navega
   * con teclado, y aquí se mueven cuatro fotos, no cuarenta. El botón del extremo no se
   * deshabilita —un control deshabilitado no es enfocable y desaparece para un lector de
   * pantalla—: sencillamente no está, porque en el extremo no hay ningún movimiento que ofrecer.
   */
  protected mover(imagen: ImagenDeGaleriaAdmin, desplazamiento: -1 | 1): void {
    // El botón no se deshabilita mientras va la petición, y el guardia está aquí a propósito:
    // `[cargando]` pone `disabled`, y deshabilitar el botón que acabas de pulsar le quita el foco
    // al sitio —a `<body>`— antes de que la lista se repinte. Se vio en el navegador.
    if (this.reordenandoGaleria()) {
      return;
    }
    const actual = this.galeria().map((i) => i.id);
    const desde = actual.indexOf(imagen.id);
    const hasta = desde + desplazamiento;
    if (desde < 0 || hasta < 0 || hasta >= actual.length) {
      return;
    }
    const pedido = [...actual];
    [pedido[desde], pedido[hasta]] = [pedido[hasta], pedido[desde]];

    this.errorReordenar.set(null);
    this.aviso.set(null);
    this.mutacionReordenarGaleria.mutate(
      { productoId: this.id(), imagenIds: pedido },
      {
        onSuccess: () => {
          this.aviso.set('admin.productos.editar.galeria.reordenada');
          // El foco sigue a la imagen que se movió, no al sitio donde estaba el botón: si se
          // quedara quieto, pulsar "subir" dos veces movería dos imágenes distintas. Se pide
          // aquí y se hace cuando la lista vuelva a pintarse — ver `enfocarAlRepintar`.
          this.enfocarAlRepintar.set({ id: imagen.id, desplazamiento, ordenEsperado: pedido });
        },
        onError: (error) =>
          this.errorReordenar.set(
            mensajeDeError(error, this.transloco, 'admin.productos.editar.galeria.errorReordenar'),
          ),
      },
    );
  }

  /**
   * El botón que movió la imagen, ya en su fila nueva. Puede no existir: si la imagen llegó a un
   * extremo, ese botón desaparece, y entonces el foco va al que sigue teniendo sentido.
   */
  private botonDeMoverDe(imagenId: string, desplazamiento: -1 | 1): HTMLElement | null {
    const fila = this.filas().find((f) => f.nativeElement.dataset['imagenId'] === imagenId);
    if (!fila) {
      return null;
    }
    // Hasta el `<button>` de dentro: el atributo cae en el host de `ts-boton`, que no es
    // enfocable.
    const direccion = desplazamiento === -1 ? 'subir' : 'bajar';
    return (
      fila.nativeElement.querySelector<HTMLElement>(`[data-mover="${direccion}"] button`) ??
      fila.nativeElement.querySelector<HTMLElement>('[data-mover] button')
    );
  }

  protected subirImagenPrincipal(): void {
    if (this.subiendoImagen() || this.cambiandoPrincipal()) {
      return;
    }
    const archivo = this.archivoSeleccionado();
    if (!archivo || !this.dimensionesArchivo || this.formularioImagen.invalid) {
      this.formularioImagen.markAllAsTouched();
      this.errorImagen.set(
        this.transloco.translate('admin.productos.editar.imagenPrincipal.faltanCampos'),
      );
      return;
    }
    this.errorImagen.set(null);
    this.avisoImagen.set(null);

    const { altEs, altEn } = this.formularioImagen.getRawValue();
    this.mutacionImagen.mutate(
      {
        productoId: this.id(),
        archivo,
        ancho: this.dimensionesArchivo.ancho,
        alto: this.dimensionesArchivo.alto,
        altEs,
        altEn,
      },
      {
        onSuccess: () => {
          this.limpiarPrevisualizacion();
          this.archivoSeleccionado.set(null);
          this.dimensionesArchivo = null;
          this.formularioImagen.reset();
          this.avisoImagen.set('admin.productos.editar.imagenPrincipal.subida');
          this.enfocarDespuesDePintar(() => this.avisoImagenPrincipal()?.nativeElement);
        },
        onError: () =>
          this.errorImagen.set(
            this.transloco.translate('admin.productos.editar.imagenPrincipal.error'),
          ),
      },
    );
  }
}
