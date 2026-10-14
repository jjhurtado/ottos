-- Mobile app 1.0.0-rc.5 for iOS, build 7 (frontend tag 1.0.0-rc.5; TestFlight label "1.0.0 (7)"), 2026-10-13. Not
-- mandatory: no build is turned away. Builds 5 and 6 are not registered: the app only compares with the latest.
-- The download link stays empty (OTTOS_IOS_DOWNLOAD_URL unset): the app's "Actualizar" opens the TestFlight app.

INSERT INTO apps.app_releases (id, platform, build, version_name, mandatory, notes, released_at)
SELECT gen_random_uuid(), 'IOS', 7, '1.0.0-rc.5', FALSE,
       'Anular remesas, registrar remesas antiguas y el informe mensual con la moneda del negocio.',
       TIMESTAMP WITH TIME ZONE '2026-10-13 15:00:00-04'
WHERE NOT EXISTS (SELECT 1 FROM apps.app_releases WHERE platform = 'IOS' AND build = 7);
