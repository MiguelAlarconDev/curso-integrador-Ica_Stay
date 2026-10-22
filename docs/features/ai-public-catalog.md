# IA-06.1 — Consultas públicas verificadas

Estado: implementado y validado. Actualizado: 2026-10-22.

El POST chat mantiene {message} → {answer}, cookies, CSRF e historial. Angular no cambia.
El backend reconoce una gramática acotada en español y ejecuta exclusivamente
HotelService.listActive/matchActiveName y RoomService.availableLimited. Ejemplos:

- Hoteles en Ica.
- Muéstrame las habitaciones y precios del hotel Oasis Ica.
- ¿Qué hoteles hay disponibles en Ica?
- Disponibilidad en Ica del 2030-01-02 al 2030-01-04 para 2 huéspedes.

Se exige ciudad o nombre completo de hotel, con comillas opcionales. Se extrae la
mención del hotel antes de buscar la ciudad: `en Hotel Oasis Ica` no es una ciudad.
`en Ica` y `en la ciudad de Ica` son filtros de ciudad. Los nombres se comprueban
contra ACTIVE mediante igualdad sin distinguir mayúsculas, admitiendo únicamente
el prefijo literal `Hotel ` del catálogo. No se usa coincidencia difusa ni se
elige por semejanza: cero coincidencias informa ausencia en el catálogo público;
varias solicitan ciudad/nombre inequívoco antes de consultar habitaciones. La
búsqueda exacta se limita en SQL, no se filtran los primeros tres de una búsqueda
amplia. Las consultas públicas existentes conservan su búsqueda por substring.

`Disponibles` sin fechas/huéspedes permite listar hoteles o habitaciones ACTIVE,
con advertencia explícita de disponibilidad no comprobada; no vuelve a pedir una
ciudad ya indicada. Precios sin fechas son tarifas publicadas por noche. Una
consulta de disponibilidad explícita o con criterios parciales solicita fechas
ISO y 1–20 huéspedes por habitación. Hotel encontrado sin habitaciones activas
y hotel sin habitaciones para fechas/huéspedes tienen mensajes distintos de una
ausencia de coincidencia del hotel. No interpreta fechas relativas, grupos de
habitaciones, ordenación por precio ni referencias implícitas a mensajes anteriores:
debe repetirse la consulta completa. Datos faltantes/ambiguos solicitan precisión.
Las fechas y solapamientos se validan con las reglas existentes de RoomService.

Decisión: los hechos se presentan mediante una plantilla del backend, sin generación
libre del modelo. Esto evita que qwen2.5:1.5b invente/modifique nombres, precios o
disponibilidad. Ollama conserva orientación general, sin recibir entidades ni
resultados de estas herramientas. No se requiere tool calling ni SQL generado.
Los intercambios de catálogo se identifican en historial con model=icastay-public-catalog-v1
y no se reutilizan como hechos actuales en el contexto de Ollama.
El filtro de privacidad sigue excluyendo secretos y datos personales. Se reconocen
fechas ISO válidas antes de detectar secuencias numéricas sensibles para no confundir
las fechas de estancia con documentos/teléfonos; los marcadores de contraseñas,
tokens y reservas privadas continúan bloqueando la persistencia. Las exclusiones
conservadoras pueden impedir guardar algunos intercambios legítimos.

Máximo 3 hoteles y 3 habitaciones por hotel, limitados en SQL mediante Pageable.
Las rutas públicas existentes siguen sin ese límite adicional. Misma consulta de
solapamiento: salida exclusiva, CONFIRMED y PENDING_PAYMENT no vencida bloquean.
Solo ACTIVE, nombres/ciudad/número/capacidad/precio por noche/moneda. No se exponen
descripciones, propietarios, usuarios, reservas ni políticas. La disponibilidad es
una observación, no una garantía de reserva; listado parcial explícito, sin total final.

Dos workers dedicados sin cola; deadline de herramienta 5s, transacción readOnly
3s y statement_timeout 2s/lock_timeout 1s locales a PostgreSQL. Una operación que
no responda a interrupción puede ocupar un worker hasta que el driver termine;
no se crean tareas ilimitadas. No hay transacción JPA durante Ollama/Mongo.
Fallo/saturación de PostgreSQL: 503 genérico, nunca respuesta fabricada ni fallback
a Ollama para los hechos solicitados. Ninguna herramienta de escritura.

La protección contra prompt injection se basa en no ejecutar instrucciones del
modelo ni del contenido del catálogo: parámetros enlazados, herramientas fijas,
campos públicos seleccionados, sin descripciones libres en contexto. Los nombres
del catálogo son texto literal, no instrucciones. El filtro de palabras no es la
barrera de autorización: incluso una frase maliciosa que pase el parser solo
puede activar estas lecturas públicas acotadas.

## Validación ejecutada

122 pruebas aprobadas, ninguna omitida: parser, límites, privacidad, inyección como
texto inerte, timeout/saturación, error de acceso a datos y regresión IA-05/JWT.
PostgreSQL 17 aislado en 55439 verificó filtros ACTIVE, ciudad/nombre/capacidad,
precios, disponibilidad real y POST público; se reutilizaron además los tests
HotelSearchIntegrationTest y AvailabilityIntegrationTest. Mongo aislado en 27028
verificó la regresión de persistencia; startup con Mongo caído siguió operativo.
No se usaron bases de desarrollo ni conversaciones reales. Los procesos aislados
se detuvieron tras la validación.

Desde backend, con ICASTAY_TEST_POSTGRES=isolated, ICASTAY_TEST_MONGODB_URI apuntando
a 127.0.0.1:27028/icastay_ai_test_history y datasource explícito al PostgreSQL aislado:

```text
mvn -Dtest=Ai*Test,Ollama*Test,RoomServiceTest,AvailabilityIntegrationTest,HotelSearchIntegrationTest,JwtAuthenticationFilterTest test
mvn -DskipTests compile
git diff --check
```

Compilación y diff --check correctos. Sin dependencias nuevas, cambios Angular,
entidades, migraciones ni escrituras del flujo IA en PostgreSQL. Las inserciones
de fixtures se limitaron a las bases aisladas. Pendiente revisión en navegador con
los servicios locales: las pruebas HTTP usan MockMvc y Ollama usa su servidor
controlado de pruebas, no se afirma verificación del modelo local extremo a extremo.

## Corrección de reconocimiento natural — 2026-10-22

Casos de regresión: las tres preguntas reportadas, nombres sin comillas, ciudad
separada del nombre, mayúsculas, coincidencias ambiguas, nombres parciales o
inactivos y huéspedes inválidos. El solapamiento continúa en RoomService sin cambios.
La API local de solo lectura confirmó `Hotel Oasis Ica` en `Ica`, y devolvió dos
habitaciones para entrada 2026-12-15, salida 2026-12-17 y dos huéspedes al verificar.
Esta observación no garantiza disponibilidad futura. No se escribieron fixtures
en el catálogo real; los tests usan PostgreSQL/MongoDB aislados.

Validación de la corrección: **141 pruebas aprobadas, cero fallos/errores/omitidas**
con la misma selección backend documentada arriba, incluyendo 17 casos del parser
natural y 7 pruebas de integración del catálogo. `mvn -DskipTests compile` y
`git diff --check` correctos. Se mantienen 3 hoteles × 3 habitaciones, solo lectura
y reglas de solapamiento existentes. No se modificaron Angular, cookies, sesiones,
MongoDB, CSRF ni JWT en esta corrección. No se reinició el backend local en ejecución:
la observación del catálogo se hizo mediante sus endpoints públicos de lectura,
y el código corregido se validó contra bases aisladas.
