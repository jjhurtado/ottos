-- Phase 1 · Zones: official provinces and municipalities of Cuba (codes from the political-administrative division).
-- Only La Habana for now; add other provinces with new migrations.

CREATE TABLE provinces (
    code   VARCHAR(2)   PRIMARY KEY,
    name   VARCHAR(100) NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE municipalities (
    code          VARCHAR(4)   PRIMARY KEY,
    province_code VARCHAR(2)   NOT NULL,
    name          VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_municipalities_province FOREIGN KEY (province_code) REFERENCES provinces (code)
);

INSERT INTO provinces (code, name) VALUES ('23', 'La Habana');

INSERT INTO municipalities (code, province_code, name) VALUES
    ('2301', '23', 'Playa'),
    ('2302', '23', 'Plaza de la Revolución'),
    ('2303', '23', 'Centro Habana'),
    ('2304', '23', 'La Habana Vieja'),
    ('2305', '23', 'Regla'),
    ('2306', '23', 'La Habana del Este'),
    ('2307', '23', 'Guanabacoa'),
    ('2308', '23', 'San Miguel del Padrón'),
    ('2309', '23', 'Diez de Octubre'),
    ('2310', '23', 'Cerro'),
    ('2311', '23', 'Marianao'),
    ('2312', '23', 'La Lisa'),
    ('2313', '23', 'Boyeros'),
    ('2314', '23', 'Arroyo Naranjo'),
    ('2315', '23', 'Cotorro');
