# Cuenta de demostración

TaskFlow **no tiene registro público**: `POST /users` exige `ROLE_ADMIN`
(ver [seguridad.md](seguridad.md)). Para que un visitante pueda probar la aplicación hacen falta
credenciales creadas por un administrador.

Este documento explica el estado actual, el procedimiento más sencillo para habilitar una cuenta
demo segura y las reglas que debe cumplir. **No requiere cambios de código ni mecanismos nuevos de
inicialización.**

## Estado actual

- El demo público está desplegado (frontend en Vercel, API en Render), pero **todavía no hay una
  cuenta demo publicada**. Crearla implica escribir en la base de datos de producción, una acción
  manual del responsable del despliegue que queda fuera del alcance de la documentación.
- El repositorio **no contiene credenciales** de ningún tipo: ni de demo, ni de administración, ni
  secretos de producción.

## Requisitos de una cuenta demo segura

| Requisito | Motivo |
| --- | --- |
| Rol `USER` | No puede listar usuarios (`/users` es *ADMIN-only*) ni crear tareas; solo ver y mover las suyas |
| Datos ficticios | Ningún nombre, correo o tarea puede pertenecer a una persona real |
| Contraseña exclusiva | Distinta de cualquier contraseña real y reutilizable en ningún otro servicio |
| Revocable | Se puede cambiar o desactivar en cualquier momento sin tocar el código |
| Nunca `ADMIN`/`MODERATOR` | Evita publicar una cuenta con privilegios administrativos |

## Opción A — Alta con un ADMIN existente (recomendada)

Es el camino natural: usa la propia API, asigna siempre `ROLE_USER` y no toca la base de datos a
mano.

```powershell
# 1. Login como ADMIN y guarda el token
$API = "http://localhost:8080"
$login = Invoke-RestMethod -Uri "$API/auth/login" -Method Post -ContentType "application/json" `
    -Body '{"email":"ADMIN_EMAIL","password":"ADMIN_PASSWORD"}'
$headers = @{ Authorization = "Bearer $($login.token)" }

# 2. Crear la cuenta demo (el rol USER lo impone el backend)
Invoke-RestMethod -Uri "$API/users" -Method Post -Headers $headers -ContentType "application/json" `
    -Body '{"name":"Demo","email":"demo@example.com","password":"CAMBIA_ESTA_CONTRASENA"}'
```

```bash
# Equivalente en Linux/macOS
API="http://localhost:8080"
TOKEN=$(curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" \
  -d '{"email":"ADMIN_EMAIL","password":"ADMIN_PASSWORD"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

curl -s -X POST "$API/users" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo","email":"demo@example.com","password":"CAMBIA_ESTA_CONTRASENA"}'
```

Si el cliente envía un campo `role` en el JSON **se ignora**: `UserMapper.toEntity` fija `ROLE_USER`.

### Contenido inicial (opcional)

Un `USER` no puede crear tareas (`POST /tasks` requiere `ADMIN` o `MODERATOR`), así que el contenido
de la cuenta demo lo crea el administrador indicando el `userId` del usuario demo:

```powershell
Invoke-RestMethod -Uri "$API/tasks" -Method Post -Headers $headers -ContentType "application/json" `
    -Body '{"title":"Preparar informe mensual","description":"Consolidar cifras y enviar el resumen.","userId":4}'
```

> `description` admite entre 5 y 200 caracteres. Los estados válidos son
> `PENDING`, `IN_PROGRESS`, `DONE` y `CANCELLED`.

## Opción B — Primer usuario de una base de datos vacía (entorno local)

Con una base recién migrada **no existe ningún usuario** y el registro está deshabilitado, así que el
primer administrador se crea directamente en PostgreSQL. Se puede generar el hash BCrypt con la
extensión `pgcrypto` (formato `$2a$`, compatible con el `BCryptPasswordEncoder` de Spring Security):

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (email, name, password_hash, role, active)
VALUES ('admin@example.com', 'Admin', crypt('CAMBIA_ESTA_CONTRASENA', gen_salt('bf', 10)), 'ADMIN', true);
```

A partir de ahí ya se puede iniciar sesión y crear el resto de cuentas (incluida la demo) con
`POST /users`. Alternativa equivalente: generar el hash con `BCryptPasswordEncoder` desde el propio
proyecto y copiarlo en el `INSERT`.

## Rotación y revocación

| Acción | Petición |
| --- | --- |
| Cambiar la contraseña | `PATCH /users/{id}/password` con `{"newPassword":"..."}` (solo `ADMIN`) |
| Desactivar la cuenta | `PATCH /users/{id}/status` con `{"active":false}` |
| Volver a activarla | `PATCH /users/{id}/status` con `{"active":true}` |

Una cuenta inactiva no puede iniciar sesión: el backend responde `401` con el mismo mensaje genérico
que unas credenciales incorrectas, así que no se revela el estado de la cuenta.

El rate limiting del login se aplica también a la cuenta demo: 5 intentos fallidos por pareja
`(IP, email)` y 30 por IP en una ventana de 5 minutos (valores por defecto). Un bloqueo es temporal y
caduca solo.

## Qué no hacer

- No publicar credenciales de una cuenta `ADMIN` ni de un `MODERATOR`.
- No guardar la contraseña demo en `.env`, en `application.yaml` ni en capturas de pantalla.
- No reutilizar la contraseña de la demo en ningún otro servicio ni en la base de datos local.
- No usar la cuenta demo para probar endpoints de administración: eso requiere un `ADMIN` real.

## Pendiente

Provisionar la cuenta demo en el entorno público (Opción A) y publicar sus credenciales en la
sección «Acceso a la demo» de los dos README. Es una acción manual del responsable del despliegue y
no forma parte de los cambios de esta etapa de documentación.
