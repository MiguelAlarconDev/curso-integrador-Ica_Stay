# IA-07.1 — Auditoría y endurecimiento de IcaStay AI

Fecha: 2026-10-22. Rama: `feature/ai-assistant`.

## Alcance y criterios

Revisión del código y pruebas de sesiones, seguridad HTTP, historial, catálogo,
cliente Ollama y chatbot Angular. Cambios limitados a defectos comprobables;
sin dependencias, cambios de contratos ni reglas de negocio. No es una prueba de
penetración ni una certificación de seguridad. Las bases de integración son
aisladas; no se leen conversaciones reales ni se insertan fixtures en ellas.

Fuentes: README, arquitectura y decisiones, `ai-assistant.md`,
`ai-public-catalog.md`, `ai-catalog-cards.md`, implementación y pruebas.
La documentación conceptual todavía marca MongoDB/Ollama como futuros y la
introducción de `ai-assistant.md` describe IA-03.1; sus incrementos posteriores y
las specs IA-06 describen el comportamiento actual. No se cambió arquitectura
para resolver esa diferencia histórica.

## Hallazgos corregidos

| ID / severidad | Evidencia | Corrección y prueba |
| --- | --- | --- |
| AI-071-01 / media | `OllamaClient.exchange` usaba `BodyHandlers.ofString()` sin límite de bytes. `num_predict` es una instrucción al proveedor, no un límite HTTP. Un cuerpo grande, incluso con respuesta pequeña y metadata excesiva, se acumulaba completo antes de analizar JSON. Tampoco había límite de caracteres en la respuesta entregada. | Suscriptor limitado a 128 KiB de bytes recibidos, cancelación al excederlos y error 502 genérico. Contenido limitado a 16 000 unidades UTF-16, coherente con persistencia. `OllamaHttpContractTest` envía 140 000 bytes de metadata por HTTP con longitud fija y chunked: ambos se rechazan y una petición posterior funciona. `OllamaClientTest` comprueba 16 001 rechazados, 16 000 aceptados y liberación del cupo. |
| AI-071-02 / baja (accesibilidad) | `AiChat.loadHistory.next` forzaba foco al textarea cuando llegaba el historial, incluso si el visitante había pasado al botón de cierre. | El foco se establece al iniciar la acción de recuperación, no al completar la operación asíncrona. La apertura mantiene su foco inicial y el reintento mueve el foco antes de retirar su botón. Prueba DOM con historial pendiente y foco en cerrar: la respuesta ya no lo desplaza. |

No se clasifican como vulnerabilidades controles que ya estaban implementados.
Las pruebas nuevas ejercitan los casos que el código previo no acotaba o no
preservaba. No se cambian cookies, permisos, JWT, consultas ni almacenamiento.

## Controles revisados y evidencia existente

| Área | Evidencia y conclusión acotada |
| --- | --- |
| Sesión | `AiSessionService`: 32 bytes SecureRandom, firma HMAC, comparación constante, formato canónico, UUID, caducidad y rechazo de cookies duplicadas. HttpOnly, SameSite=Lax, Path específico, sin Domain. Secure obligatorio fuera de origen HTTP local; clave obligatoria fuera de local. `AiSessionServiceTest` cubre manipulación, expiración y configuración. No se modificaron valores de despliegue. |
| CSRF/CORS/JWT | `AiCookieCsrfFilter` verifica Origin exacto y Fetch Metadata; POST de navegador con cookie sin Origin se rechaza. JSON obligatorio. CORS con credenciales solo en rutas IA exactas; permisos por método/ruta, no wildcard IA. `AiAnonymousConversationTest`, `AiChatEndpointTest` y pruebas JWT comprueban orígenes, cookies y autorización. Angular excluye JWT solo para las dos rutas IA, usa credenciales allí y no lee el token HttpOnly. |
| Aislamiento/retención | `AiHistoryRepository` filtra propietario derivado, conversación, esquema y expiresAt mayor que ahora; orden estable, paginación limitada. Renovación de 30 días solo del propietario/conversación activos; TTL es limpieza adicional. Pruebas de repositorio, servicio y Mongo aislado. Ningún identificador de conversación sustituye la cookie como credencial. |
| Privacidad | No se devuelve el token, propietario interno ni entidades privadas. `AiHistoryPrivacy` filtra patrones sensibles antes de guardar/reutilizar contexto; logs de fallo de historial son genéricos. Persistencia solo tras intercambio completado y flush; pruebas de desconexión e interrupción. |
| Herramientas e inyección | `AiCatalogReader` selecciona DTO públicos mediante servicios existentes, ACTIVE, transacción readOnly y parámetros enlazados. Máximo 3 hoteles × 3 habitaciones; no hay ejecución SQL o permisos decididos por Ollama. Contexto de hasta cinco intercambios, presupuesto de 12 000 caracteres y roles asignados por servidor, con sistema siempre presente. Las respuestas verificadas se construyen directamente, sin pasar por el modelo. |
| Precios/disponibilidad | Precios decimales publicados y moneda, sin totales inventados. Sin fechas: UNKNOWN y aviso. Con fechas/huéspedes: reglas existentes de solapamiento y salida exclusiva. Catálogo y pruebas PostgreSQL distinguen hotel inexistente de habitaciones no disponibles. Historial Angular aparece marcado como no vuelto a comprobar; no reconstruye tarjetas interpretando texto. |
| Límites/fallos | Entrada de 1000 caracteres, semáforo Ollama sin cola, deadline completo y cancelación. Catálogo con dos trabajadores sin cola, deadline y límites de sentencia/transacción. Mongo tiene tiempos/pool acotados; fallo de lectura produce historial no disponible y chat básico puede continuar; fallo de escritura no invalida respuesta ya enviada. Integración de arranque comprueba núcleo con Mongo caído. Errores de catálogo no se reemplazan por datos inventados. |
| Interfaz | Interpolación Angular sin HTML del modelo; enlaces internos UUID, validación de metadata, una región aria-live, etiquetas y foco visible. Cancelación al destruir, bloqueo de duplicados, reintento sin repetir pregunta. CSS usa ancho de viewport, altura dinámica, scroll y safe areas. Pruebas DOM; no se afirma validación visual con lector de pantalla o dispositivos físicos. |

