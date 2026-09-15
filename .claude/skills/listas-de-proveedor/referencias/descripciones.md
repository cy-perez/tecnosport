# Estructura de la descripción

Todas las descripciones usan la misma estructura. Un catálogo donde cada ficha
está escrita distinta se ve improvisado y obliga al cliente a buscar el dato en un
lugar diferente cada vez.

## Plantilla

```markdown
[Párrafo de apertura: 50 a 80 palabras. Qué es, para quién y el dato técnico que
lo distingue de su versión anterior o de su competencia directa.]

## Características principales
- [5 a 7 viñetas, cada una un beneficio concreto con su dato: pantalla, procesador,
  cámara, batería, conectividad, resistencia]

## Ficha técnica
| Atributo | Detalle |
|---|---|
| Pantalla | |
| Procesador | |
| Memoria | |
| Cámara | |
| Batería | |
| Conectividad | |
| Sistema operativo | |
| Dimensiones y peso | |

## Contenido de la caja
- [lo que trae realmente]

## Garantía y notas
- [garantía, condición del equipo, aclaraciones de eSIM o compatibilidad]
```

Los atributos de la ficha cambian por categoría: un power bank lleva capacidad,
potencia de entrada y salida y número de puertos; un portátil lleva pantalla,
procesador, RAM, almacenamiento, gráficos, puertos y peso.

## Metadatos

- **Meta título**: máximo 60 caracteres, empieza por la marca y el modelo.
- **Meta descripción**: máximo 155 caracteres, dice qué es y un diferenciador.
  Sin "compra ya" ni signos de admiración.

## Tono

Informativo y verificable. El cliente que compra un celular de dos millones ya
comparó en tres sitios: lo que convence es el dato exacto, no el adjetivo.

- Nada de "el mejor", "increíble", "revolucionario".
- Cada afirmación técnica sale de la ficha oficial del fabricante. Si no se pudo
  confirmar, no entra.
- No prometer autonomía en horas, cobertura ni velocidades que el fabricante no
  declare.
- Español neutro, medidas en el sistema métrico, precios en pesos.

## Declaraciones que no se pueden omitir

- **Equipos activados**: decir que la garantía del fabricante ya está corriendo y
  desde cuándo, si se sabe.
- **Accesorios compatibles**: si el cargador o el accesorio no es de la marca del
  equipo, la ficha dice "compatible con", nunca la marca a secas.
- **eSIM**: si el equipo no tiene bandeja física, decirlo en la apertura, no en la
  ficha. Es el reclamo más común en equipos importados.
- **RAM extendida**: si la lista trae `(8+8+256)`, la ficha dice 8 GB de RAM y
  menciona aparte los 8 GB de RAM virtual que el equipo toma del almacenamiento.
  Sumarlas es una afirmación falsa que el cliente verifica en dos toques.
- **Garantía**: quién responde y por cuánto tiempo. En Colombia la garantía legal
  aplica aunque no se mencione; lo que evita el reclamo es que esté escrita.

Cuando la ficha use contenido de Open Icecat, la plantilla debe llevar la
mención "Specs Icecat" con enlace y el descargo de responsabilidad. Es condición
de la licencia, no un crédito opcional.

Para los textos legales del sitio (términos, garantías, retracto, datos
personales) usa la skill `textos-legales-comerciales`, no los redactes aquí.
