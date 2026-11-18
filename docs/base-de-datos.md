# Base de datos y migraciones (Flyway)

Esquema, migraciones versionadas y adopción de bases existentes. Guía de inicio en el [README](../README.md).

El esquema de PostgreSQL lo administra **Flyway**; Hibernate queda en
`spring.jpa.hibernate.ddl-auto: validate`, así que comprueba que las entidades coinciden con las
tablas pero **nunca** crea ni modifica el esquema.

- Las migraciones versionadas viven en `src/main/resources/db/migration`
  (`V1__initial_schema.sql`, `V2__...`, ...) y se aplican al arrancar la aplicación.
- `V1__initial_schema.sql` reproduce el esquema que creó Hibernate con `ddl-auto: update`: tablas
  `users` y `tasks`, identidad en las claves primarias, `UNIQUE (email)`, la clave foránea
  `tasks.user_id -> users.id` y la CHECK de `tasks.status`. Se conservan los nombres físicos de
  las constraints para que una base nueva y una existente queden idénticas.
- Cada migración aplicada queda registrada en `flyway_schema_history` (versión, checksum, fecha y
  resultado). Si un checksum cambia, el arranque falla en lugar de aplicar algo distinto.

## Base de datos nueva

Con la base vacía basta arrancar:

```powershell
.\gradlew.bat bootRun
```

Flyway aplica `V1` (y las siguientes en orden) y después Hibernate valida el esquema.

## Base de datos existente (adopción con baseline)

TaskFlow ya tenía datos antes de Flyway. **No se ejecuta `V1` sobre una base existente** y no se
borra nada: se marca la versión 1 como línea base y Flyway administra a partir de la siguiente
migración.

1. Haz una copia de seguridad (`pg_dump`).
2. Comprueba que el esquema existente coincide con `V1` (tablas, columnas, tipos, nulabilidad,
   PK, UNIQUE, CHECK y FK):

   ```sql
   SELECT table_name, column_name, data_type, is_nullable, column_default
   FROM information_schema.columns
   WHERE table_schema = 'public' AND table_name IN ('users', 'tasks')
   ORDER BY table_name, ordinal_position;

   SELECT c.relname AS tabla, co.conname, co.contype, pg_get_constraintdef(co.oid)
   FROM pg_constraint co
   JOIN pg_class c ON c.oid = co.conrelid
   WHERE c.relname IN ('users', 'tasks');
   ```

3. Arranca **una sola vez** con el baseline activado:

   ```powershell
   $env:FLYWAY_BASELINE_ON_MIGRATE = "true"
   .\gradlew.bat bootRun
   ```

   Se crea `flyway_schema_history` con la fila `1 | BASELINE` y **no se ejecuta ningún DDL**.

4. Elimina la variable y arranca de nuevo: Flyway valida y aplica las migraciones posteriores.

`baseline-on-migrate` está **desactivado por defecto** (`FLYWAY_BASELINE_ON_MIGRATE=false`) y no
debe quedar activo de forma permanente: es una decisión puntual y manual.

Garantías comprobadas por los tests de integración:

- Flyway solo hace baseline de esquemas **no vacíos**: una base vacía aplica `V1` normalmente y
  nunca queda marcada como "ya inicializada".
- Sin baseline, una base no vacía sin historial aborta el arranque **sin tocar nada**.
- Si el esquema no coincide con las entidades, Hibernate `validate` detiene el arranque y no
  repara el esquema por su cuenta.
- Limitación conocida: un esquema *parcialmente* creado (por ejemplo solo `users`) también se
  marca como línea base. Flyway no puede saber si el esquema está completo: la fila `1 | BASELINE`
  se escribe **antes** de que Hibernate valide y su `checksum` es `NULL`, de modo que no hay forma
  automática de detectar el desfase. Por eso el paso 2 es obligatorio.

#### Baseline aplicado sobre un esquema incompleto (recuperación)

Si el paso 3 se ejecutó sobre un esquema incompleto, el arranque falla en la validación de
Hibernate (`Schema-validation: missing table [tasks]`) **pero la fila de baseline ya está
registrada**:

