-- Migración SOLO para tests: comprueba que Flyway aplica las versiones en orden
-- después de V1. Vive en src/test/resources y nunca forma parte del artefacto.
CREATE TABLE flyway_it_order_marker (
    id   bigint PRIMARY KEY,
    note varchar(50) NOT NULL
);
