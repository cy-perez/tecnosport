import { afterNextRender, computed, inject, Injectable, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Carrito } from '../domain/carrito.model';
import { REPOSITORIO_CARRITO } from '../domain/repositorio-carrito.puerto';
import { SnapshotLinea } from '../domain/snapshot-linea.model';
import { ALMACEN_CARRITO_ID } from '../domain/almacen-carrito-id.puerto';
import { ALMACEN_SNAPSHOT_LINEAS } from '../domain/almacen-snapshot-lineas.puerto';

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
  private readonly queryClient = inject(QueryClient);

  readonly carritoId = signal<string | null>(null);

  constructor() {
    afterNextRender(() => {
      const id = this.almacenCarritoId.leer();
      if (id) {
        this.carritoId.set(id);
      }
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
    queryFn: () => this.repositorio.ver(this.carritoId() as string),
    enabled: this.carritoId() !== null,
    staleTime: 60_000,
  }));

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
    mutationFn: (variables: { varianteId: string; cantidad: number }) =>
      this.agregarInterno(variables.varianteId, variables.cantidad),
  }));

  private readonly mutacionActualizar = injectMutation(() => ({
    mutationFn: (variables: { lineaId: string; cantidad: number }) =>
      this.actualizarInterno(variables.lineaId, variables.cantidad),
  }));

  private readonly mutacionEliminar = injectMutation(() => ({
    mutationFn: (lineaId: string) => this.eliminarInterno(lineaId),
  }));

  readonly agregando = computed(() => this.mutacionAgregar.isPending());

  async agregarAlCarrito(varianteId: string, cantidad: number, snapshot: SnapshotLinea): Promise<void> {
    this.almacenSnapshots.guardar(snapshot);
    await this.mutacionAgregar.mutateAsync({ varianteId, cantidad });
  }

  async actualizarCantidad(lineaId: string, cantidad: number): Promise<void> {
    await this.mutacionActualizar.mutateAsync({ lineaId, cantidad });
  }

  async eliminarLinea(lineaId: string): Promise<void> {
    await this.mutacionEliminar.mutateAsync(lineaId);
  }

  private async agregarInterno(varianteId: string, cantidad: number): Promise<Carrito> {
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
    const actualizado = await this.repositorio.agregarLinea(id, varianteId, cantidad);
    this.actualizarCache(actualizado);
    return actualizado;
  }

  private async actualizarInterno(lineaId: string, cantidad: number): Promise<Carrito> {
    const actualizado = await this.repositorio.actualizarCantidad(this.idOForzar(), lineaId, cantidad);
    this.actualizarCache(actualizado);
    return actualizado;
  }

  private async eliminarInterno(lineaId: string): Promise<Carrito> {
    const actualizado = await this.repositorio.eliminarLinea(this.idOForzar(), lineaId);
    this.actualizarCache(actualizado);
    return actualizado;
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
