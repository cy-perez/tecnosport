-- Un modelo nuevo de tecnologia necesita 4 fotos para aprobarse (ADR-0079), salvo que el
-- fabricante no publique mas tomas oficiales: la persona lo declara en la skill de fotos, con su
-- motivo, y la importacion de borradores lo trae (28/10/2026, los AirPods Pro 3 con dos).
--
-- Las dos columnas van juntas: un minimo sin motivo no se puede defender, y el dominio exige los
-- dos. Nulas, el borrador pide las 4 de siempre.
ALTER TABLE borrador_tecnologia
    ADD COLUMN minimo_de_fotos INTEGER,
    ADD COLUMN motivo_minimo_de_fotos TEXT,
    ADD CONSTRAINT ck_borrador_tecnologia_minimo_de_fotos
        CHECK ((minimo_de_fotos IS NULL AND motivo_minimo_de_fotos IS NULL)
            OR (minimo_de_fotos BETWEEN 1 AND 3 AND btrim(motivo_minimo_de_fotos) <> ''));
