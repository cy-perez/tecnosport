# ADR 0024. Los festivos se calculan, no se cargan

Fecha: 2026-09-10. Estado: aceptada.

## Contexto

Tres plazos legales del sistema se cuentan en días hábiles: los cinco del
derecho de retracto, los de respuesta a una peticion, queja o reclamo, y los
cinco de la solicitud de reversión del pago. Los tres pasan por
`CalendarioHabil`, que sabía de fines de semana y no de festivos.

Eso no hacía mentir al sistema —pasado el límite responde `INDETERMINADO` en vez
de dar un plazo por vencido, porque un festivo solo empuja el límite hacia
adelante— pero dejaba a las tres figuras sin veredicto definitivo, y el panel
mostrando "no se sabe" a quien tiene que decidir.

El dato estaba marcado como `TODO: FESTIVOS_COLOMBIA` desde la Fase 6, junto a
los datos de negocio pendientes. **Ahí estaba el error de clasificación**: los
festivos no los decide una tienda. Están en la ley, y la ley es determinista.
Mientras estuvo en esa lista, el marcador no esperaba a nadie: nadie iba a
"decidir" un festivo.

## Decisión

`FestivosColombia.delAnio(int)` los **calcula** para cualquier año, y
`CalendarioHabil.calculado()` es el `bean` de producción.

Las reglas, verificadas en fuente oficial el 10 de septiembre de 2026:

- **Ley 51 de 1983** (la "Ley Emiliani"), art. 1. Trece fechas fijas más el
  Jueves y el Viernes Santos, la Ascensión, el Corpus Christi y el Sagrado
  Corazón. De esas, se trasladan al lunes siguiente cuando no caen en lunes: el
  6 de enero, el 19 de marzo, el 29 de junio, el 15 de agosto, el 12 de octubre,
  el 1 y el 11 de noviembre, y los tres que dependen de la Pascua. **El Jueves y
  el Viernes Santos no se trasladan**, y es el detalle que más se equivoca.
- **Ley 2578 de 2026**, art. 6, sancionada el 1 de junio de 2026: el 9 de julio
  es festivo nacional y remite a la Ley 51 para fijar la fecha del descanso, o
  sea que también se traslada. Colombia pasó de dieciocho festivos a diecinueve
  tres meses antes de esta decisión. Contra esa ley hay una demanda de
  constitucionalidad en curso; mientras no haya decisión, rige, y aquí se
  aplica.
- El Domingo de Pascua, con el algoritmo gregoriano de Meeus.

## Alternativas

**Una tabla por año, cargada de configuración o de la base.** Es más fácil de
auditar de un vistazo: se leen dieciocho fechas y se comparan con el calendario
publicado. Se descartó porque habría que alimentarla cada diciembre, y el
diciembre que nadie se acordara, los tres plazos volverían a responder "no se
sabe" **sin que ninguna prueba se quejara**. Un dato que caduca en silencio es
peor que un cálculo.

**Una librería de festivos.** No se evaluó a fondo: la regla completa son
cuarenta líneas, y una dependencia nueva es deuda (`CLAUDE.md`). Además habría
que auditar igual si conoce la Ley 2578, que es de hace tres meses.

## Consecuencias

- Los tres plazos dan veredicto definitivo. `INDETERMINADO` y
  `sinFestivosCargados()` se quedan: son la respuesta honesta si alguna vez hace
  falta un calendario que no se conoce, y las pruebas los usan.
- **Las listas de 2026 y 2027 quedan clavadas en `FestivosColombiaTest`**, y no
  comprobadas recalculando el traslado: eso solo comprobaría que el código
  coincide consigo mismo. Lo que hay que sostener es que coinciden con el
  calendario publicado del país.
- El `bean` sale de `ConfiguracionRetracto` —donde estaba solo porque el
  retracto fue el primero que necesitó contar días hábiles— y pasa a
  `compartido/ConfiguracionCalendario`.
- **Una ley nueva de festivos no la detecta nadie automáticamente.** El día que
  el Congreso añada o quite uno, o que la Corte tumbe la Ley 2578, hay que
  tocar `FestivosColombia` y las dos listas de la prueba. Es el precio de
  calcular, y es explícito: la alternativa era el mismo problema una vez al año
  y sin aviso.
- Un año anterior a 1984 lanza `ExcepcionDeDominio` en vez de devolver un
  calendario que no describe a ningún año real: antes de la Ley 51 no había
  traslado.
