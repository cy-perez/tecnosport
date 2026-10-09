// La subida de las tomas del estudio a un producto, por la API del panel: compartida entre
// `cargar-catalogo.mjs` y `importar-lista-tecnologia.mjs`, que suben las mismas fotos con las
// mismas reglas.

import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";

/**
 * Lo que se sube de una toma, que **no es la maestra**.
 *
 * La maestra del estudio es un artefacto de archivo: 2000 px y medio megabyte. Hasta el 21 de
 * septiembre de 2026 era lo que llegaba al bucket, y en la primera medición de Lighthouse que
 * valió algo resultó ser el elemento más pesado de la portada **y** de la ficha, con 635 kB. La
 * variante web del mismo fotograma —AVIF, 1200 px— pesa 58.
 *
 * **Se niega en vez de caer a la maestra** si la variante no está. Caer sería volver al defecto
 * que esto corrige, y sin decir nada: la carga terminaría "bien" y el sitio seguiría pesando diez
 * veces lo que debe. El procesamiento del estudio no amplía, así que una toma sin variante es una
 * foto original demasiado pequeña, y eso se arregla con otra foto.
 *
 * El alto se deriva de la proporción de la maestra y no se lee del AVIF: leer su cabecera pide una
 * dependencia nueva para responder algo que ya se sabe. Comprobado contra el archivo real —las
 * maestras del estudio son cuadradas y el AVIF de 1200 mide 1200x1200—.
 */
export function paraLaWeb(foto, dondeSeUsa) {
  if (!foto.variantes?.length) {
    throw new Error(
      `${dondeSeUsa}: la toma ${foto.ruta} no tiene variante web (AVIF hasta 1200 px).\n` +
        "No se sube la maestra en su lugar: son 635 kB donde caben 58. Reprocesa el estudio, o " +
        "la foto original es demasiado pequeña para publicarla.",
    );
  }
  const [mayor] = foto.variantes;
  return {
    // De mayor a menor, como vienen del estudio. La primera es la base: de ella salen el alto, el
    // hash y la URL que se sirve cuando el navegador no elige.
    variantes: foto.variantes,
    vistaPrevia: foto.vistaPrevia,
    ruta: mayor.ruta,
    contentType: mayor.contentType,
    ancho: mayor.ancho,
    alto: Math.round((foto.alto * mayor.ancho) / foto.ancho),
  };
}

/**
 * Sube las variantes de una toma y devuelve el cuerpo de la confirmación.
 *
 * Una URL firmada y un `PUT` por cada ancho, más el JPEG de la vista previa si el estudio lo dejó.
 * Son tres o cuatro viajes donde antes había uno, y es el precio de que el navegador pueda elegir:
 * la portada pedía 211 KiB de AVIF de 1200 px para pintarlos en huecos de 180.
 *
 * El hash y el alto son los de la variante mayor, que es la que el agregado toma como base.
 *
 * @param pedir la función de la herramienta que llama a la API del panel con su sesión
 */
export async function subirVariantes(pedir, endpointDeSubida, web, titulo) {
  const variantes = [];
  let hash = null;
  for (const variante of web.variantes) {
    const bytes = readFileSync(variante.ruta);
    const subida = await pedir(endpointDeSubida, {
      method: "POST",
      body: JSON.stringify({ contentType: variante.contentType }),
    });
    const puesta = await fetch(subida.url, {
      method: "PUT",
      headers: { "Content-Type": variante.contentType },
      body: bytes,
    });
    if (!puesta.ok) {
      throw new Error(
        `La subida de ${variante.ancho} px a Cloud Storage respondió ${puesta.status}`,
      );
    }
    variantes.push({ ancho: variante.ancho, objectKey: subida.objectKey });
    if (variante.ancho === web.ancho) {
      hash = createHash("sha256").update(bytes).digest("hex");
    }
  }

  let objectKeyVistaPrevia = null;
  if (web.vistaPrevia) {
    const bytes = readFileSync(web.vistaPrevia.ruta);
    const subida = await pedir(endpointDeSubida, {
      method: "POST",
      body: JSON.stringify({ contentType: web.vistaPrevia.contentType }),
    });
    const puesta = await fetch(subida.url, {
      method: "PUT",
      headers: { "Content-Type": web.vistaPrevia.contentType },
      body: bytes,
    });
    if (!puesta.ok) {
      throw new Error(`La subida de la vista previa respondió ${puesta.status}`);
    }
    objectKeyVistaPrevia = subida.objectKey;
  }

  return {
    variantes,
    objectKeyVistaPrevia,
    alto: web.alto,
    hash,
    altEs: `${titulo} sobre fondo gris`,
    altEn: `${titulo} on a grey background`,
  };
}
