-- Mobile app 1.0.0-rc.3 for Android, build 4 (frontend tag 1.0.0-rc.3, 2026-10-13). Not mandatory: no build is
-- turned away. iOS build 4 is registered once it is on TestFlight.

INSERT INTO app_releases (id, platform, build, version_name, mandatory, notes, released_at)
VALUES ('00000000-0000-0000-0005-000000000001', 'ANDROID', 4, '1.0.0-rc.3', FALSE,
        'Elegir de contactos al crear remitentes y beneficiarios, la caja de cada repartidor para ventas y botón Actualizar.',
        TIMESTAMP WITH TIME ZONE '2026-10-13 12:00:00-04');