## Riesgos residuales y límites de la auditoría

- Una cookie robada es una credencial hasta su caducidad; no existe revocación
  individual persistente. Rotar la clave invalida todas las sesiones. HTTPS y
  gestión de claves siguen siendo responsabilidades del despliegue.
- El prompt reduce instrucciones adversarias, pero no prueba que qwen2.5:1.5b
  nunca alucine o salga de tema. El modelo no tiene herramientas ni datos privados;
  solo el catálogo determinista constituye información verificada. No se hizo
  una evaluación estadística del modelo real en esta auditoría.
- El filtro de privacidad por patrones puede producir falsos positivos y omitir
  información sensible no reconocible. No es un sistema universal de DLP. Evitar
  introducir datos personales sigue siendo necesario.
- Los cupos locales no son rate limiting distribuido ni cuotas por visitante.
  Tampoco hay un límite específico del tamaño total del JSON entrante antes de
  deserializarlo; la validación de message ocurre después. Un despliegue público
  requiere límites de cuerpo, conexiones y frecuencia en su entrada HTTP.
  No se añadió infraestructura de gateway ni se cambió el servidor compartido.
- PostgreSQL, Mongo y Ollama siguen compartiendo recursos del equipo. Los límites
  existentes reducen el impacto, no garantizan aislamiento frente a agotamiento
  global. El nuevo límite mide bytes entregados al suscriptor, no memoria total
  del cliente HTTP/Jackson.
- Flush no confirma recepción por el navegador: solo se evita persistencia en
  cancelaciones detectables. TTL puede eliminar con retraso; las lecturas aplican
  expiresAt. Disponibilidad puede cambiar después de la consulta.
- Pendiente revisión visual móvil/escritorio y con tecnologías de asistencia;
  las pruebas DOM no sustituyen esas verificaciones.

## Validación

- Backend: **204 pruebas aprobadas, cero fallos, errores u omitidas**. La primera
  tanda sin infraestructura omitió 12 integraciones; la ejecución final las
  incluyó con PostgreSQL en 55439 y MongoDB en 27028, bases exclusivas de prueba.
- Frontend: **102 pruebas aprobadas en siete archivos**, incluyendo la nueva
  regresión de foco, chatbot, metadata, autenticación y rutas públicas.
- Compilación backend (incluida en Maven test) y `npm.cmd run build`: correctas.
  Bundle inicial Angular: 370.41 kB, dentro del presupuesto.
- `git diff --check`: correcto. Sin commits, push, nuevas dependencias ni cambios
  en entidades, migraciones, seguridad HTTP o reglas de reservas/pagos.

Selección Maven:

```text
Ai*Test,Ollama*Test,RoomServiceTest,AvailabilityIntegrationTest,HotelSearchIntegrationTest,JwtAuthenticationFilterTest,JwtTokenServiceTest,AuthServiceTest,ReservationServiceTest,UserReservationIntegrationTest,PaymentIntegrationTest,AuthHttpIntegrationTest,SecurityAuthorizationIntegrationTest
```

Ejecutada con `ICASTAY_TEST_POSTGRES=isolated`, `ICASTAY_TEST_MONGODB_URI`
apuntando a `127.0.0.1:27028/icastay_ai_test_history`, datasource de prueba
`127.0.0.1:55439/icastay_ai_infra_test` y Mongo de regresión
`127.0.0.1:27028/icastay_ai_test_regression`. No usar estos tests con bases reales.

Selección Angular:

```text
npm.cmd test -- --watch=false --include=src/app/features/ai/**/*.spec.ts --include=src/app/core/services/ai-chat.service.spec.ts --include=src/app/core/models/ai-catalog-validation.spec.ts --include=src/app/core/auth/auth.spec.ts --include=src/app/app.spec.ts
npm.cmd run build
```

Archivos modificados: `OllamaClient.java`, `OllamaClientTest.java`,
`OllamaHttpContractTest.java`, `ai-chat.ts`, `ai-chat.spec.ts`. Este informe es el
único archivo nuevo. Las pruebas HTTP de Ollama usan un servidor controlado, no
afirman haber validado respuestas del modelo local real.
