import { fileURLToPath } from "node:url";
import { z } from "zod";

export const PROVEEDORES = ["groq", "gemini", "openrouter", "openai", "ollama", "mock", "none"] as const;

const bool = z
  .string()
  .optional()
  .transform((v) => v === "true" || v === "1");

const vacioComoUndefined = z
  .string()
  .optional()
  .transform((v) => (v && v.trim() ? v : undefined));

const EnvSchema = z
  .object({
    PORT: z.coerce.number().int().default(8787),
    HOST: z.string().default("0.0.0.0"),
    NODE_ENV: z.enum(["development", "production", "test"]).default("development"),
    CORS_ORIGIN: z.string().default("http://localhost:5173"),
    DB_PATH: z.string().default("./data/app.db"),
    SERVE_WEB: bool,
    WEB_DIST: z.string().default(fileURLToPath(new URL("../../web/dist", import.meta.url))),
    APP_VERSION: z.string().default("0.1.0"),

    AI_PROVIDER: z.enum(PROVEEDORES).default("mock"),
    AI_BASE_URL: vacioComoUndefined,
    AI_API_KEY: vacioComoUndefined,
    AI_MODEL: z.string().default("openai/gpt-oss-120b"),
    /** Modelos de respaldo separados por coma, usados cuando el anterior agota su cuota (429). */
    AI_MODELOS_RESPALDO: z
      .string()
      .optional()
      .transform((v) => (v ?? "").split(",").map((s) => s.trim()).filter(Boolean)),
    AI_STT_MODEL: vacioComoUndefined,
    AI_TIMEOUT_MS: z.coerce.number().int().min(1000).default(12000),
    AI_MAX_TOOL_CALLS: z.coerce.number().int().min(0).max(6).default(1),
    MOCK_FAIL: z.enum(["none", "timeout", "invalid", "429"]).default("none"),

    RATE_LIMIT_ANALISIS: z.coerce.number().int().min(1).default(20),
    RATE_LIMIT_GLOBAL: z.coerce.number().int().min(1).default(100),
    SOURCE_TIMEOUT_MS: z.coerce.number().int().min(500).default(4000),
    SAFE_BROWSING_API_KEY: vacioComoUndefined,
    URLHAUS_AUTH_KEY: vacioComoUndefined,

    CASE_SIGNING_KEY: vacioComoUndefined,
    OWNER_SALT: z.string().default("sal-de-desarrollo"),
  })
  .superRefine((env, ctx) => {
    // Constitución §II: nunca mostrar resultados simulados como reales en producción.
    if (env.NODE_ENV === "production" && env.AI_PROVIDER === "mock") {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["AI_PROVIDER"],
        message: "AI_PROVIDER=mock no está permitido en producción. Usa un proveedor real o 'none'.",
      });
    }
    if (env.NODE_ENV === "production" && env.OWNER_SALT === "sal-de-desarrollo") {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["OWNER_SALT"], message: "Define OWNER_SALT en producción." });
    }
    if (!["mock", "none"].includes(env.AI_PROVIDER) && !env.AI_API_KEY && env.AI_PROVIDER !== "ollama") {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["AI_API_KEY"], message: `AI_API_KEY es obligatoria para ${env.AI_PROVIDER}.` });
    }
  });

export type Config = z.infer<typeof EnvSchema>;

export const BASE_URL_POR_PROVEEDOR: Record<string, string> = {
  groq: "https://api.groq.com/openai/v1",
  gemini: "https://generativelanguage.googleapis.com/v1beta/openai/",
  openrouter: "https://openrouter.ai/api/v1",
  openai: "https://api.openai.com/v1",
  ollama: "http://localhost:11434/v1",
};

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const r = EnvSchema.safeParse(env);
  if (!r.success) {
    const detalle = r.error.issues.map((i) => `  - ${i.path.join(".")}: ${i.message}`).join("\n");
    throw new Error(`Configuración inválida:\n${detalle}`);
  }
  return r.data;
}

export const corsOrigins = (c: Config) => c.CORS_ORIGIN.split(",").map((s) => s.trim()).filter(Boolean);
