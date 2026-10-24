-- La ficha de un borrador de tecnología se puede editar antes de aprobar (23/10/2026).
--
-- La edición va aparte de `descripcion`, que es la de la skill y la reescribe cada lista: así una
-- lista nueva no pisa lo que una persona corrigió a mano. `ficha_nueva_de_la_skill` avisa que,
-- después de la edición, una lista trajo otra ficha.
ALTER TABLE borrador_tecnologia
    ADD COLUMN descripcion_editada TEXT,
    ADD COLUMN ficha_nueva_de_la_skill BOOLEAN NOT NULL DEFAULT FALSE;
