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
    tallasPorTono: [],
    preciosAdicionales: [],
    ...overrides,
  };
}

export function fotoDePrueba(mensajeId: string): FotoBorrador {
  return {
    mensajeId,
    url: 'https://storage.local/' + mensajeId + '.jpg',
    pieDeFoto: null,
    origen: 'PROVEEDOR',
    tonoSugerido: null,
  };
}

/** Doble escrito a mano. Aprobar deja el borrador APROBADO con un producto, como el servidor. */
export class RepositorioBorradoresAdminFalso implements RepositorioBorradoresAdmin {
  readonly ediciones: { id: string; cambios: EditarBorrador }[] = [];
  readonly aprobaciones: { id: string; aprobacion: AprobarBorrador }[] = [];
  readonly rechazos: { id: string; motivo: string }[] = [];
  readonly eliminados: string[] = [];
  readonly fotosDescartadas: { id: string; mensajeId: string }[] = [];
  readonly fotosSubidas: { id: string; archivo: File }[] = [];
  readonly particiones: { id: string; fotos: readonly string[] }[] = [];
  readonly movidas: { id: string; mensajeId: string; destinoId: string }[] = [];
  /** Los otros borradores de la publicación que devuelve `obtener`. */
  hermanos: { id: string; titulo: string }[] = [];
  /** Por nombre de archivo: si se pone, subir ese revienta con esto. */
  readonly fallosAlSubir = new Map<string, unknown>();
  /** Si se pone, `eliminar` revienta con esto: el 409 de un borrador que no se borra. */
  falloAlEliminar: unknown = null;
  /** El tamaño de tanda del borrado en bloque; chico para que las pruebas vean varias. */
  tandaDeBorrado = 100;
  /** Cuántas tandas pidió la pantalla. */
  tandasPedidas = 0;

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
    return { borrador, fotos: this.fotos, textos: this.textos, hermanos: this.hermanos };
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

  /** Como el servidor: la foto queda al final, del panel, y el borrador deja de estar sin fotos. */
  async subirFoto(id: string, archivo: File): Promise<FotoBorrador> {
    const fallo = this.fallosAlSubir.get(archivo.name);
    if (fallo) {
      throw fallo;
    }
    this.fotosSubidas.push({ id, archivo });
    const foto: FotoBorrador = {
      mensajeId: 'subida-' + this.fotosSubidas.length,
      url: 'https://storage.local/subida-' + this.fotosSubidas.length + '.jpg',
      pieDeFoto: null,
      origen: 'PANEL',
      tonoSugerido: null,
    };
    this.fotos = [...this.fotos, foto];
    const actual = this.borradores.find((b) => b.id === id);
    if (actual) {
      this.reemplazar(id, { alertas: actual.alertas.filter((a) => a !== 'SIN_FOTOS') });
    }
    return foto;
  }

  /** Como el servidor: el nuevo se lleva las fotos nombradas, que este deja de tener. */
  async partir(id: string, fotos: readonly string[]): Promise<Borrador> {
    this.particiones.push({ id, fotos });
    const origen = this.borradores.find((b) => b.id === id) ?? borradorDePrueba({ id });
    const nuevo: Borrador = { ...origen, id: id + '-parte' };
    this.borradores = [...this.borradores, nuevo];
    this.fotos = this.fotos.filter((foto) => !fotos.includes(foto.mensajeId));
    return nuevo;
  }

  /** Como el servidor: la foto deja de verse en este borrador. */
  async moverFoto(id: string, mensajeId: string, destinoId: string): Promise<void> {
    this.movidas.push({ id, mensajeId, destinoId });
    this.fotos = this.fotos.filter((foto) => foto.mensajeId !== mensajeId);
  }

  async eliminar(id: string): Promise<void> {
    if (this.falloAlEliminar) {
      throw this.falloAlEliminar;
    }
    this.eliminados.push(id);
    this.borradores = this.borradores.filter((b) => b.id !== id);
  }

  async contarSinAprobar(): Promise<number> {
    return this.sinAprobar().length;
  }

  /** Como el servidor: los aprobados no se tocan y se dice cuántos quedan. */
  async eliminarSinAprobar(): Promise<{ eliminados: number; quedan: number }> {
    this.tandasPedidas++;
    const tanda = this.sinAprobar().slice(0, this.tandaDeBorrado);
    this.borradores = this.borradores.filter((b) => !tanda.includes(b));
    return { eliminados: tanda.length, quedan: this.sinAprobar().length };
  }

  private sinAprobar(): Borrador[] {
    return this.borradores.filter((b) => b.estado === 'EN_REVISION' || b.estado === 'RECHAZADO');
  }

  private reemplazar(id: string, cambios: Partial<Borrador>): Borrador {
    const actual = this.borradores.find((b) => b.id === id) ?? borradorDePrueba({ id });
    const nuevo: Borrador = { ...actual, ...cambios };
    this.borradores = this.borradores.map((b) => (b.id === id ? nuevo : b));
    return nuevo;
  }
}
