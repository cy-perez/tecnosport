# ADR-0091: el tenis puesto y con utilería de otra marca se publica

Fecha: 2026-10-26
Estado: aceptada
Cambia: `ADR-0089`, decisiones 4 y 5 (solo pasan los tenis que se ven solos), y `ADR-0090`,
decisión 6 (el filtro nombra los casos que se colaron).

## Contexto

La segunda corrida del experimento de `ADR-0090` sobre la exportación de Tenis Al Pie dejó pasar
60 de las 138 fotos que llegaron al lector. Revisando las descartadas, el negocio encontró unas 40
que debían publicarse, y casi todas salían por la regla de `ADR-0089`, no por un error del lector:

- **El tenis puesto**: el proveedor fotografía buena parte de sus modelos en el pie, y de varios
  modelos no hay otra foto.
- **La utilería de otra marca**: el Samba en la mano junto a un libro Cartier, el Diesel sobre una
  caja de McQueen, el Adidas con una caja Supreme al fondo, el tenis puesto junto a un balón
  Spalding.

Las que sí estaban bien descartadas eran de otra clase: la bota (16/10, WA0105) y el fotograma de
un video con el ícono de silencio encima (23/10, WA0383).

## Decisión

Decidida por el negocio el 26 de octubre de 2026.

1. **Se publica la foto del tenis puesto y la que tiene artículos de otras marcas.** De una tanda
   de calzado solo quedan fuera lo que no es un tenis —Crocs, chancla, sandalia, bota—, la foto sin
   calzado, la captura de pantalla de otra aplicación y el fotograma de un video.
2. **El lector dice si la foto se puede publicar** (`foto_publicable`), en vez de si el tenis se ve
   solo (`producto_solo`). El campo cambia de nombre porque cambia lo que significa: con el nombre
   viejo, el modelo seguía empujado a descartar todo lo que no se viera solo. Una lectura guardada
   antes se sigue leyendo con el campo viejo.
3. **El tenis puesto y el mismo en la mano son uno solo** para `mismo_tenis_que`: se juntan en el
   mismo borrador.

## Consecuencias

- **El esquema del lector cambió**: `LectorDeFotosClaudeContratoTest` contra la API real se corre
  antes de fusionar.
- **Las tandas sin precio siguen fuera** (`ADR-0089`, decisión 2): las 62 fotos de las tandas del
  15/10 a las 09:47 y del 17/10 por la mañana no llegan al lector por eso, no por este filtro.
- **La utilería de otras marcas se lleva al abogado** (`docs/14`, punto 10): una foto publicada con
  un libro Cartier o una bolsa Dior al lado puede leerse como una asociación con esas marcas, y es
  la razón por la que `ADR-0089` las descartaba.
- **Quien aprueba elige la principal.** Con más fotos por tenis, la primera de la tanda puede ser
  la del tenis puesto; al aprobar, el panel deja marcar otra como principal.
