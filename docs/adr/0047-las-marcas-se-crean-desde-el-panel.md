# ADR-0047: las marcas se crean desde el panel; las categorías siguen siendo migración

Fecha: 2026-09-20
Estado: aceptada
Matiza a: `V38` y `V54` (su razonamiento, que no se toca)

## Contexto

`V38` metió las diez categorías de tecnología por migración y dejó escrito el
porqué, que `V54` repitió al cargar las doce marcas reales del negocio:

> El dato real que toda instalación necesita es una migración; el ejemplo para
> poder desarrollar es una siembra.

El argumento es correcto y sigue en pie. Lo que separa es **dato real** de
**ficción del sembrador** —"Under Trail" y el "Celular TecnoSport Aurora" no
existen—, y por eso las categorías y las marcas del arranque no podían ir en
`SembradorCatalogo`, que además solo corre con la tabla de productos vacía.

Lo que ese razonamiento **no** separa, y aquí es donde hacía falta mirar otra
vez, es **dato de arranque** de **dato que crece**.

`V54` lo dice sin darse cuenta: *"no hay endpoint que cree marcas"*. Con esa
frase, el precio de la marca trece es una migración y un despliegue. Y la marca
trece no es un caso hipotético: llega en la lista del proveedor del lunes
siguiente, igual que llegaron estas doce.

## Decisión

**Las marcas se crean desde el panel.** `POST /api/v1/admin/marcas`, con su
pantalla en `/admin/marcas`.

**Las categorías no.** Siguen entrando por migración, y la diferencia no es
caprichosa:

| | Marcas | Categorías |
|---|---|---|
| Qué son | el fabricante de lo que se compra | la taxonomía de la tienda |
| Cada cuánto aparece una nueva | con cada lista de proveedor | casi nunca |
| De qué dependen | de a quién se le compre | de la línea, que es un enum del dominio |

Una categoría nueva suele venir acompañada de una decisión de negocio —¿es una
línea propia o cuelga de tecnología?— que merece quedar escrita en una migración
con su razonamiento, como quedó la de `V38`. Una marca nueva no decide nada: es
el nombre del fabricante.

**Las doce de `V54` se quedan donde están.** Eran el arranque: sin ellas no se
podía cargar el primer producto, y una instalación nueva las sigue necesitando.

## Lo que el panel puede y lo que no

**Puede:** listar todas las marcas y crear una.

**No puede renombrar ni borrar**, y las dos ausencias son deliberadas:

- **Renombrar** cambia lo que ve quien compra, en la ficha y en el filtro de la
  vitrina, y puede dejar en ridículo una URL compartida o una captura de
  WhatsApp. Es una operación con consecuencias hacia afuera.
- **Borrar** tiene que decidir antes qué pasa con los productos que cuelgan de
  esa marca, y esa decisión no existe todavía.

Ninguna de las dos hace falta para cargar catálogo, que es el problema que esta
decisión resuelve.

## La unicidad deja de distinguir mayúsculas

`V54` creó `marca_nombre_unico` sobre la columna tal cual, y **mientras las
marcas las escribiera una persona de una sola vez, eso bastaba**. Con un
formulario detrás no basta: "xiaomi" el martes y "Xiaomi" el jueves son dos
filas distintas para ese índice.

El daño es exactamente el que `V54` describe para el duplicado exacto —los
productos repartidos entre las dos marcas, el filtro de la vitrina ofreciendo
"Xiaomi" dos veces y cada una con media marca detrás—, así que la protección
tiene que cubrir el caso nuevo. La `V56` lo pasa a `lower(nombre)`.

**Sin `unaccent`:** comparar "Sony" con "Sóny" exigiría esa extensión, que es
una dependencia nueva del esquema. Queda fuera a propósito y no por descuido.

El caso de uso pregunta antes de escribir, y el índice sigue siendo el guardián
de verdad: entre la pregunta y la escritura hay una ventana por la que se cuela
una segunda petición, y lo único que la cierra es la base. La violación se
traduce en el adaptador —`apps/api/CLAUDE.md`: ninguna excepción de JPA sale de
`infrastructure`— con el patrón de `emision_de_guia`: consultar antes, y en el
`catch` traducir con lo que ya se tiene en la mano, sin volver a tocar una
sesión que el flush fallido ya rompió.

## Consecuencias

**A favor:**

- Cargar una lista de proveedor con una marca nueva deja de exigir un
  despliegue.
- La base admite una marca menos ambigua que antes: el duplicado por mayúsculas
  ya no entra.

**En contra, y hay que decirlo:**

- **El momento en que se descubre que falta la marca es a mitad del formulario
  de producto**, y la pantalla de marcas es otra: ir a crearla pierde lo
  tecleado. El enlace lo advierte en su propio texto. Si estorba, la salida es
  crearla en línea, y eso es otra tarea.
- **Una marca mal escrita se queda mal escrita.** Sin renombrar, el arreglo es
  una migración —justo el rodeo del que esta decisión sale— o crear la correcta
  y dejar la otra huérfana. Es el precio de no haber resuelto todavía qué hace
  un renombrado con lo que el comprador ya vio.

**Qué reabre esta decisión:** si aparece la necesidad real de renombrar —una
marca que cambia de nombre en el mercado, que pasa—, lo que hay que diseñar no
es un `PATCH` suelto sino qué le ocurre a las URL y a los filtros que ya
existen. Y si las categorías empiezan a cambiar con la frecuencia de las marcas,
esta tabla de diferencias deja de describir el negocio y hay que volver a
mirarla.
