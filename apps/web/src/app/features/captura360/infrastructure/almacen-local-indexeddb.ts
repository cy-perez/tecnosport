import { Injectable } from '@angular/core';
import {
  AlmacenLocalDeCapturas,
  FotogramaGuardado,
  SesionGuardada,
} from '../domain/almacen-local-capturas.puerto';

const BASE = 'tecnosport-captura360';
/**
 * Sube a 2 el 23 de septiembre de 2026: la referencia de nivel de una sesión pasó de ser un par
 * `beta`/`gamma` a ser un vector de gravedad (`nivel-360.ts`). Una sesión guardada con la forma
 * vieja no se puede leer con la nueva —los campos ni siquiera se llaman igual— y arrastrarla
 * daría una referencia inventada, que es peor que perder la sesión a medias. Por eso la subida
 * **borra lo guardado** en vez de migrarlo: el asistente todavía no ha capturado un set en
 * producción, y lo que se pierde es una captura a medias de desarrollo.
 */
const VERSION = 2;
const SESIONES = 'sesiones';
const FOTOGRAMAS = 'fotogramas';

/**
 * IndexedDB a pelo, sin librería envolvente: son cinco operaciones y una sola tabla de índices.
 * Una dependencia nueva es deuda, y esta no se paga sola.
 *
 * Los `Blob` se guardan tal cual — IndexedDB los almacena nativos, que es justo el motivo por el
 * que este almacén no es `localStorage`.
 */
@Injectable()
export class AlmacenLocalIndexedDb implements AlmacenLocalDeCapturas {
  disponible(): boolean {
    return typeof indexedDB !== 'undefined';
  }

  async guardarSesion(sesion: SesionGuardada): Promise<void> {
    const base = await this.abrir();
    await this.escribir(base, SESIONES, (almacen) => almacen.put(sesion));
    base.close();
  }

  async sesionDe(productoId: string): Promise<SesionGuardada | null> {
    const base = await this.abrir();
    const sesiones = await this.leer<SesionGuardada>(base, SESIONES, (almacen) =>
      almacen.index('productoId').getAll(productoId),
    );
    base.close();
    if (sesiones.length === 0) {
      return null;
    }
    // Si quedó más de una a medias del mismo producto, la última es la que interesa.
    return sesiones.reduce((mas, sesion) =>
      sesion.actualizadaEn > mas.actualizadaEn ? sesion : mas,
    );
  }

  async guardarFotograma(fotograma: FotogramaGuardado): Promise<void> {
    const base = await this.abrir();
    await this.escribir(base, FOTOGRAMAS, (almacen) =>
      // Clave compuesta: repetir una toma sobreescribe la anterior en vez de acumular.
      almacen.put(fotograma, `${fotograma.sesionId}:${fotograma.orden}`),
    );
    base.close();
  }

  async fotogramasDe(sesionId: string): Promise<FotogramaGuardado[]> {
    const base = await this.abrir();
    const fotogramas = await this.leer<FotogramaGuardado>(base, FOTOGRAMAS, (almacen) =>
      almacen.index('sesionId').getAll(sesionId),
    );
    base.close();
    return fotogramas.sort((uno, otro) => uno.orden - otro.orden);
  }

  async olvidar(sesionId: string): Promise<void> {
    const base = await this.abrir();
    const fotogramas = await this.leer<FotogramaGuardado>(base, FOTOGRAMAS, (almacen) =>
      almacen.index('sesionId').getAll(sesionId),
    );
    await this.escribir(base, FOTOGRAMAS, (almacen) => {
      for (const fotograma of fotogramas) {
        almacen.delete(`${fotograma.sesionId}:${fotograma.orden}`);
      }
      return almacen.count();
    });
    await this.escribir(base, SESIONES, (almacen) => almacen.delete(sesionId));
    base.close();
  }

  private abrir(): Promise<IDBDatabase> {
    return new Promise((resolver, rechazar) => {
      const peticion = indexedDB.open(BASE, VERSION);
      peticion.onupgradeneeded = (evento) => {
        const base = peticion.result;
        // De la versión 1 a la 2 cambió la forma de la referencia de nivel: lo guardado no se
        // puede leer, así que se tira. Ver el comentario de `VERSION`.
        if (evento.oldVersion > 0 && evento.oldVersion < 2) {
          for (const nombre of [SESIONES, FOTOGRAMAS]) {
            if (base.objectStoreNames.contains(nombre)) {
              base.deleteObjectStore(nombre);
            }
          }
        }
        if (!base.objectStoreNames.contains(SESIONES)) {
          const sesiones = base.createObjectStore(SESIONES, { keyPath: 'sesionId' });
          sesiones.createIndex('productoId', 'productoId');
        }
        if (!base.objectStoreNames.contains(FOTOGRAMAS)) {
          const fotogramas = base.createObjectStore(FOTOGRAMAS);
          fotogramas.createIndex('sesionId', 'sesionId');
        }
      };
      peticion.onsuccess = () => resolver(peticion.result);
      peticion.onerror = () => rechazar(peticion.error);
    });
  }

  private escribir(
    base: IDBDatabase,
    almacen: string,
    operacion: (almacen: IDBObjectStore) => IDBRequest,
  ): Promise<void> {
    return new Promise((resolver, rechazar) => {
      const transaccion = base.transaction(almacen, 'readwrite');
      operacion(transaccion.objectStore(almacen));
      transaccion.oncomplete = () => resolver();
      transaccion.onerror = () => rechazar(transaccion.error);
    });
  }

  private leer<T>(
    base: IDBDatabase,
    almacen: string,
    operacion: (almacen: IDBObjectStore) => IDBRequest<T[]>,
  ): Promise<T[]> {
    return new Promise((resolver, rechazar) => {
      const peticion = operacion(base.transaction(almacen, 'readonly').objectStore(almacen));
      peticion.onsuccess = () => resolver(peticion.result ?? []);
      peticion.onerror = () => rechazar(peticion.error);
    });
  }
}
