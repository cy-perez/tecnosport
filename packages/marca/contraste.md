# Informe de contraste

Umbrales WCAG 2.1: 4.5:1 para texto normal, 3:1 para texto grande (24px o
19px en negrita), iconos y bordes de control.

## Modo claro

| Par | Frente | Fondo | Ratio | Nivel |
|---|---|---|---|---|
| Texto principal sobre fondo | `#14171C` | `#F5F6F8` | 16.61:1 | AAA |
| Texto sobre tarjeta | `#14171C` | `#FFFFFF` | 17.96:1 | AAA |
| Texto secundario sobre fondo | `#5B6472` | `#F5F6F8` | 5.53:1 | AA |
| Texto secundario sobre tarjeta | `#5B6472` | `#FFFFFF` | 5.98:1 | AA |
| Texto del boton principal | `#FFFFFF` | `#1B1F26` | 16.53:1 | AAA |
| Primario como texto o icono sobre fondo | `#1B1F26` | `#F5F6F8` | 15.28:1 | AAA (texto grande) |
| Texto del boton principal en hover | `#FFFFFF` | `#3B3E44` | 10.72:1 | AAA |
| Texto del boton de acento | `#14171C` | `#F5B301` | 9.69:1 | AAA |
| Etiqueta 'En stock' sobre tarjeta | `#14804A` | `#FFFFFF` | 4.98:1 | AA |
| Aviso sobre tarjeta | `#A15C00` | `#FFFFFF` | 5.19:1 | AA |
| Mensaje de error sobre fondo | `#B3261E` | `#F5F6F8` | 6.04:1 | AA |
| Borde de campo de formulario | `#7C8595` | `#FFFFFF` | 3.72:1 | AA (texto grande) |
| Anillo de foco sobre fondo | `#1B1F26` | `#F5F6F8` | 15.28:1 | AAA (texto grande) |
| Texto sobre la franja de marca | `#FFFFFF` | `#1B1F26` | 16.53:1 | AAA |
| Texto sobre la franja de datos | `#FFFFFF` | `#121419` | 18.42:1 | AAA |
| Texto sobre encabezado de tabla o codigo | `#14171C` | `#E9ECF0` | 15.16:1 | AAA |

## Modo oscuro

El modo oscuro no es invertir colores: se verifica aparte, porque el gris
suave que funciona sobre blanco casi nunca funciona sobre negro.

| Par | Frente | Fondo | Ratio | Nivel |
|---|---|---|---|---|
| Texto principal sobre fondo | `#EDF0F4` | `#0E1217` | 16.44:1 | AAA |
| Texto sobre tarjeta | `#EDF0F4` | `#191E26` | 14.64:1 | AAA |
| Texto secundario sobre fondo | `#98A2B3` | `#0E1217` | 7.3:1 | AAA |
| Texto secundario sobre tarjeta | `#98A2B3` | `#191E26` | 6.5:1 | AA |
| Texto del boton principal | `#14171C` | `#F5B301` | 9.69:1 | AAA |
| Primario como texto o icono sobre fondo | `#F5B301` | `#0E1217` | 10.14:1 | AAA (texto grande) |
| Texto del boton principal en hover | `#14171C` | `#D89E01` | 7.54:1 | AAA |
| Texto del boton de acento | `#14171C` | `#F5B301` | 9.69:1 | AAA |
| Etiqueta 'En stock' sobre tarjeta | `#3DBB7E` | `#191E26` | 6.86:1 | AA |
| Aviso sobre tarjeta | `#E5912F` | `#191E26` | 6.71:1 | AA |
| Mensaje de error sobre fondo | `#F27168` | `#0E1217` | 6.57:1 | AA |
| Borde de campo de formulario | `#636972` | `#191E26` | 3.02:1 | AA (texto grande) |
| Anillo de foco sobre fondo | `#F5B301` | `#0E1217` | 10.14:1 | AAA (texto grande) |
| Texto sobre la franja de marca | `#EDF0F4` | `#191E26` | 14.64:1 | AAA |
| Texto sobre la franja de datos | `#EDF0F4` | `#101419` | 16.17:1 | AAA |
| Texto sobre encabezado de tabla o codigo | `#EDF0F4` | `#242A34` | 12.62:1 | AAA |

## Pares prohibidos por el manual de marca

No son fallas del kit: son combinaciones que el sistema **no debe**
producir. Se listan con su ratio real para que nadie las reintroduzca
por descuido.

| Combinacion | Ratio | Por que se prohibe |
|---|---|---|
| `#F5B301` sobre `#FFFFFF` | 1.85:1 | El ambar sobre blanco da 1.85:1. Nunca como texto ni como icono sobre fondo claro: solo como relleno con grafito encima. |
| `#F5B301` sobre `#F5F6F8` | 1.71:1 | Mismo caso sobre el fondo de pagina. Un precio en ambar sobre gris claro no se lee. |
| `#5B6472` sobre `#F5B301` | 3.23:1 | Gris medio sobre ambar: no alcanza. Sobre el boton de acento el texto va en grafito. |

Todos los pares en uso cumplen el umbral que les corresponde.