```sql
SELECT installed_rank, version, description, type, success, checksum
FROM flyway_schema_history;
-- 1 | 1 | esquema previo a Flyway | BASELINE | t | (NULL)
```

Consecuencias:

- La fila **permanece** aunque Hibernate detenga el arranque: Flyway confirma el historial antes de
  que se inicialice JPA, así que no se revierte con el fallo.
- **Desactivar `baseline-on-migrate` después no lo soluciona**: la versión 1 ya figura en el
  historial, por lo que Flyway informa `Schema "public" is up to date. No migration necessary.` y
  **nunca** ejecuta `V1`; el error de validación se repite en cada arranque.
- El `checksum` de `BASELINE` es `NULL`, así que `validate` tampoco detecta que el esquema no
  coincide con `V1`.
- Tampoco sirve borrar solo `flyway_schema_history`: sobre un esquema no vacío Flyway aborta con
  `Found non-empty schema(s) "public" but no schema history table` y no modifica nada (en PostgreSQL
  el DDL de Flyway es transaccional, ni siquiera recrea el historial).

Recuperación segura (elegir **una** opción y no editar checksums ni la tabla de historial):

| Situación | Procedimiento |
| --- | --- |
| Solo existe la fila `BASELINE` (ninguna fila con `type` = `SQL`) y el esquema se puede restaurar desde `pg_dump` o recrear vacío | Restaurar la copia con `pg_restore` (o recrear la base vacía), ejecutar `DROP TABLE flyway_schema_history;` y arrancar **sin** baseline: `.\gradlew.bat bootRun` aplica `V1` completo. |
| Solo existe la fila `BASELINE` (ninguna fila con `type` = `SQL`) y no hay copia de seguridad, pero los datos existentes deben conservarse | Completar **a mano** los objetos que falten exactamente como `V1` (tablas, columnas, tipos, nulabilidad, PK, UNIQUE, CHECK y FK con los mismos nombres físicos) y **conservar** la fila `BASELINE`. El esquema queda equivalente a `V1` y las migraciones posteriores se aplican con normalidad. |
| Ya se aplicaron migraciones posteriores (`V2` en adelante) | **Restaurar la copia de seguridad** y repetir el baseline con el esquema ya verificado. No borrar el historial: se perdería el registro de migraciones aplicadas sobre datos reales. |

`DROP TABLE flyway_schema_history` **solo es seguro** en el primer caso: historial con únicamente la
fila `BASELINE` y un esquema que se va a restaurar o recrear por completo antes de volver a arrancar.
Si existen filas `SQL`, borrar la tabla obliga a Flyway a empezar de cero, `V1` falla con
`relation "users" already exists` y el arranque queda bloqueado.

> Comprobado empíricamente: baselinar un esquema con solo `users` deja `1 | BASELINE` registrada,
> Hibernate aborta con `missing table [tasks]`, y el arranque siguiente (con baseline desactivado)
> responde `Schema "public" is up to date. No migration necessary.` seguido del mismo error. Tras
> completar la tabla `tasks` con los objetos de `V1`, la aplicación arranca y valida el esquema.

## Compatibilidad con PostgreSQL 18

Flyway 11.7.2 (la versión que fija Spring Boot) declara soporte oficial hasta PostgreSQL 17 y en
cada arranque registra:

```
WARN ... PostgreSQL 18.1 is newer than this version of Flyway and support has not been tested.
The latest supported version of PostgreSQL is 17.
```

Es una advertencia, no un fallo: las migraciones y los tests de integración pasan contra PostgreSQL
18.1 (baseline, `V1`, validación de Hibernate y los escenarios de integración). Aun así, **una
prueba empírica satisfactoria no equivale a soporte oficial**: ante cualquier comportamiento
anómalo de las migraciones la referencia es la matriz de compatibilidad de Flyway, no esta
comprobación. Actualizar Flyway queda fuera de esta entrega.

## Ver el estado de las migraciones

```sql
SELECT installed_rank, version, description, type, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `FLYWAY_ENABLED` | `true` | Desactiva Flyway (solo lo usa el test de contexto con H2) |
| `FLYWAY_BASELINE_ON_MIGRATE` | `false` | Adopción manual y puntual de una base existente |
