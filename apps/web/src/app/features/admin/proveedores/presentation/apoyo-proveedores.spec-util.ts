import { DatosProveedor, Proveedor } from '../domain/proveedor.model';
import { RepositorioProveedoresAdmin } from '../domain/repositorio-proveedores-admin.puerto';

export function proveedorDePrueba(overrides: Partial<Proveedor> = {}): Proveedor {
  return {
    id: 'prov-1',
    nombre: 'Bolsos Medellín',
    nombreEnExportacion: 'Bolsos Mayorista Med',
    telefonoWhatsApp: '573001234567',
    linea: 'BOLSOS',
    factorDeMargen: 1.35,
    publicacionAutomatica: false,
    activo: true,
    ...overrides,
  };
}

/** Doble escrito a mano, sin librería de dobles (docs/06-testing.md). */
export class RepositorioProveedoresAdminFalso implements RepositorioProveedoresAdmin {
  readonly creados: DatosProveedor[] = [];
  readonly editados: { id: string; datos: DatosProveedor }[] = [];

  constructor(private proveedores: Proveedor[] = []) {}

  async listar(): Promise<Proveedor[]> {
    return this.proveedores;
  }

  async obtener(id: string): Promise<Proveedor> {
    const proveedor = this.proveedores.find((p) => p.id === id);
    if (!proveedor) {
      throw new Error('no existe');
    }
    return proveedor;
  }

  async crear(datos: DatosProveedor): Promise<Proveedor> {
    this.creados.push(datos);
    const proveedor = { id: 'prov-' + (this.proveedores.length + 1), ...datos };
    this.proveedores = [...this.proveedores, proveedor];
    return proveedor;
  }

  async editar(id: string, datos: DatosProveedor): Promise<Proveedor> {
    this.editados.push({ id, datos });
    return { id, ...datos };
  }
}
