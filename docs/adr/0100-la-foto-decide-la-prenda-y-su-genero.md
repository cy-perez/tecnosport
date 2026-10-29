# ADR-0100: la foto decide la prenda y su género; la marca es Genérica

Fecha: 2026-10-29
Estado: aceptada.
Relacionados: `adr/0078` (la ingesta lee las fotos), `adr/0085` (la partición dama/caballero),
`adr/0089` y `adr/0092` (el género del calzado y «el texto manda»), `adr/0067` (la exportación
del chat).

## Contexto

La revisión de los borradores de Meraki del 28 de octubre de 2026 dejó cuatro problemas:

1. **Ningún anuncio decía para quién era la prenda.** «Buzo navideño», «Conjunto polo»,
   «Enterizo deportivo», «Vestido elegante»: todos de dama, y todos llegaban sin categoría,
   porque el panel solo proponía una para los ocho tipos que caen siempre en la misma hoja
   (la blusa, la falda…). El género solo se leía en el calzado.
2. **El proveedor nombraba mal la prenda.** Una «camiseta manga larga» con cuello rígido y
   botones de arriba abajo es una camisa, y su categoría es otra.
3. **La marca se elegía a mano en cada borrador**, aunque lo que llega de un proveedor se
   publica con la marca Genérica: el panel solo la proponía a las réplicas.
4. **Ocho borradores pintaban fotos rotas.** El chat traía `imagen omitida`: WhatsApp exporta
   sin el archivo la foto que el teléfono no había descargado. La ingesta las agrupa —sirven
   para saber dónde empieza y termina un álbum— y la API las devuelve con `url` nula, como
   dice su contrato; el panel las pintaba igual, como `<img src="">`.

## Decisión

1. **El lector de fotos dice la prenda y su género**, una vez por anuncio: `prenda.tipo` y
   `prenda.genero` en la raíz de `ia/lector-fotos/esquema.json`. No por foto: la prenda y el
   género no cambian de una vista a otra, y así la lectura no crece por cada foto.
2. **El género: manda el anuncio, después la foto**, igual que en el calzado (`adr/0092`):
   `GeneroDelAnuncio` primero, y la lectura solo si el texto no lo dice. Se guarda en el
   campo `generoDeCalzado` del borrador, que la partición dama/caballero de la ropa ya usaba
   (`adr/0085`): renombrarlo movía una columna y el contrato por un nombre.
3. **El tipo: manda la foto**, al revés que en todo lo demás. Cuando la lectura ve otra
   prenda que la del anuncio, el borrador se queda con la de la foto y lleva la alerta
   `TIPO_CORREGIDO_POR_FOTO`. Solo si las fotos son de ese producto y de nadie más: con varios
   productos en un anuncio o en un álbum, la prenda que vio la lectura puede ser la de otro.
   El título no se toca; lo corrige quien aprueba.
4. **Tres tipos nuevos**: `CAMISA`, `GORRA` y `BERMUDA`. Las tres tienen hoja en el catálogo
   y salían `CAMISETA`, `OTRO` y `SHORT`.
5. **La categoría que propone el panel sale del tipo y del género**: `ropa-{dama|caballero}-{hoja}`,
   con varias hojas candidatas cuando el árbol no es parejo —el buzo es `buzos` en la nube y
   `busos` en la migración `V63`—, y se propone la primera que exista. Una prenda unisex no
   tiene rama en la ropa: se queda con la hoja fija de su tipo, si la tiene, o la elige quien
   aprueba.
6. **Todo borrador llega con la marca Genérica** —ropa, calzado y bolsos—, sea réplica o no.
   `V152` la crea donde no existe, comparando sin tilde para no duplicar la de la nube.
7. **Una foto sin archivo no se pinta ni se aprueba**: el panel la cuenta y dice cuántas
   faltan, con las dos salidas —subirlas desde el panel, o abrir el chat para que se
   descarguen y exportarlo otra vez—. La API no cambia.

## Consecuencias

- **Lo ya ingerido no se recalcula**: la lectura guardada no trae `prenda`, y el borrador
  sigue sin género. Los borradores de Meraki del 28 de octubre se corrigen a mano o
  volviendo a subir el lote. Lo que sí arreglan solos son las fotos rotas y la marca.
- El cambio de esquema del lector se prueba contra la API real antes de fusionar: un enum
  mal formado da 400 y tumba la ingesta.
- La lectura de la ropa sigue siendo Haiku. Si el género o la prenda salen mal con
  frecuencia, la palanca es el modelo de `proveedores.lector-fotos`, no el prompt.
