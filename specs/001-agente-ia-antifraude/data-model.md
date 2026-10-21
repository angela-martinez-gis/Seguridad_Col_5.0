# Data Model: Agente IA antifraude (Fase 1)

**Plan**: [plan.md](./plan.md) · Tipos compartidos en `packages/core/src/types.ts` (Zod). Persistencia: SQLite con `node:sqlite` (`apps/api/src/db/migrations/*.sql`), efímera en la PoC.

Convenciones: fechas ISO 8601 UTC en almacenamiento, mostradas en `America/Bogota`; huellas en hex SHA-256 (64 caracteres); IDs de caso con formato `IF-YYYYMMDD-XXXXXX` (Crockford base32).

---

## 1. Evidencia *(transitoria — nunca se persiste)*

| Campo | Tipo | Reglas |
|---|---|---|
| `tipo` | `"texto" \| "enlace" \| "captura" \| "audio" \| "relato_llamada"` | requerido |
| `texto` | string | 1–5.000 caracteres tras normalizar; si es más largo se analiza el primer fragmento y se avisa |
| `textoRedactado` | string | salida de `redactPII(texto)`; es lo único que sale del dispositivo |
| `enlaces` | string[] | extraídos de `texto`, máx. 10 |
| `numeros` | string[] | teléfonos/remitentes detectados o informados |
| `relato` | `RelatoLlamada?` | solo si `tipo = relato_llamada` |
| `huella` | string(64) | SHA-256 de `normalize(texto)` calculado en el cliente |

**RelatoLlamada**: `quienDijoSer` (string), `quePidio` (enum múltiple: `clave`, `codigo`, `datos_tarjeta`, `transferencia`, `instalar_app`, `otro`), `numeroOrigen` (string?), `huboPresion` (bool), `entregoAlgo` (`"nada" | "datos" | "dinero" | "ambos"`), `notas` (string ≤2.000).

## 2. Análisis / Veredicto

| Campo | Tipo | Reglas |
|---|---|---|
| `nivel` | `"bajo" \| "medio" \| "alto"` | = max(`reglas.nivel`, `ia.nivel`) |
| `puntaje` | int 0–100 | escala normalizada para el medidor |
| `modalidad` | enum `Modalidad` | `suplantacion_bancaria`, `suplantacion_autoridad`, `falso_premio`, `falso_empleo`, `falsa_inversion`, `extorsion`, `vishing`, `compra_falsa`, `otro`, `ninguna` |
| `motivos` | `Motivo[]` | ≥1 si nivel ≠ bajo |
| `categorias` | Record<`Categoria`, int> | puntos por categoría para el desglose visual |
| `queHacer` | string[] | según nivel; si `entregoAlgo ≠ nada`, pasos urgentes primero |
| `denuncia` | `CanalDenuncia[]` | Policía/Fiscalía + entidad suplantada si se conoce |
| `fuentes` | `SourceCheck[]` | |
| `discrepancia` | string? | explicación si reglas e IA difieren (FR-006) |
| `ia` | `{ estado: "usada" \| "no_disponible" \| "sin_consentimiento" \| "desactivada", proveedor?, modelo? }` | |
| `versionReglas` | semver | de `packages/core` |
| `generadoEn` | datetime | |

**Motivo**: `texto` (es-CO, lenguaje sencillo), `origen` (`"regla" | "ia" | "fuente"`), `categoria` (`urgencia | datos | suplantacion | enlace | fuente_abierta | voz | manipulacion`), `fuente?` (nombre), `consultadoEn?`.

## 3. SourceCheck (consulta a fuente abierta)

| Campo | Tipo | Reglas |
|---|---|---|
| `fuente` | `"safe_browsing" \| "urlhaus" \| "rdap" \| "entidades" \| "comunidad"` | |
| `indicador` | string | dominio (nunca URL completa en almacenamiento) o número |
| `resultado` | `"coincide" \| "limpio" \| "sin_datos" \| "senal"` | `sin_datos` ≠ fraude (FR-013) |
| `detalle` | string | p. ej. "Dominio registrado hace 6 días" |
| `consultadoEn` | datetime | |
| `estado` | `"ok" \| "no_disponible" \| "timeout"` | |

