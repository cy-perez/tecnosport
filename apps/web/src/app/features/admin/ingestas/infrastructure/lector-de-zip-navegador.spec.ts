import { LectorDeZipNavegador } from './lector-de-zip-navegador';

/**
 * Un zip de verdad, armado byte a byte: cabecera local y entrada del directorio central por cada
 * archivo, sin comprimir, y el registro de fin con su comentario opcional. Es lo que escriben
 * WhatsApp y el «Enviar a carpeta comprimida» de Windows, salvo la compresión, que al lector no le
 * importa porque no abre los archivos.
 */
function zipCon(archivos: Record<string, string>, comentario = ''): Blob {
  const texto = new TextEncoder();
  const locales: Uint8Array[] = [];
  const centrales: Uint8Array[] = [];
  let desplazamiento = 0;
  for (const [nombre, contenido] of Object.entries(archivos)) {
    const nombreBytes = texto.encode(nombre);
    const datos = texto.encode(contenido);
    const local = new Uint8Array(30 + nombreBytes.length + datos.length);
    const l = new DataView(local.buffer);
    l.setUint32(0, 0x04034b50, true);
    l.setUint16(6, 0x0800, true);
    l.setUint32(18, datos.length, true);
    l.setUint32(22, datos.length, true);
    l.setUint16(26, nombreBytes.length, true);
    local.set(nombreBytes, 30);
    local.set(datos, 30 + nombreBytes.length);
    locales.push(local);

    const central = new Uint8Array(46 + nombreBytes.length);
    const c = new DataView(central.buffer);
    c.setUint32(0, 0x02014b50, true);
    c.setUint16(8, 0x0800, true);
    c.setUint32(20, datos.length, true);
    c.setUint32(24, datos.length, true);
    c.setUint16(28, nombreBytes.length, true);
    c.setUint32(42, desplazamiento, true);
    central.set(nombreBytes, 46);
    centrales.push(central);
    desplazamiento += local.length;
  }
  const largoCentral = centrales.reduce((suma, c) => suma + c.length, 0);
  const comentarioBytes = texto.encode(comentario);
  const fin = new Uint8Array(22 + comentarioBytes.length);
  const f = new DataView(fin.buffer);
  f.setUint32(0, 0x06054b50, true);
  f.setUint16(8, centrales.length, true);
  f.setUint16(10, centrales.length, true);
  f.setUint32(12, largoCentral, true);
  f.setUint32(16, desplazamiento, true);
  f.setUint16(20, comentarioBytes.length, true);
  fin.set(comentarioBytes, 22);
  return new Blob([...locales, ...centrales, fin] as BlobPart[]);
}

describe('LectorDeZipNavegador', () => {
  const lector = new LectorDeZipNavegador();

  it('lee los nombres del directorio central, con su carpeta', async () => {
    const zip = zipCon({
      'Meraki.txt': '[6/10/26, 9:14:25 a. m.] Meraki: hola',
      'MerakiMen.txt': '7/10/2026, 11:02 - Meraki: hola',
      'fotos/IMG-20261007-WA0040.jpg': 'jpg',
    });

    expect(await lector.nombresDeEntradas(zip)).toEqual([
      'Meraki.txt',
      'MerakiMen.txt',
      'fotos/IMG-20261007-WA0040.jpg',
    ]);
  });

  it('encuentra el final aunque el zip traiga un comentario', async () => {
    const zip = zipCon({ 'Meraki.txt': 'a' }, 'Exportado por WhatsApp');

    expect(await lector.nombresDeEntradas(zip)).toEqual(['Meraki.txt']);
  });

  it('un archivo que no es zip falla', async () => {
    await expect(
      lector.nombresDeEntradas(new Blob(['no soy un zip, soy un texto cualquiera'])),
    ).rejects.toThrow();
  });
});
