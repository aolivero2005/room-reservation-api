# Room Reservation API

API REST para reservar salas de reuniones: crear y listar salas; crear, listar (con filtros y paginación) y cancelar reservas. Rechaza reservas solapadas (409), valida la entrada con errores 400 claros y protege las escrituras con autenticación.

**Stack:** Java 21 · Spring Boot 4.2 · Spring Data JPA · Flyway · Spring Security · springdoc-openapi · H2 / PostgreSQL · JUnit 5 + Mockito + MockMvc.

---

## Cómo ejecutarlo

Requisitos: JDK 21. Docker solo si se quiere usar PostgreSQL.

```bash
./gradlew bootRun     # Windows: gradlew.bat bootRun
./gradlew test
```

La API queda en `http://localhost:8080`, con H2 en memoria (no hay que instalar nada). Flyway crea el esquema al arrancar.

- **Credenciales para crear/cancelar (HTTP Basic):** `admin` / `admin123` (configurables con `APP_USER` y `APP_PASSWORD`).
- **Swagger UI:** `http://localhost:8080/swagger-ui.html` → botón **Authorize** para los endpoints protegidos.
- **Consola H2:** `http://localhost:8080/h2-console` → JDBC URL `jdbc:h2:mem:roomdb`, usuario `sa`, sin contraseña.

**Con PostgreSQL (opcional):**
```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=postgres'
```
El contenedor usa el puerto **5433** para no chocar con un PostgreSQL local.

---

## Endpoints

| Método | Ruta | Auth | Respuestas |
|---|---|---|---|
| `POST` | `/api/rooms` | Sí | 201 · 400 · 401 · 409 (nombre duplicado) |
| `GET` | `/api/rooms` | No | 200 |
| `GET` | `/api/rooms/{id}` | No | 200 · 404 |
| `POST` | `/api/reservations` | Sí | 201 · 400 · 401 · 404 (sala inexistente) · **409 (sala ocupada)** |
| `GET` | `/api/reservations?roomId=&date=&zone=&status=&page=&size=&sort=` | No | 200 · 400 |
| `GET` | `/api/reservations/{id}` | No | 200 · 404 |
| `DELETE` | `/api/reservations/{id}` | Sí | 204 · 401 · 404 · 409 (ya cancelada) |

```bash
# Crear una sala
curl -u admin:admin123 -X POST localhost:8080/api/rooms \
  -H 'Content-Type: application/json' -d '{"name":"Sala Andes","capacity":8}'

# Reservar (las fechas llevan zona horaria: Z o un offset como -03:00)
curl -u admin:admin123 -X POST localhost:8080/api/reservations \
  -H 'Content-Type: application/json' \
  -d '{"roomId":1,"responsible":"Ana","start":"2030-01-15T10:00:00-03:00","end":"2030-01-15T11:00:00-03:00"}'

# Reservas de la sala 1 el 15/01/2030 (en hora de Buenos Aires)
curl 'localhost:8080/api/reservations?roomId=1&date=2030-01-15&zone=America/Argentina/Buenos_Aires'

# Cancelar
curl -u admin:admin123 -X DELETE localhost:8080/api/reservations/1
```

Los errores siguen el formato estándar **RFC 9457 (Problem Details)**. Los de validación indican cada campo:
```json
{
  "title": "Validation failed",
  "status": 400,
  "detail": "The request contains invalid fields",
  "errors": [
    { "field": "responsible", "message": "responsible is required" },
    { "field": "end", "message": "end must be after start" }
  ]
}
```

---

## Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| **Base de datos** | H2 por defecto; PostgreSQL con el perfil `postgres`. Mismas migraciones de Flyway para ambas. | Se arranca con un solo comando, sin instalar nada. |
| **Solapamiento** | Intervalos `[start, end)`. Solo cuentan las reservas activas. | Una reunión de 10 a 11 y otra de 11 a 12 no chocan. |
| **Concurrencia** | Al reservar se bloquea la fila de la sala (`SELECT … FOR UPDATE`) antes de comprobar solapamientos. En PostgreSQL, además, una restricción en la base impide solapamientos. | Sin bloqueo, dos peticiones simultáneas podrían reservar el mismo hueco. |
| **Fechas** | La entrada exige zona horaria; la salida es siempre UTC. El filtro `date` devuelve las reservas que se solapan con ese día, en UTC o en la zona indicada en `zone`. | Una fecha sin zona es ambigua. Una reserva de 23:00 a 01:00 aparece en ambos días. |
| **Cancelación** | Borrado lógico (estado `CANCELLED`), `DELETE` → 204. Cancelar dos veces → 409. | Se conserva el historial y el hueco queda libre. La alternativa sería hacerlo idempotente (204). |
| **Autenticación** | HTTP Basic sin sesión, con un usuario en memoria. Los `GET` son públicos. | Es lo más simple que cumple el requisito. Sin sesión ni cookies, CSRF no aplica y está desactivado. |
| **Errores** | Todos en Problem Details, incluido el 401. Solo las restricciones de base conocidas (nombre duplicado, solapamiento) se convierten en 409; el resto es un 500. | Un 409 genérico escondería errores reales detrás de un mensaje falso. |

