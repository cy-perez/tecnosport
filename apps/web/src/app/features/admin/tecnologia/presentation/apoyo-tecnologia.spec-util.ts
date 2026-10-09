import {
  AprobarBorradorTecnologia,
  BorradorTecnologia,
  ConfiguracionTecnologia,
  EleccionDeConfiguracion,
  EstadoBorradorTecnologia,
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
    ...cambios,
  };
}

/** Doble escrito a mano: guarda lo que le piden y responde como el servidor. */
export class RepositorioBorradoresTecnologiaFalso implements RepositorioBorradoresTecnologia {
  readonly elecciones: { id: string; elecciones: readonly EleccionDeConfiguracion[] }[] = [];
  readonly aprobaciones: { id: string; aprobacion: AprobarBorradorTecnologia }[] = [];
  readonly rechazos: { id: string; motivo: string }[] = [];
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
}
