# Plantilla de entrega — inventario de vacíos legales

Cópiala y rellénala. El orden importa: primero la respuesta a la pregunta que de
verdad se hizo, después el detalle.

---

# Vacíos legales de [sitio] — [fecha]

**Alcance de esta revisión:** [qué documentos y qué partes del sistema se
recorrieron; qué quedó fuera y por qué]

---

## ¿Se puede abrir?

[Una frase. Sin rodeos. "Sí, con tres salvedades que hay que aceptar por
escrito" o "No: hay dos incumplimientos que un comprador provoca el primer día".]

---

## Promesas rastreadas

Cada promesa del documento, y dónde la cumple el sistema. La columna de veredicto
solo admite tres valores: **coincide**, **contradice**, **no existe**.

| # | Promesa | Dónde se promete | Dónde se cumple | Veredicto |
|---|---|---|---|---|
| 1 | | `archivo:línea` | `archivo:línea` | |

---

## Hallazgos

Ordenados por riesgo, no por esfuerzo. Cada uno clasificado como **bug**,
**decisión de negocio** o **hueco de la ley**, y con ruta y línea.

### 1. [Título del hallazgo]

- **Tipo:** bug / decisión de negocio / hueco de la ley
- **Nivel:** bloquea el lanzamiento / se atiende a mano / incoherencia latente /
  deuda de evidencia
- **Qué promete el sitio:** [cita literal, con su archivo]
- **Qué hace el sistema:** [lo verificado, con archivo y línea]
- **Qué pasa si nadie lo toca:** [el escenario concreto, con un comprador real
  haciendo algo normal]
- **Qué lo cierra:** [el cambio, o la decisión que hace falta antes del cambio]
- **Prueba que lo sostendría:** [qué comprobaría que no se reabre]

---

## Datos del negocio que faltan

Nada de esto se inventa. Sin estos datos, los hallazgos que dependen de ellos
quedan abiertos.

| Marca | Qué falta | Qué bloquea |
|---|---|---|
| `[[PLAZO DE ENTREGA REAL]]` | | |

---

## Puntos para revisión de abogado

Decisiones que no tienen una respuesta técnica correcta, sino una elección de
riesgo. Se explica la disyuntiva; la decisión no es de quien audita.

**1. [Tema]**
- Qué está en juego:
- Opción A y su consecuencia:
- Opción B y su consecuencia:
- Qué se dejó como está y por qué:

---

## Verificación normativa

Normas consultadas y fecha de verificación. Si algo cambió respecto del material
de referencia, se anota aquí.

| Norma | Qué se verificó | Fuente | Verificada el |
|---|---|---|---|
| | | | |

---

*Esta revisión la hizo una IA leyendo el código y los documentos publicados. No
es asesoría jurídica ni una certificación de cumplimiento. Los puntos señalados
arriba requieren revisión de un abogado titulado en Colombia antes de abrir al
público.*
