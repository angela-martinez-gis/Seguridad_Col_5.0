import { BASE_URL_POR_PROVEEDOR, type Config } from "../config";
import { MockProvider } from "./mock";
import { OpenAiCompatibleProvider } from "./openaiCompatible";
import type { AiProvider } from "./types";

/** Cambiar de proveedor = cambiar variables de entorno (FR-024, SC-005). */
export function createAiProvider(config: Config): AiProvider | null {
  switch (config.AI_PROVIDER) {
    case "none":
      return null;
    case "mock":
      return new MockProvider(config.MOCK_FAIL);
    default:
      return new OpenAiCompatibleProvider({
        id: config.AI_PROVIDER,
        baseURL: config.AI_BASE_URL ?? BASE_URL_POR_PROVEEDOR[config.AI_PROVIDER],
        apiKey: config.AI_API_KEY ?? "",
        model: config.AI_MODEL,
        fallbackModels: config.AI_MODELOS_RESPALDO,
        sttModel: config.AI_STT_MODEL,
      });
  }
}
