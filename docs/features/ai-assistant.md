# IcaStay AI — Chat público e infraestructura de historial

**Estado:** en revisión. **Actualizado:** 2026-10-21.

## Objetivo y alcance

Visitantes, sin login, pueden consultar orientación hotelera en español mediante
Ollama local. IcaStay es una plataforma de búsqueda y reservas hoteleras en Ica.
Este incremento no consulta PostgreSQL, no guarda conversaciones, no incorpora
MongoDB ni frontend y no ejecuta acciones de negocio. Cada petición es independiente.

La arquitectura conceptual de IA describe un futuro asistente autenticado con
datos por rol. IA-03.1 establece un alcance público distinto, por instrucción del
proyecto: incluso con JWT, no se incorporan identidad, reservas ni datos privados.

## Contrato

`POST /api/v1/ai/chat`, Content-Type `application/json`.

Entrada: `{"message":"¿Cómo puedo buscar alojamiento en Ica?"}`.
Salida 200: `{"answer":"..."}`.

`message` debe ser texto no nulo, no blanco y de hasta 1000 caracteres Java
(unidades UTF-16), medidos antes de recortar espacios exteriores. No admite
números, arrays u objetos como mensaje. El servidor controla modelo, URL,
instrucciones y opciones; no acepta historial ni roles del cliente.

- 400: mensaje inválido, cuerpo ausente o JSON inválido.
- 503: Ollama inaccesible, generación interrumpida o capacidad local ocupada.
- 504: timeout de conexión o respuesta.
- 502: HTTP no exitoso de Ollama, JSON inválido o respuesta sin contenido textual.

Los errores usan ResponseStatusException con mensajes públicos genéricos;
no se devuelven cuerpos de error de Ollama, direcciones internas ni excepciones.
El formato del cuerpo de error depende del tratamiento estándar de Spring.

## Integración y configuración

HttpClient de Java 21 y Jackson 3 existentes; ninguna dependencia nueva.
POST `/api/chat` con `stream:false`, mensajes system/user y
`options.num_predict`; se extrae `message.content` y se recortan extremos.
El cliente reutilizado no sigue redirecciones. No hay reintentos automáticos.

Propiedades bajo `icastay.ai`:

| Propiedad | Variable de entorno | Defecto |
|---|---|---|
| base-url | AI_BASE_URL | http://localhost:11434 |
| model | AI_MODEL | qwen2.5:1.5b |
| connect-timeout | AI_CONNECT_TIMEOUT | 3s |
| response-timeout | AI_RESPONSE_TIMEOUT | 60s |
| max-tokens | AI_MAX_TOKENS | 256 |
| max-concurrent-requests | AI_MAX_CONCURRENT_REQUESTS | 1 |

URL HTTP(S) absoluta sin credenciales, query, fragmento ni ruta distinta de `/`;
modelo no blanco; tiempos y límites positivos. Configuración inválida impide
crear el cliente. Un semáforo por instancia rechaza inmediatamente exceso de
concurrencia, sin cola de espera; libera capacidad también al fallar.
El timeout de respuesta limita el intercambio completo, incluido el cuerpo;
al vencer o interrumpirse la espera se cancela la petición HTTP pendiente.

El prompt limita al ámbito hotelero de Ica, exige reconocer la falta de datos
verificados y prohíbe inventar hoteles, tarifas o disponibilidad. No hay datos
verificados en este incremento. El prompt no garantiza obediencia del modelo:
puede alucinar o desviarse; no constituye un mecanismo de autorización.

## Seguridad y límites

Solo el POST exacto se añade a permitAll. Se mantienen filtro JWT, CORS,
sesiones stateless y permisos existentes. Sin acceso a bases ni herramientas,
el modelo no puede modificar hoteles, reservas, pagos ni usuarios.
No se registran preguntas, respuestas ni credenciales en el módulo.
El límite es local por proceso, no un rate limit distribuido. La configuración
HTTP debe apuntar a un servicio de confianza. El límite de tokens solicitado no
es un límite de bytes de respuesta frente a un servidor malicioso.

## Criterios de aceptación y pruebas

