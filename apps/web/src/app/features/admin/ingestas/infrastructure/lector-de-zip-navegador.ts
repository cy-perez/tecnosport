import { Injectable } from '@angular/core';
import { LectorDeZip } from '../domain/lector-de-zip.puerto';

/** Fin del directorio central: «PK\x05\x06». */
const FIRMA_FIN = 0x06054b50;
/** Una entrada del directorio central: «PK\x01\x02». */
const FIRMA_ENTRADA = 0x02014b50;
/** El registro de fin mide 22 bytes, más un comentario de hasta 65.535. */
const LARGO_FIN = 22;
const COMENTARIO_MAXIMO = 0xffff;

/**
 * Lee los nombres de un zip en el navegador sin descomprimirlo ni cargarlo entero: solo el final,
 * donde está el directorio central, que es la lista de lo que trae. Un zip de WhatsApp pesa
 * decenas de megas y el directorio, unos kilobytes.
 *
 * Sin librería a propósito (`CLAUDE.md`: cada dependencia es deuda): el formato del directorio es
 * estable desde hace treinta años y esto necesita una sola cosa de él. No lee ZIP64 —más de 65.535
 * archivos o más de 4 GB—, que ninguna exportación de chat alcanza: un zip así se reporta como
 * ilegible, y el panel lo trata como uno que no tiene la estructura acordada.
 */
@Injectable()
export class LectorDeZipNavegador implements LectorDeZip {
  async nombresDeEntradas(archivo: Blob): Promise<string[]> {
    const largoCola = Math.min(archivo.size, LARGO_FIN + COMENTARIO_MAXIMO);
    const cola = new DataView(await leer(archivo.slice(archivo.size - largoCola)));
    const fin = buscarFin(cola);

    const total = cola.getUint16(fin + 10, true);
    const largoDirectorio = cola.getUint32(fin + 12, true);
    const inicioDirectorio = cola.getUint32(fin + 16, true);
    if (total === 0xffff || largoDirectorio === 0xffffffff || inicioDirectorio === 0xffffffff) {
      throw new Error('El zip usa ZIP64, que una exportación de chat no necesita.');
    }

    const directorio = new DataView(
      await leer(archivo.slice(inicioDirectorio, inicioDirectorio + largoDirectorio)),
    );
    const nombres: string[] = [];
    let posicion = 0;
    for (let i = 0; i < total; i++) {
      if (directorio.getUint32(posicion, true) !== FIRMA_ENTRADA) {
        throw new Error('El directorio del zip está dañado.');
      }
      const largoNombre = directorio.getUint16(posicion + 28, true);
      const largoExtra = directorio.getUint16(posicion + 30, true);
      const largoComentario = directorio.getUint16(posicion + 32, true);
      const bytes = new Uint8Array(
        directorio.buffer,
        directorio.byteOffset + posicion + 46,
        largoNombre,
      );
      // En UTF-8 siempre. Sin la bandera de UTF-8 (bit 11) el estándar dice CP437, pero los nombres
      // acordados son ASCII y leen igual en los dos; un nombre con tildes mal leído no es uno de
      // los acordados de todas formas.
      nombres.push(new TextDecoder('utf-8').decode(bytes));
      posicion += 46 + largoNombre + largoExtra + largoComentario;
    }
    return nombres;
  }
}

/** El registro de fin, buscado desde el final: puede haber un comentario detrás. */
function buscarFin(cola: DataView): number {
  for (let i = cola.byteLength - LARGO_FIN; i >= 0; i--) {
    if (cola.getUint32(i, true) === FIRMA_FIN) {
      return i;
    }
  }
  throw new Error('El archivo no es un zip.');
}

/** `Blob.arrayBuffer` donde existe; `FileReader` donde no, como en algunos entornos de prueba. */
function leer(parte: Blob): Promise<ArrayBuffer> {
  if (typeof parte.arrayBuffer === 'function') {
    return parte.arrayBuffer();
  }
  return new Promise((resolver, rechazar) => {
    const lector = new FileReader();
    lector.onload = () => resolver(lector.result as ArrayBuffer);
    lector.onerror = () => rechazar(lector.error);
    lector.readAsArrayBuffer(parte);
  });
}
