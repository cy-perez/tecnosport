-- La explicación de verdicto_plazo, al día y donde se puede leer.
--
-- V22 dejó escrito, en un comentario del propio archivo, que el veredicto se congela porque "el
-- calendario de festivos puede cargarse después". Eso dejó de ser cierto con el ADR-0024: los
-- festivos se calculan y no se cargan. El comentario de V22 no se corrige —editar una migración ya
-- aplicada le cambia el checksum y Flyway rechaza la base entera, incluida la de dev— y además un
-- comentario de archivo no llega a la base. Así que la explicación va a la columna, con el mismo
-- patrón que V27 usó para medio_preferido.
comment on column solicitud_retracto.verdicto_plazo is
    'Veredicto del plazo de retracto el dia en que se radico, congelado a proposito: un calendario '
    'legal cambia -la Ley 2578 de 2026 declaro un festivo con el ano empezado, y esta demandada- y '
    'recalcularlo moveria hacia atras el dato con el que una persona ya decidio. INDETERMINADO solo '
    'aparece en filas radicadas antes del ADR-0024, cuando los festivos eran un dato pendiente; hoy '
    'se calculan y el veredicto es siempre EN_PLAZO o VENCIDO.';
