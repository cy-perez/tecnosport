import { afterNextRender, computed, inject, Injectable, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { CarritoInexistenteError } from '../domain/carrito.errores';
import { Carrito, CarritoCotizado, LineaCarrito, LineaCotizada } from '../domain/carrito.model';
import { REPOSITORIO_CARRITO } from '../domain/repositorio-carrito.puerto';
import { SnapshotLinea } from '../domain/snapshot-linea.model';
import { ALMACEN_CARRITO_ID } from '../domain/almacen-carrito-id.puerto';
import { ALMACEN_SNAPSHOT_LINEAS } from '../domain/almacen-snapshot-lineas.puerto';
import { ALMACEN_LINEAS_SIN_MARCAR } from '../domain/almacen-lineas-sin-marcar.puerto';

/**
 * Único en toda la app (`providedIn: 'root'`), no una función de fábrica como
 * `usarBusquedaProductos`: el encabezado (siempre visible), la ficha y la página del carrito
 * necesitan compartir la MISMA señal de `carritoId` — si cada quien tuviera la suya, el encabezado
 * nunca se enteraría de que la ficha acaba de crear un carrito.
 *
 * El id del carrito vive en `localStorage`: nada que precargar en SSR (el servidor no sabe qué
 * carrito es "el de este visitante", no hay sesión todavía). La señal arranca en `null` y se llena
 * en `afterNextRender` — mismo patrón que ya usa `Encabezado` para la cookie de tema — para que el
 * SSR sirva siempre "sin carrito" de forma determinista y el cliente lo cargue justo después de
 * hidratar, sin pelear con la hidratación. Detalle en apps/web/CLAUDE.md.
 */
@Injectable({ providedIn: 'root' })
export class CarritoStore {
  private readonly repositorio = inject(REPOSITORIO_CARRITO);
  private readonly almacenCarritoId = inject(ALMACEN_CARRITO_ID);
  private readonly almacenSnapshots = inject(ALMACEN_SNAPSHOT_LINEAS);
  private readonly almacenSinMarcar = inject(ALMACEN_LINEAS_SIN_MARCAR);
  private readonly queryClient = inject(QueryClient);

  readonly carritoId = signal<string | null>(null);

  /** Las líneas que el comprador desmarcó; ver `AlmacenLineasSinMarcar`. */
  private readonly sinMarcar = signal<ReadonlySet<string>>(new Set());

  constructor() {
    afterNextRender(() => {
      const id = this.almacenCarritoId.leer();
      if (id) {
        this.carritoId.set(id);
      }
      this.sinMarcar.set(new Set(this.almacenSinMarcar.leer()));
    });
  }

  // staleTime alto a propósito: cada mutación ya deja la caché al día con la respuesta fresca
  // del servidor (`actualizarCache`). Con staleTime 0, el momento exacto en que la consulta pasa
  // de deshabilitada a habilitada (justo cuando agregarInterno crea el carrito) dispara un refetch
  // automático que compite con la propia mutación en curso y puede pisar su resultado con una
  // "foto" tomada antes de que la línea se agregara. No es una desconfianza en el caché: es no
  // pelear con una escritura que ya sabemos que es la más fresca posible.
  readonly consulta = injectQuery(() => ({
    queryKey: ['carrito', this.carritoId()] as const,
    queryFn: async () => {
      const id = this.carritoId() as string;
      const carrito = await this.repositorio.ver(id);
      if (carrito === null) {
        // El id guardado ya no existe en el servidor (purga de inactivos, base reiniciada). Se
        // suelta: con él puesto, el encabezado y la página del carrito seguían preguntando por un
        // carrito muerto, y agregar fallaba para siempre.
        this.soltarId(id);
      }
      return carrito;
    },
    enabled: this.carritoId() !== null,
    staleTime: 60_000,
  }));

  /**
   * Los precios de hoy, del servidor. Se invalida con cada cambio del carrito, porque la llave
   * incluye las líneas y sus cantidades: otra combinación es otra cotización.
   */
  readonly cotizacion = injectQuery(() => {
    const carrito = this.consulta.data();
    return {
      queryKey: [
        'carrito',
        this.carritoId(),
        'cotizacion',
        carrito?.lineas.map((linea) => `${linea.id}:${linea.cantidad}`).join(',') ?? '',
      ] as const,
      queryFn: (): Promise<CarritoCotizado | null> =>
        this.repositorio.cotizar(this.carritoId() as string),
      enabled: this.carritoId() !== null && (carrito?.lineas.length ?? 0) > 0,
      staleTime: 30_000,
    };
  });

  /**
   * Si la línea se puede pagar: la cotización no la trae sin precio. Mientras la cotización no ha
   * llegado, o si todavía no incluye una línea recién agregada, se da por vendible: decidirlo es
   * del servidor, que rechaza al crear el pedido la que ya no se vende.
   */
  lineaSeVende(lineaId: string): boolean {
    const cotizada = this.cotizacion.data()?.lineas.find((linea) => linea.lineaId === lineaId);
    return cotizada === undefined || cotizada.precioUnitario !== null;
  }

  private readonly lineasVendibles = computed(() =>
    (this.consulta.data()?.lineas ?? []).filter((linea) => this.lineaSeVende(linea.id)),
  );

  /**
   * Las casillas solo aparecen con dos líneas vendibles o más: con una no hay nada que elegir. Y
   * sin casillas todo cuenta como marcado, para que una línea desmarcada cuando había dos no se
   * quede fuera del pago, sin casilla con que volver a marcarla, al quitar la otra.
   */
  readonly seleccionHabilitada = computed(() => this.lineasVendibles().length >= 2);

  /**
   * Lo que se va a pagar: las líneas vendibles que siguen marcadas. Es lo único que el checkout
   * cotiza, consulta y manda en el pedido; las demás se quedan en el carrito para otra compra.
   */
  readonly lineasPorPagar = computed<readonly LineaCarrito[]>(() => {
    const vendibles = this.lineasVendibles();
    if (!this.seleccionHabilitada()) {
      return vendibles;
    }
    const sinMarcar = this.sinMarcar();
    return vendibles.filter((linea) => !sinMarcar.has(linea.id));
  });

  estaMarcada(lineaId: string): boolean {
    return !this.seleccionHabilitada() || !this.sinMarcar().has(lineaId);
  }

  readonly todasMarcadas = computed(
    () => this.lineasPorPagar().length === this.lineasVendibles().length,
  );

  marcar(lineaId: string, marcada: boolean): void {
    const siguiente = new Set(this.sinMarcar());
    if (marcada) {
      siguiente.delete(lineaId);
    } else {
      siguiente.add(lineaId);
    }
    this.fijarSinMarcar(siguiente);
  }

  marcarTodas(marcadas: boolean): void {
    this.fijarSinMarcar(new Set(marcadas ? [] : this.lineasVendibles().map((linea) => linea.id)));
  }

  /** Solo se guardan ids de este carrito: los de líneas que ya no existen no pintan nada. */
  private fijarSinMarcar(ids: ReadonlySet<string>): void {
    const existentes = new Set((this.consulta.data()?.lineas ?? []).map((linea) => linea.id));
    const vigentes = new Set([...ids].filter((id) => existentes.has(id)));
    this.sinMarcar.set(vigentes);
    this.almacenSinMarcar.guardar([...vigentes]);
  }

  /** Las líneas de la cotización que se van a pagar. */
  private readonly cotizadasPorPagar = computed<readonly LineaCotizada[]>(() => {
    const porPagar = new Set(this.lineasPorPagar().map((linea) => linea.id));
    return (this.cotizacion.data()?.lineas ?? []).filter((linea) => porPagar.has(linea.lineaId));
  });

  /**
   * Lo que el comprador ve como subtotal: la suma de lo marcado, con los precios del servidor.
   * Antes cada pantalla sumaba el precio guardado en el navegador al agregar, que podía estar viejo
   * o faltar (y entonces sumaba cero). Es la misma suma que hace el servidor —las líneas con
   * precio—, así que con todo marcado coincide con su subtotal; y es solo para mostrar: el pedido
   * lo vuelve a calcular el servidor. Cero sin nada marcado o mientras la cotización llega.
   */
  readonly subtotal = computed(() =>
    this.cotizadasPorPagar().reduce((suma, linea) => suma + (linea.subtotal ?? 0), 0),
  );

  /**
   * Lo que se ahorra lo marcado pagando sin pasarela —transferencia, en el punto o contra
   * entrega—: subtotal publicado menos subtotal de contado, los dos del servidor (ADR-0095). Cero
   * sin cotización o si no hay descuento; nunca negativo.
   */
  readonly ahorroSinPasarela = computed(() => {
    const contado = this.cotizadasPorPagar().reduce(
      (suma, linea) => suma + (linea.subtotalContado ?? 0),
      0,
    );
    return Math.max(0, this.subtotal() - contado);
  });

  /** El precio de hoy de una línea; `null` si todavía no llegó o si ya no se vende. */
  precioDeLinea(lineaId: string): number | null {
    return (
      this.cotizacion.data()?.lineas.find((linea) => linea.lineaId === lineaId)?.precioUnitario ??
      null
    );
  }

  readonly cantidadTotal = computed(
    () => this.consulta.data()?.lineas.reduce((total, linea) => total + linea.cantidad, 0) ?? 0,
  );

  /** Foto guardada al agregar la línea (`docs`: no autoritativa, solo para
   * pintar nombre/precio/imagen fuera de la propia página del carrito —
   * el resumen del checkout la usa con el mismo criterio). */
  snapshotDeLinea(varianteId: string): SnapshotLinea | null {
    return this.almacenSnapshots.leer(varianteId);
  }

  private readonly mutacionAgregar = injectMutation(() => ({
    mutationFn: (variables: {
      varianteId: string;
      cantidad: number;
      enlaceCompra: string | null;
    }) => this.agregarInterno(variables.varianteId, variables.cantidad, variables.enlaceCompra),
  }));

  private readonly mutacionActualizar = injectMutation(() => ({
    mutationFn: (variables: { lineaId: string; cantidad: number }) =>
      this.actualizarInterno(variables.lineaId, variables.cantidad),
  }));

  private readonly mutacionEliminar = injectMutation(() => ({
    mutationFn: (lineaId: string) => this.eliminarInterno(lineaId),
  }));

  readonly agregando = computed(() => this.mutacionAgregar.isPending());

  /**
   * @param enlaceCompra el del color de tecnología confirmado con el proveedor (ADR-0086); con él
   *     la línea queda con una unidad
   */
  async agregarAlCarrito(
    varianteId: string,
    cantidad: number,
    snapshot: SnapshotLinea,
    enlaceCompra: string | null = null,
  ): Promise<void> {
    this.almacenSnapshots.guardar(snapshot);
    await this.mutacionAgregar.mutateAsync({ varianteId, cantidad, enlaceCompra });
  }

  /**
   * El carrito dejó de existir porque sus líneas ya son de un pedido. No se llama al confirmar
   * —ahí el pedido todavía puede fallar y el reintento necesita el carrito intacto— sino cuando el
   * camino de pago ya salió bien y no hay vuelta atrás. Ver `checkout/presentation/confirmar`.
   *
   * Las fotos de línea se quedan: no estorban, se pisan solas al volver a agregar, y borrarlas
   * dejaría sin nombre ni precio a cualquier pantalla que todavía las esté pintando.
   */
  limpiar(): void {
    const id = this.carritoId();
    this.soltarId(id);
    if (id) {
      this.queryClient.removeQueries({ queryKey: ['carrito', id] });
    }
  }

  /**
   * Las líneas pagadas salen del carrito y las que se dejaron fuera se quedan, para otra compra.
   * Si se pagó todo —o no se sabe qué, `null`: un pedido anotado antes de que existiera la
   * selección—, es {@link limpiar}. Mismo momento que `limpiar`: cuando el pago ya salió bien.
   *
   * Lo que queda vuelve a estar marcado: es otra compra, y arranca como arrancan todas.
   *
   * <p>No lanza. Si quitar una línea falla, se queda en el carrito y el comprador la quita a mano;
   * es el mal menor frente a romper la pantalla final de una compra que ya está pagada.
   */
  async quitarPagadas(lineaIds: readonly string[] | null): Promise<void> {
    const id = this.carritoId();
    if (!id) {
      return;
    }
    try {
      const carrito = this.consulta.data() ?? (await this.repositorio.ver(id));
      const pagadas = new Set(lineaIds ?? []);
      if (lineaIds === null || !carrito || carrito.lineas.every((linea) => pagadas.has(linea.id))) {
        this.limpiar();
        return;
      }
      this.sinMarcar.set(new Set());
      this.almacenSinMarcar.guardar([]);
      for (const linea of carrito.lineas) {
        if (pagadas.has(linea.id)) {
          await this.eliminarLinea(linea.id);
        }
      }
    } catch {
      // Ver arriba: una línea pagada que se queda es lo que se puede arreglar a mano.
    }
  }

  /** Olvida el id solo si sigue siendo el activo: otra mutación pudo haber creado uno nuevo. */
  private soltarId(id: string | null): void {
    if (this.carritoId() !== id) {
      return;
    }
    this.almacenCarritoId.borrar();
    this.carritoId.set(null);
    this.sinMarcar.set(new Set());
    this.almacenSinMarcar.guardar([]);
  }

  async actualizarCantidad(lineaId: string, cantidad: number): Promise<void> {
    await this.mutacionActualizar.mutateAsync({ lineaId, cantidad });
  }

  async eliminarLinea(lineaId: string): Promise<void> {
    await this.mutacionEliminar.mutateAsync(lineaId);
  }

  private async agregarInterno(
    varianteId: string,
    cantidad: number,
    enlaceCompra: string | null,
  ): Promise<Carrito> {
    const guardado = this.carritoId();
    if (guardado) {
      try {
        const actualizado = await this.repositorio.agregarLinea(
          guardado,
          varianteId,
          cantidad,
          enlaceCompra,
        );
        this.actualizarCache(actualizado);
        return actualizado;
      } catch (error) {
        // El carrito se purgó entre que la página lo cargó y este clic. Lo que la persona quiere es
        // su producto en un carrito, no un error: se suelta el viejo y se sigue con uno nuevo.
        if (!(error instanceof CarritoInexistenteError)) {
          throw error;
        }
        this.limpiar();
      }
    }
    let id = this.carritoId();
    if (!id) {
      const nuevo = await this.repositorio.crear();
      id = nuevo.id;
      // Sembrar la caché ANTES de habilitar la consulta (carritoId.set): si la consulta pasa de
      // deshabilitada a habilitada sin datos todavía cacheados para esa llave, TanStack dispara su
      // propio fetch automático — que compite con el agregarLinea de más abajo y puede pisar su
      // resultado con una foto tomada antes de que la línea existiera. Con la caché ya poblada, esa
      // consulta no tiene motivo para refetchear (staleTime).
      this.actualizarCache(nuevo);
      this.carritoId.set(id);
      this.almacenCarritoId.guardar(id);
    }
    const actualizado = await this.repositorio.agregarLinea(id, varianteId, cantidad, enlaceCompra);
    this.actualizarCache(actualizado);
    return actualizado;
  }

  private async actualizarInterno(lineaId: string, cantidad: number): Promise<Carrito> {
    const actualizado = await this.soltandoSiNoExiste(() =>
      this.repositorio.actualizarCantidad(this.idOForzar(), lineaId, cantidad),
    );
    this.actualizarCache(actualizado);
    return actualizado;
  }

  private async eliminarInterno(lineaId: string): Promise<Carrito> {
    const actualizado = await this.soltandoSiNoExiste(() =>
      this.repositorio.eliminarLinea(this.idOForzar(), lineaId),
    );
    this.actualizarCache(actualizado);
    return actualizado;
  }

  /**
   * Cambiar una línea de un carrito que ya no existe no tiene arreglo —la línea se fue con él—,
   * pero el id sí se suelta, para que la pantalla pase a "carrito vacío" en vez de seguir fallando.
   */
  private async soltandoSiNoExiste(operacion: () => Promise<Carrito>): Promise<Carrito> {
    try {
      return await operacion();
    } catch (error) {
      if (error instanceof CarritoInexistenteError) {
        this.limpiar();
      }
      throw error;
    }
  }

  private actualizarCache(carrito: Carrito): void {
    this.queryClient.setQueryData(['carrito', carrito.id], carrito);
  }

  private idOForzar(): string {
    const id = this.carritoId();
    if (!id) {
      throw new Error('No hay un carrito activo.');
    }
    return id;
  }
}
