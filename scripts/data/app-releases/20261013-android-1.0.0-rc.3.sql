-- Mobile app 1.0.0-rc.3 for Android, build 4 (frontend tag 1.0.0-rc.3, 2026-10-13). Not mandatory: no build is
-- turned away. First registered by the old migration V3; kept here to restore it after the 2026-10-13 rebuild.

INSERT INTO apps.app_releases (id, platform, build, version_name, mandatory, notes, released_at)
SELECT gen_random_uuid(), 'ANDROID', 4, '1.0.0-rc.3', FALSE,
       'Elegir de contactos al crear remitentes y beneficiarios, la caja de cada repartidor para ventas y botón Actualizar.',
       TIMESTAMP WITH TIME ZONE '2026-10-13 12:00:00-04'
WHERE NOT EXISTS (SELECT 1 FROM apps.app_releases WHERE platform = 'ANDROID' AND build = 4);