## 4. Caso *(tabla `cases`)*

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | TEXT PK | `IF-YYYYMMDD-XXXXXX`, único |
| `owner_hash` | TEXT | `SHA-256(clientId + SALT)`; índice |
| `creado_en` | TEXT | ISO UTC |
| `huella_evidencia` | TEXT(64) | del cliente; índice para "Comprobar mensaje" |
| `tipo_evidencia` | TEXT | enum de Evidencia.tipo |
| `veredicto_json` | TEXT | Veredicto **sin** texto de evidencia ni datos personales |
| `consent_id` | TEXT FK → consents.id | requerido si `ia.estado = usada` |
| `previa_owner` | TEXT(64) | `hash_entrada` del caso anterior del mismo owner o `"INICIO"` |
| `previa_global` | TEXT(64) | `hash_entrada` del caso anterior en el servidor o `"INICIO"` |
| `hash_entrada` | TEXT(64) | SHA-256 del JSON canónico (claves ordenadas) de todas las columnas anteriores excepto `owner_hash` |
| `firma` | TEXT | Ed25519 (base64) sobre `hash_entrada` |
| `clave_id` | TEXT | huella de la clave pública usada |

**Reglas**: solo se crea con permiso "Guardar registro" activo (FR-014); inmutable (sin UPDATE); `DELETE` solo por "Borrar historial" del owner, lo que deja la cadena global con hueco **declarado** (`deleted_ranges` registra el rango borrado y la fecha, para que la verificación lo distinga de una manipulación).

**Verificación** (FR-016, SC-004): recalcular `hash_entrada`, validar firma y enlaces `previa_*` en orden; resultado `{ ok, rotoEn?, motivo? }`.

## 5. Consentimiento *(tabla `consents`)*

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | TEXT PK | UUID |
| `owner_hash` | TEXT | |
| `otorgado_en` | TEXT | |
| `alcance` | TEXT | `"analisis_ia"`, `"transcripcion"` |
| `proveedor` | TEXT | proveedor IA activo en ese momento |
| `texto_version` | TEXT | versión del texto de consentimiento mostrado |

Un consentimiento cubre **un** análisis (se consume al usarlo; `usado_en` TEXT).

## 6. Reporte de comunidad *(tabla `community_reports`)*

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | INTEGER PK | |
| `indicador` | TEXT | dominio normalizado (minúsculas, sin `www.`) o número E.164 |
| `tipo` | `"dominio" \| "numero"` | |
| `reporter_hash` | TEXT | owner_hash; UNIQUE(`indicador`, `reporter_hash`) |
| `modalidad` | TEXT | del veredicto |
| `entidad` | TEXT? | entidad suplantada |
| `creado_en` | TEXT | |

**Señal activa**: `COUNT(DISTINCT reporter_hash) ≥ 5` con `creado_en ≥ now − 72h` (FR-010).

## 7. Entidad vigilada *(archivo `packages/core/data/entidades.json`, versionado)*

`{ id, nombre, alias[], dominios[], canales: [{ tipo, valor, fuente (URL oficial), verificado (YYYY-MM-DD) }], denuncia?: { url, fuente } }`. Semilla: Bancolombia, Davivienda, BBVA, Nequi, Daviplata, Banco de Bogotá, Banco Agrario, DIAN, Fiscalía, Policía. Canales vacíos → "sin datos suficientes".

## 8. Estadísticas agregadas *(vista calculada, sin tabla propia)*

Para el panel público (FR-028): conteos por `modalidad`, por `entidad` (de `veredicto_json`) y por semana ISO, desde `cases` + `community_reports`. Solo se publica un grupo si tiene ≥3 casos (k-anonimato básico, SC-009). Sin texto, huellas ni owner.

## 9. Configuración del modelo IA *(variables de entorno, no BD)*

`AI_PROVIDER` (`groq | gemini | openrouter | openai | ollama | mock | none`), `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL`, `AI_STT_MODEL`, `AI_TIMEOUT_MS` (12.000), `AI_MAX_TOOL_CALLS` (1). Expuesto públicamente solo `{ proveedor, modelo }` en `GET /api/config` para el aviso de privacidad.

## Transiciones de estado (flujo de un análisis)

```text
entrada → normalizada → redactada ─┬─ (sin consentimiento) → reglas locales → veredicto[ia=sin_consentimiento]
                                   └─ (consentimiento) → API: reglas + fuentes ∥ agente IA
                                          ├─ IA ok → fusión max() → veredicto[ia=usada]
                                          └─ IA falla/timeout → veredicto[ia=no_disponible]
veredicto → (permiso registro) → caso firmado → comprobante descargable
```
