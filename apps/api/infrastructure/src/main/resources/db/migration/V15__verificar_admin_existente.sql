-- Cualquier ADMIN que ya existiera antes de V14 se sembró con SembradorAdmin, nunca con registro —
-- la misma regla que aplica "el ADMIN sembrado nace verificado" (ver SembradorAdmin) se aplica aquí
-- para no dejar sin acceso a un ADMIN ya creado antes de que existiera esta columna.
update usuario set correo_verificado_en = creado_en where rol = 'ADMIN' and correo_verificado_en is null;