- Petición anónima válida devuelve únicamente answer.
- Mensajes inválidos se rechazan antes de llamar a Ollama; 1000 se admite.
- Petición a Ollama contiene el modelo configurado, prompt y stream:false.
- Fallos HTTP, JSON, contenido, conexión y timeout tienen errores sanitizados.
- Saturación rechaza sin esperar; éxito, fallo e interrupción liberan capacidad.
- POST exacto público; otras rutas/métodos AI y rutas privadas siguen protegidos.
- CORS permite Angular y rechaza otros orígenes.
- Pruebas aisladas de PostgreSQL, Flyway y Ollama real.

La verificación manual contra Ollama real es independiente de las pruebas
automatizadas; no se afirma conectividad real sin ejecutarla.

## Validación ejecutada

2026-10-21: 50 pruebas aprobadas, sin fallos, errores ni omitidas:

```text
mvn.cmd -Dtest=AiChatEndpointTest,AiChatServiceTest,AiConfigurationTest,OllamaClientTest,OllamaHttpContractTest,JwtAuthenticationFilterTest test
```

Incluye un servidor HTTP local simulado y la cadena SecurityConfig real con
dependencias de autenticación simuladas. No se arrancaron PostgreSQL, Flyway
ni Ollama real. `git diff --check` sin errores. Queda pendiente la comprobación
manual con la instalación local de Ollama y qwen2.5:1.5b.

## IA-05.1 — Infraestructura MongoDB (en revisión)

No conecta el historial al endpoint ni modifica su contrato. No hay cookies,
recuperación pública ni contexto conversacional. El chat sigue sin persistir.

Única dependencia nueva: spring-boot-starter-data-mongodb, versión gestionada
por Boot 4.1.1. spring.mongodb.uri usa MONGODB_URI, defecto
mongodb://127.0.0.1:27017/icastay_ai. PostgreSQL, JPA y Flyway no cambian.
Repositorio concreto con MongoTemplate: sin escaneo de repositorios Mongo,
sin relaciones JPA, sin MongoTransactionManager ni transacción distribuida.

### Documento e identidad

Un intercambio completado por documento en ai_conversations: id UUID interno,
schemaVersion=1, conversationId UUIDv4 opaco, ownerReference SHA-256 con dominio
icastay-ai-owner:v1 y token aleatorio de 256 bits, question (1–1000 unidades
UTF-16, no blanco), answer (1–16000, no blanco), model (1–128, no blanco),
createdAt y expiresAt como fechas BSON UTC. IDs se almacenan como cadenas.
El token tiene 32 bytes aleatorios codificados Base64URL sin padding (43 caracteres).
No se admiten contraseñas, emails, IPs ni JWT como identidad anónima. La futura
capa de sesión debe generarlo con SecureRandom; validar formato no prueba entropía.
SHA-256 es adecuado para un secreto aleatorio de 256 bits, no para contraseñas.
El token no se persiste ni registra. Conocer conversationId no autoriza lectura.
Los tipos de historial no se exponen por HTTP; toString omite contenido privado.

### Retención e índices previstos

30 días de inactividad por pareja propietario/conversación. Actividad en este
servicio significa un nuevo intercambio completado y persistido: se inserta con
expiresAt=now+30 días y se extienden los registros aún vigentes de esa pareja
mediante $max. Leer no renueva. No se reactivan registros ya vencidos. La futura
sesión debe rotar conversationId al expirar; su ciclo de vida queda fuera de etapa.

Sin transacción multidocumento: inserción y renovación pueden fallar parcialmente;
se informa UNAVAILABLE sin reintento automático y no se promete conservación
atómica. $max evita reducir expiración por escrituras concurrentes. Un registro
eliminado por TTL no se recupera. Si la renovación falla, algunos registros pueden
caducar antes que los nuevos; nunca se prolongan por una lectura.

Índices definidos en AiHistoryIndexes, sin aplicarlos automáticamente:

- owner_conversation_created: {ownerReference:1, conversationId:1, createdAt:-1, _id:-1}.
- expires_at_ttl: {expiresAt:1}, expireAfterSeconds:0.

