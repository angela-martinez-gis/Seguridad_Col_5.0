import { useEffect, useState } from "react";
import { api } from "./apiClient";

export interface ConfigPublica {
  ia: { proveedor: string; modelo: string | null; disponible: boolean; transcripcion: boolean };
  fuentes: Record<string, boolean>;
  versionReglas: string;
  textoConsentimientoVersion: string;
  registroEfimero: boolean;
}

/* Descripción del tratamiento de datos de cada proveedor (T057, research.md §1).
   Revisado 2026-10-20; confirmar contra los términos vigentes antes de salir a público. */
export const PROVEEDORES_IA: Record<string, { nombre: string; datos: string; fuente?: string }> = {
  groq: {
    nombre: "Groq (modelos abiertos gpt-oss, Qwen y Whisper)",
    datos:
      "Según la documentación de Groq, por defecto no conserva los textos ni audios de las solicitudes de inferencia; solo metadatos de uso. El compromiso de no entrenar con estos datos figura en su acuerdo de tratamiento de datos (pendiente de confirmación formal).",
    fuente: "https://console.groq.com/docs/your-data",
  },
  gemini: {
    nombre: "Google Gemini",
    datos:
      "En el plan gratuito, Google puede usar lo enviado para mejorar sus productos y entrenar modelos, con posible revisión humana. En el plan de pago no aplica ese uso.",
    fuente: "https://ai.google.dev/gemini-api/terms",
  },
  openrouter: {
    nombre: "OpenRouter",
    datos: "El tratamiento depende del modelo enrutado; consulta la política de cada proveedor en OpenRouter.",
    fuente: "https://openrouter.ai/privacy",
  },
  openai: { nombre: "OpenAI", datos: "Según sus términos de API, los datos enviados no se usan para entrenar por defecto.", fuente: "https://openai.com/policies" },
  ollama: { nombre: "Modelo propio (Ollama)", datos: "El modelo corre en servidores propios del servicio; no se comparte con terceros." },
  mock: { nombre: "Simulador de pruebas", datos: "Entorno de desarrollo: no se envía nada a terceros." },
  none: { nombre: "Sin IA", datos: "El análisis usa solo reglas y fuentes abiertas." },
};

let cache: Promise<ConfigPublica | null> | null = null;

export function cargarConfig(): Promise<ConfigPublica | null> {
  cache ??= api<ConfigPublica>("/api/config", { timeoutMs: 15_000 }).catch(() => {
    cache = null;
    return null;
  });
  return cache;
}

export function useConfig() {
  const [config, setConfig] = useState<ConfigPublica | null>(null);
  useEffect(() => {
    let vivo = true;
    cargarConfig().then((c) => vivo && setConfig(c));
    return () => {
      vivo = false;
    };
  }, []);
  return config;
}
