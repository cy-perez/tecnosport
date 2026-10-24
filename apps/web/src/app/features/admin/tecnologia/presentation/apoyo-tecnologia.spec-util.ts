import {
  AprobarBorradorTecnologia,
  BorradorTecnologia,
  ConfiguracionTecnologia,
  EleccionDeConfiguracion,
  EstadoBorradorTecnologia,
  FotoNuevaDeBorrador,
} from '../domain/borrador-tecnologia.model';
import { RepositorioBorradoresTecnologia } from '../domain/repositorio-borradores-tecnologia.puerto';

export function configuracionDePrueba(
  cambios: Partial<ConfiguracionTecnologia> = {},
): ConfiguracionTecnologia {
  return {
    sku: 'a17-1-sim',
    titulo: 'Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM',
    ram: '8GB',
    almacenamiento: '256GB',
    sim: '1 SIM',
    costoProveedor: 675000,
    precioMercado: 849900,
    coloresSugeridos: ['Negro'],
    coloresElegidos: [],
    precioVenta: null,
    ...cambios,
  };
}

export function borradorTecnologiaDePrueba(
  cambios: Partial<BorradorTecnologia> = {},
): BorradorTecnologia {
  return {
    id: 'bt-1',
    proveedorId: 'prov-1',
    idModelo: 'samsung-galaxy-a17-5g',
    titulo: 'Samsung Galaxy A17 5G',
    marcaSugerida: 'Samsung',
    categoriaSugerida: 'celulares',
    descripcion: 'El Galaxy A17 5G.',
    descripcionDeLaSkill: 'El Galaxy A17 5G.',
    fichaEditada: false,
    fichaNuevaDeLaSkill: false,
    paleta: ['Negro', 'Gris', 'Azul'],
    configuraciones: [
      configuracionDePrueba(),
      configuracionDePrueba({
        sku: 'a17-dual-sim',
        titulo: 'Samsung Galaxy A17 5G 8GB RAM 256GB Dual SIM',
        sim: 'Dual SIM',
        costoProveedor: 690000,
        precioMercado: null,
        coloresSugeridos: [],
      }),
    ],
    productoId: null,
    estado: 'EN_REVISION',
    motivoRechazo: null,
    vistoEn: '2026-10-08T05:00:00Z',
    creadoEn: '2026-10-08T15:00:00Z',
    // Las cuatro que exige un modelo nuevo: así cada prueba de la aprobación prueba lo suyo.
    cantidadDeFotos: 4,
    fotosQueFaltan: 0,
    fotos: [1, 2, 3, 4].map((n) => ({
      id: `foto-${n}`,
      url: `https://firmada.local/foto-${n}.avif`,
      ancho: 1200,
      alto: 1200,
      altEs: `Samsung Galaxy A17 5G sobre fondo blanco ${n}`,
      altEn: `Samsung Galaxy A17 5G on a white background ${n}`,
    })),
    ...cambios,
  };
}

/** Doble escrito a mano: guarda lo que le piden y responde como el servidor. */
export class RepositorioBorradoresTecnologiaFalso implements RepositorioBorradoresTecnologia {
  readonly elecciones: { id: string; elecciones: readonly EleccionDeConfiguracion[] }[] = [];
  readonly aprobaciones: { id: string; aprobacion: AprobarBorradorTecnologia }[] = [];
  readonly rechazos: { id: string; motivo: string }[] = [];
  readonly subidas: { id: string; foto: FotoNuevaDeBorrador }[] = [];
  readonly quitadas: { id: string; fotoId: string }[] = [];
  readonly ordenes: { id: string; fotoIds: readonly string[] }[] = [];
  readonly fichas: { id: string; descripcion: string | null }[] = [];
  /** Si se pone, editar la ficha revienta con esto. */
  falloAlEditarFicha: unknown = null;
  /** Si se pone, aprobar revienta con esto. */
  falloAlAprobar: unknown = null;

  constructor(private borradores: BorradorTecnologia[] = []) {}

  /** Lo que respondería el servidor después de otra importación. */
  reemplazar(borrador: BorradorTecnologia): void {
    this.borradores = this.borradores.map((b) => (b.id === borrador.id ? borrador : b));
  }

  async listar(estado: EstadoBorradorTecnologia): Promise<BorradorTecnologia[]> {
    return this.borradores.filter((b) => b.estado === estado);
  }

  async obtener(id: string): Promise<BorradorTecnologia> {
    const borrador = this.borradores.find((b) => b.id === id);
    if (!borrador) {
      throw new Error('no existe');
    }
    return borrador;
  }

  async elegir(
    id: string,
    elecciones: readonly EleccionDeConfiguracion[],
  ): Promise<BorradorTecnologia> {
    this.elecciones.push({ id, elecciones });
    return this.obtener(id);
  }

  async aprobar(id: string, aprobacion: AprobarBorradorTecnologia): Promise<string> {
    if (this.falloAlAprobar) {
      throw this.falloAlAprobar;
    }
    this.aprobaciones.push({ id, aprobacion });
    this.borradores = this.borradores.map((b) =>
      b.id === id ? { ...b, estado: 'APROBADO', productoId: 'prod-1' } : b,
    );
    return 'prod-1';
  }

  async rechazar(id: string, motivo: string): Promise<BorradorTecnologia> {
    this.rechazos.push({ id, motivo });
    this.borradores = this.borradores.map((b) =>
      b.id === id ? { ...b, estado: 'RECHAZADO', motivoRechazo: motivo } : b,
    );
    return this.obtener(id);
  }

  async subirFoto(id: string, foto: FotoNuevaDeBorrador): Promise<BorradorTecnologia> {
    this.subidas.push({ id, foto });
    const actual = await this.obtener(id);
    const nueva = {
      id: `foto-subida-${this.subidas.length}`,
      url: `https://firmada.local/subida-${this.subidas.length}.jpg`,
      ancho: foto.ancho,
      alto: foto.alto,
      altEs: foto.altEs,
      altEn: foto.altEn,
    };
    this.reemplazar({
      ...actual,
      fotos: [...actual.fotos, nueva],
      cantidadDeFotos: actual.fotos.length + 1,
    });
    return this.obtener(id);
  }

  async quitarFoto(id: string, fotoId: string): Promise<void> {
    this.quitadas.push({ id, fotoId });
    const actual = await this.obtener(id);
    const fotos = actual.fotos.filter((f) => f.id !== fotoId);
    this.reemplazar({ ...actual, fotos, cantidadDeFotos: fotos.length });
  }

  async reordenarFotos(id: string, fotoIds: readonly string[]): Promise<BorradorTecnologia> {
    this.ordenes.push({ id, fotoIds });
    const actual = await this.obtener(id);
    const fotos = fotoIds.map((fid) => actual.fotos.find((f) => f.id === fid)!);
    this.reemplazar({ ...actual, fotos });
    return this.obtener(id);
  }

  async editarFicha(id: string, descripcion: string | null): Promise<BorradorTecnologia> {
    if (this.falloAlEditarFicha) {
      throw this.falloAlEditarFicha;
    }
    this.fichas.push({ id, descripcion });
    const actual = await this.obtener(id);
    this.reemplazar({
      ...actual,
      descripcion: descripcion ?? actual.descripcionDeLaSkill,
      fichaEditada: descripcion !== null,
      fichaNuevaDeLaSkill: false,
    });
    return this.obtener(id);
  }
}