El primer índice soporta consultas por pareja y tiene propietario como prefijo;
no se ofrece búsqueda por conversación sin propietario. Paginación de 1–50 filas,
page 0–1000, orden estable createdAt DESC/id DESC, sin count global. Siempre se
filtra expiresAt > now y schemaVersion=1. TTL es asíncrono; la lectura no depende
de que el monitor haya borrado ya el documento. Nuevas inserciones pueden desplazar
páginas; no es un snapshot. Antes de habilitar uso real: revisar índices/validadores
existentes, desplegar índices explícitamente y decidir validación Mongo compatible.
Crear TTL puede borrar datos vencidos existentes; IA-05.1 no lo aplica en icastay_ai.

### Fallos y aislamiento

Driver con operaciones acotadas y sin reintentos automáticos; sin llamadas a Mongo
en constructores ni inicialización de índices. Ante DataAccessException, guardar
devuelve UNAVAILABLE; leer devuelve available=false sin confundirlo con página
vacía. Logs genéricos, sin preguntas/respuestas/identificadores, URI ni excepciones.
El indicador Mongo (management.health.mongodb.enabled=false en Boot 4.1.1)
se excluye del health global para que una dependencia opcional
caída no marque como caídos los servicios hoteleros. No se elimina el health de
PostgreSQL. Es necesario diseñar monitorización separada al activar el historial.

Pruebas: mapeo y servicio aislados; repositorio con MongoDB exclusivo en otro puerto
y base con prefijo icastay_ai_test_. Nunca se usa icastay_ai para fixtures. No hay
inserciones ni consultas de documentos durante la inspección de metadatos real.

### Inspección y validación ejecutadas — 2026-10-21

Inspección de solo metadatos de icastay_ai.ai_conversations mediante el driver:
listCollections devolvió options={} (sin validador explícito); listIndexes devolvió
únicamente _id_. No se leyeron documentos ni se crearon índices en esa base.

69 pruebas aprobadas, sin fallos ni omitidas. Incluyen mapeo BSON, derivación de
propietario, límites, paginación, aislamiento, renovación, errores, regresión de
chat/JWT y arranque completo. MongoDB temporal en 127.0.0.1:27028 con directorio
propio; cada ejecución creó una base icastay_ai_test_history_<UUID> y la eliminó
al terminar. PostgreSQL 17 temporal en 127.0.0.1:55439/icastay_ai_infra_test:
Flyway aplicó V1/V2 solo allí y JPA validó el esquema. Con MongoDB apuntando a un
puerto sin servicio (27029), el core arrancó, health devolvió UP, catálogo 200,
reservas sin JWT 401 y un fallo al guardar historial no afectó al catálogo.
git diff --check sin errores. Instancias temporales detenidas al finalizar.

Reproducción desde backend, tras disponer de las instancias aisladas indicadas:

```powershell
$env:ICASTAY_TEST_MONGODB_URI='mongodb://127.0.0.1:27028/icastay_ai_test_history?serverSelectionTimeoutMS=2000'
$env:ICASTAY_TEST_POSTGRES='isolated'
mvn.cmd '-Dtest=AiHistoryServiceTest,AiHistoryMappingTest,AiHistoryRepositoryTest,AiHistoryMongoIntegrationTest,AiHistoryStartupIntegrationTest,AiChatEndpointTest,AiConfigurationTest,AiChatServiceTest,OllamaClientTest,OllamaHttpContractTest,JwtAuthenticationFilterTest' test
```

Sin esas variables se omiten las integraciones explícitamente; las pruebas de
Mongo rechazan URI fuera del host/puerto/base de pruebas admitidos. La prueba de
arranque tiene URL PostgreSQL de pruebas fija. No hay fallback a bases reales.

Pendientes antes de activar persistencia pública: creación controlada de índices
en la base real, decisión sobre validador de colección, consentimiento y política
de sesión anónima, integración al endpoint y monitorización separada. Este
incremento prueba la definición TTL, no espera el borrado físico del monitor TTL.
La retención prevista no está activa sobre conversaciones reales mientras no se
despliegue ese índice ni se conecte el servicio al flujo del chat.

Referencias de compatibilidad: [propiedades de Spring Boot 4.1](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)
y [índices TTL de MongoDB](https://www.mongodb.com/docs/manual/core/index-ttl/).
