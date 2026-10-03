import {
  AprobarBorrador,
  Borrador,
  BorradorDetalle,
  BorradoresPaginados,
  EditarBorrador,
  FotoBorrador,
} from '../domain/borrador.model';
import { RepositorioBorradoresAdmin } from '../domain/repositorio-borradores-admin.puerto';

export function borradorDePrueba(overrides: Partial<Borrador> = {}): Borrador {
  return {
    id: 'b-1',
    proveedorId: 'prov-1',
    publicacionId: 'pub-1',
    estado: 'EN_REVISION',
    titulo: 'Bolso tote en cuero sintético',
    tipo: 'BOLSO',
    linea: 'BOLSOS',
    precioProveedor: 53000,
    precioVentaSugerido: 72000,
    tallas: { tipo: 'UNICA', sirveHasta: 'L', valores: [] },
    cantidadTonos: 2,
    tonosNombrados: ['Negro', 'Café'],
    material: 'Cuero sintético',
    descripcion: 'Bolso tote en cuero sintético con cierre magnético.',
    altEn: null,
    alertas: [],
    motivoRechazo: null,
    productoId: null,
    creadoEn: '2026-09-30T15:00:00Z',
    ...overrides,
  };
}

export function fotoDePrueba(mensajeId: string): FotoBorrador {
  return { mensajeId, url: 'https://storage.local/' + mensajeId + '.jpg', pieDeFoto: null };
}

/** Doble escrito a mano. Aprobar deja el borrador APROBADO con un producto, como el servidor. */
export class RepositorioBorradoresAdminFalso implements RepositorioBorradoresAdmin {
  readonly ediciones: { id: string; cambios: EditarBorrador }[] = [];
  readonly aprobaciones: { id: string; aprobacion: AprobarBorrador }[] = [];
  readonly rechazos: { id: string; motivo: string }[] = [];
  readonly eliminados: string[] = [];
  readonly fotosDescartadas: { id: string; mensajeId: string }[] = [];
  /** Si se pone, `eliminar` revienta con esto: el 409 de un borrador que no se borra. */
  falloAlEliminar: unknown = null;

  constructor(
    private borradores: Borrador[] = [],
    private fotos: FotoBorrador[] = [],
    private readonly textos: string[] = [],
  ) {}

  async listar(): Promise<BorradoresPaginados> {
    return {
      items: this.borradores,
      pagina: 0,
      totalPaginas: this.borradores.length ? 1 : 0,
      totalBorradores: this.borradores.length,
    };
  }

  async obtener(id: string): Promise<BorradorDetalle> {
    const borrador = this.borradores.find((b) => b.id === id);
    if (!borrador) {
      throw new Error('no existe');
    }
    return { borrador, fotos: this.fotos, textos: this.textos };
  }

  async editar(id: string, cambios: EditarBorrador): Promise<Borrador> {
    this.ediciones.push({ id, cambios });
    return this.reemplazar(id, { ...cambios });
  }

  async aprobar(id: string, aprobacion: AprobarBorrador): Promise<Borrador> {
    this.aprobaciones.push({ id, aprobacion });
    return this.reemplazar(id, { estado: 'APROBADO', productoId: 'producto-nuevo' });
  }

  async rechazar(id: string, motivo: string): Promise<Borrador> {
    this.rechazos.push({ id, motivo });
    return this.reemplazar(id, { estado: 'RECHAZADO', motivoRechazo: motivo });
  }

  async descartarFoto(id: string, mensajeId: string): Promise<void> {
    this.fotosDescartadas.push({ id, mensajeId });
    this.fotos = this.fotos.filter((foto) => foto.mensajeId !== mensajeId);
  }

  async eliminar(id: string): Promise<void> {
    if (this.falloAlEliminar) {
      throw this.falloAlEliminar;
    }
    this.eliminados.push(id);
    this.borradores = this.borradores.filter((b) => b.id !== id);
  }

  private reemplazar(id: string, cambios: Partial<Borrador>): Borrador {
    const actual = this.borradores.find((b) => b.id === id) ?? borradorDePrueba({ id });
    const nuevo: Borrador = { ...actual, ...cambios };
    this.borradores = this.borradores.map((b) => (b.id === id ? nuevo : b));
    return nuevo;
  }
}
