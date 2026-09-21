# ADR-0051 — Retirar de la vitrina no es cancelar lo vendido

**Fecha:** 2026-09-21
**Estado:** aceptado. Completa a `adr/0047` en el mismo sentido —el panel tiene que poder hacer lo
que el negocio necesita— y responde la pregunta que `PublicarProducto` dejó abierta por escrito.

## Contexto

`PublicarProducto` nació el 19 de septiembre de 2026 y su javadoc explicaba, con nombre propio, por
qué no traía el inverso:

> No hay `DespublicarProducto` todavía y no se añade "por simetría": retirar algo que ya se vendió
> tiene consecuencias que nadie ha decidido —qué pasa con los pedidos en curso, con los enlaces
> compartidos, con el sitemap ya indexado— y un caso de uso que se escribe sin esa decisión la toma
> en silencio.

El 21 de septiembre el panel aprendió a publicar desde una pantalla, y eso volvió la ausencia
urgente: un botón que deja un producto en la vitrina **para siempre**, a un clic de distancia, con
la única salida siendo un `UPDATE` a mano. La pregunta ya no era si hacía falta, sino si las tres
consecuencias se podían decidir con evidencia en vez de con criterio.

Se podían. Las tres estaban escritas en el código:

| Qué | Dónde se comprobó |
|---|---|
| Rejilla y ficha lo ocultan | `RepositorioProductosJpa` (`where p.estado = 'PUBLICADO'`) y `VerFichaDeProducto` |
| El sitemap lo suelta | `RepositorioMapaDelSitioJpa.listarProductosPublicados` |
| Los pedidos no lo miran | Ningún paso posterior a `CrearPedido` consulta `Producto.estado` |
| El carrito tampoco | `application/carrito` no menciona `EstadoProducto` en ningún sitio |

## Decisión

### 1. Existe `despublicar()`, y devuelve el producto a `BORRADOR`

No a un estado nuevo tipo `RETIRADO`. Un tercer estado obligaría a decidir qué significa en cada
consulta que hoy pregunta "¿está publicado?" —son cinco—, y el significado que se quiere ya lo
tiene `BORRADOR`: existe, se puede editar, no se vende.

### 2. Los pedidos en curso no se tocan, y esta es la decisión de fondo

Un pedido lleva sus líneas congeladas —precio, nombre, cantidad— desde que se crea, y su ciclo
—despacho, entrega, recaudo, retracto— no vuelve a mirar el catálogo. Retirar un producto de la
vitrina **no cancela** lo que ya se vendió, y tampoco libera sus reservas de inventario.

Confundir las dos cosas habría sido el error caro: quien retira un producto porque la foto está mal
no está diciendo "devuélvele la plata a los seis que ya lo compraron".

### 3. Sin invariante que lo bloquee

Nada impide retirar un producto con pedidos en curso, ni uno recién publicado, ni uno que nunca se
vendió. Retirar de la venta es justo lo que hay que poder hacer **cuando algo resulta estar mal**, y
una regla que lo condicionara a que no haya pedidos abiertos obligaría a esperar días —o a arreglarlo
por la base— en el caso en que más urge.

### 4. `DELETE` sobre el subrecurso que lo creó

`DELETE /api/v1/admin/productos/{id}/publicacion`: se borra **la publicación**, no el producto. La
alternativa —`POST /despublicacion`— nombraría un recurso que no existe, y un `PATCH` del estado
volvería a tratar una transición como si fuera editar un campo, que es justo lo que `publicar` ya
decidió no hacer.

Idempotente como su inverso: despublicar un borrador devuelve el estado que se pedía, no un 409.

### 5. Se registra como `warn`, no como `info`

Publicar es rutina; retirar algo de la vitrina no. Si dentro de un mes alguien pregunta por qué un
producto dejó de verse, esa línea del registro es la respuesta, y buscarla entre los `info` de un
día de operación normal no es buscar, es rezar.

### 6. El carrito que lo tenga falla al pagar, y se deja dicho

La línea sobrevive en el carrito y `CrearPedido` la rechaza como si la variante no existiera. No es
el mensaje ideal —dice "ya no está disponible" en vez de "lo retiramos"—, pero es el mismo camino
que cubre la variante borrada, y separarlos pide un caso de uso que distinga "no existe" de "existía
y se retiró". Queda escrito para quien lo mejore, en vez de arreglado a medias.

## Consecuencias

- El panel puede deshacer su propia acción, que era la única de las tres transiciones del catálogo
  —crear, medir, publicar— sin marcha atrás.
- Un enlace compartido de algo retirado responde 404. Para el buscador es correcto; para quien lo
  tenía guardado, es un producto que ya no está, que es la verdad.
- El sitemap se corrige solo en la siguiente generación, sin trabajo manual.
- Queda sin resolver, a propósito, el mensaje del carrito (decisión 6).

## Alternativas rechazadas

- **Un estado `RETIRADO` aparte de `BORRADOR`**, para distinguir "nunca se publicó" de "se
  publicó y se bajó". Aporta trazabilidad que hoy no pide nadie y cuesta revisar las cinco
  consultas que preguntan por el estado. El historial de esa transición, si algún día hace falta,
  es un registro de eventos y no un estado más.
- **Bloquear la retirada cuando hay pedidos en curso.** Invierte el propósito: lo que se quiere es
  poder sacar de la venta algo que está mal, y los pedidos ya aceptados son un asunto distinto —y
  con sus propias herramientas: cancelar, retracto, reembolso.
- **Cancelar los pedidos en curso al retirar.** Nadie lo pidió, mezcla dos decisiones de negocio
  distintas en un clic, y la que se toma en silencio es la cara.
