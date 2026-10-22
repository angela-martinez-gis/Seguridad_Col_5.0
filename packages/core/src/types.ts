import { z } from "zod";

/* Tipos compartidos entre la PWA y la API. Ver specs/001-agente-ia-antifraude/data-model.md */

export const Nivel = z.enum(["bajo", "medio", "alto"]);
export type Nivel = z.infer<typeof Nivel>;

export const Categoria = z.enum([
  "urgencia",
  "datos",
  "suplantacion",
  "enlace",
  "fuente_abierta",
  "voz",
  "manipulacion",
]);
export type Categoria = z.infer<typeof Categoria>;

export const Origen = z.enum(["regla", "ia", "fuente"]);
export type Origen = z.infer<typeof Origen>;

export const Modalidad = z.enum([
  "suplantacion_bancaria",
  "suplantacion_autoridad",
  "falso_premio",
  "falso_empleo",
  "falsa_inversion",
  "extorsion",
  "vishing",
  "compra_falsa",
  "otro",
  "ninguna",
]);
export type Modalidad = z.infer<typeof Modalidad>;

export const EvidenciaTipo = z.enum(["texto", "enlace", "captura", "audio", "relato_llamada"]);
export type EvidenciaTipo = z.infer<typeof EvidenciaTipo>;

export const Motivo = z.object({
  texto: z.string().max(400),
  origen: Origen,
  categoria: Categoria,
  puntos: z.number().int().min(0).max(10).optional(),
  fuente: z.string().optional(),
  consultadoEn: z.string().optional(),
});
export type Motivo = z.infer<typeof Motivo>;

export const QuePidio = z.enum(["clave", "codigo", "datos_tarjeta", "transferencia", "instalar_app", "otro"]);
export type QuePidio = z.infer<typeof QuePidio>;

export const RelatoLlamada = z.object({
  quienDijoSer: z.string().max(200).default(""),
  quePidio: z.array(QuePidio).default([]),
  numeroOrigen: z.string().max(40).optional(),
  huboPresion: z.boolean().default(false),
  entregoAlgo: z.enum(["nada", "datos", "dinero", "ambos"]).default("nada"),
  notas: z.string().max(2000).default(""),
});
export type RelatoLlamada = z.infer<typeof RelatoLlamada>;

export const Fuente = z.enum(["safe_browsing", "urlhaus", "virustotal", "rdap", "entidades", "comunidad"]);
export type Fuente = z.infer<typeof Fuente>;

export const SourceCheck = z.object({
  fuente: Fuente,
  indicador: z.string(),
  resultado: z.enum(["coincide", "limpio", "sin_datos", "senal"]),
  detalle: z.string(),
  consultadoEn: z.string(),
  estado: z.enum(["ok", "no_disponible", "timeout"]),
});
export type SourceCheck = z.infer<typeof SourceCheck>;

export const IaEstado = z.enum(["usada", "no_disponible", "sin_consentimiento", "desactivada"]);
export type IaEstado = z.infer<typeof IaEstado>;

export const CanalDenuncia = z.object({ nombre: z.string(), url: z.string(), detalle: z.string().optional() });
export type CanalDenuncia = z.infer<typeof CanalDenuncia>;

export const Veredicto = z.object({
  nivel: Nivel,
  puntaje: z.number().int().min(0).max(100),
  modalidad: Modalidad,
  motivos: z.array(Motivo),
  categorias: z.record(z.string(), z.number()),
  queHacer: z.array(z.string()),
  urgente: z.array(z.string()).default([]),
  denuncia: z.array(CanalDenuncia),
  fuentes: z.array(SourceCheck),
  discrepancia: z.string().optional(),
  entidad: z.string().optional(),
  ia: z.object({ estado: IaEstado, proveedor: z.string().optional(), modelo: z.string().optional() }),
  versionReglas: z.string(),
  generadoEn: z.string(),
});
export type Veredicto = z.infer<typeof Veredicto>;

/** Evidencia que viaja al servidor (ya redactada). Nunca se persiste. */
export const SolicitudAnalisis = z.object({
  consentId: z.string().uuid(),
  tipo: EvidenciaTipo,
  texto: z.string().max(5000),
  numeros: z.array(z.string().max(40)).max(5).optional(),
  relato: RelatoLlamada.optional(),
  /** Permiso "Verificador de enlaces": false = no consultar fuentes sobre los enlaces. */
  consultarEnlaces: z.boolean().default(true),
});
export type SolicitudAnalisis = z.infer<typeof SolicitudAnalisis>;

/** Salida exigida al modelo de IA (contracts/ai-provider.md). */
export const AiAssessment = z.object({
  nivel: Nivel,
  confianza: z.number().min(0).max(1).default(0.5),
  modalidad: Modalidad.catch("otro"),
  motivos: z
    .array(z.object({ texto: z.string().max(240), categoria: Categoria.catch("suplantacion") }))
    .max(8)
    .default([]),
  entidadSuplantada: z.string().nullable().optional(),
  pasosAdicionales: z.array(z.string().max(200)).max(5).default([]),
});
export type AiAssessment = z.infer<typeof AiAssessment>;

export const Caso = z.object({
  id: z.string(),
  creadoEn: z.string(),
  huellaEvidencia: z.string().regex(/^[a-f0-9]{64}$/),
  tipoEvidencia: EvidenciaTipo,
  veredicto: Veredicto,
  previaOwner: z.string(),
  previaGlobal: z.string(),
  hashEntrada: z.string(),
});
export type Caso = z.infer<typeof Caso>;

export const FORMATO_COMPROBANTE = "identifica-tu-fraude/comprobante@1";

export const Comprobante = z.object({
  formato: z.literal(FORMATO_COMPROBANTE),
  caso: Caso,
  firma: z.string(),
  claveId: z.string(),
  clavePublicaPem: z.string(),
});
export type Comprobante = z.infer<typeof Comprobante>;

export const NIVEL_ORDEN: Record<Nivel, number> = { bajo: 0, medio: 1, alto: 2 };
export const maxNivel = (a: Nivel, b: Nivel): Nivel => (NIVEL_ORDEN[a] >= NIVEL_ORDEN[b] ? a : b);