Además: paginación con tamaño máximo de 100, el listado trae cada sala en la misma consulta (sin N+1) y la documentación OpenAPI se genera desde el código.

---

## Tests

85 tests: unitarios de los servicios, de la capa web (controladores, seguridad y formato de errores), de repositorio sobre el esquema real de Flyway, y de integración end-to-end con MockMvc sin nada simulado.

El test de integración incluye 8 hilos que reservan el mismo hueco a la vez: exactamente uno lo consigue. Se comprobó que el test sirve quitando temporalmente el bloqueo de la sala: en ese caso los 8 reservaban.

La suite corre contra H2. PostgreSQL se verificó manualmente: migraciones, flujo completo con `curl`, peticiones simultáneas y la restricción contra solapamientos.

<!-- TODO (Alexei): las dos comprobaciones anteriores las hizo la IA. Si las reproduces tú, puedes pasarlas a primera persona ("comprobé…"). -->

---

## Qué haría con más tiempo

- **Testcontainers**, para correr la suite también contra PostgreSQL en CI en lugar de verificarlo a mano.
- **Reloj inyectable (`Clock`)**: la validación de "fecha futura" usa el reloj del sistema y varios tests usan fechas fijas de 2030, así que **fallarán a partir de esa fecha**.
- **JWT con roles**: que solo el responsable o un administrador pueda cancelar, y tomar el responsable del usuario autenticado.
- **Más reglas de negocio**: no permitir cancelar reservas ya terminadas, poder modificar una reserva.
- **Consultas tipadas**: los filtros referencian los campos como texto, así que un cambio de nombre solo falla al ejecutar. Usaría el metamodelo estático de JPA y, si las consultas se complicaran, evaluaría QueryDSL (fork de OpenFeign).
- **CI con GitHub Actions** y una imagen Docker de la aplicación.
- **Detalles pendientes de la revisión**: activar las reglas de la consola H2 solo cuando está habilitada, y quitar configuración duplicada en los tests.

---

## Cómo usé la IA

Usé **Claude Code** durante todo el desarrollo.

**Qué le pedí**
1. Una propuesta de diseño y un plan de implementación por pasos a partir del enunciado.
2. Implementar el plan paso a paso. Los commits los hice yo después de cada paso.
3. Añadir OpenAPI / Swagger UI como extra.
4. Una revisión crítica de su propio código, para decidir qué corregir.

**Qué acepté**
- El plan y las decisiones técnicas (H2 por defecto, HTTP Basic, bloqueo pesimista, borrado lógico, Problem Details).
- La mayor parte del código, con los tests en verde en cada paso.

**Qué se corrigió en el camino** (detectado por los tests)
- Los tests se contaminaban entre sí porque compartían la misma base H2 en memoria; el test de integración pasó a usar la suya.
- Con una fecha mal escrita, el error no indicaba qué campo fallaba.
- La documentación OpenAPI generada por defecto mostraba los errores con un formato incorrecto.

**Revisión crítica**

De la lista que me dio la IA, más un punto que vi yo (las credenciales escritas a mano en los tests), decidí corregir:
- **Credenciales en los tests**: ahora los tests usan las suyas y las leen de la configuración.
- **Errores de base disfrazados de 409**: cualquier error de integridad se respondía como "nombre duplicado" o "sala ocupada". Ahora solo esos dos casos dan 409. La primera versión de esta corrección fallaba con PostgreSQL y apareció al probarla contra la base real.
- **Formato del 401**: no coincidía con el resto de errores.
- **Manejador de errores genérico**: habría convertido un "acceso denegado" en un 500.

Los demás puntos están en "Qué haría con más tiempo".

<!-- TODO (Alexei): añade aquí con tus palabras lo que revisaste o decidiste tú, y por qué elegiste esas correcciones. Si no revisaste el código de cada paso, ajusta "Qué acepté". -->

---

## Tiempo invertido

<!-- TODO (Alexei): indicar el tiempo real. -->
