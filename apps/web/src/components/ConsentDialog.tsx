import { useCallback, useEffect, useRef, useState } from "react";
import { PROVEEDORES_IA, useConfig } from "../services/config";

export type AlcanceConsentimiento = "analisis_ia" | "transcripcion";

interface Pedido {
  alcance: AlcanceConsentimiento;
  ocultos: number;
  vistaPrevia?: string;
  resolver: (ok: boolean) => void;
}

/* Consentimiento explícito por análisis (FR-020): qué se envía, a quién y para qué.
   "Solo en mi dispositivo" analiza con las reglas locales y no envía nada. */
export function useConsentimiento() {
  const [pedido, setPedido] = useState<Pedido | null>(null);
  const pedir = useCallback(
    (alcance: AlcanceConsentimiento, ocultos: number, vistaPrevia?: string) =>
      new Promise<boolean>((resolver) => setPedido({ alcance, ocultos, vistaPrevia, resolver })),
    [],
  );
  const responder = (ok: boolean) => {
    pedido?.resolver(ok);
    setPedido(null);
  };
  const dialogo = pedido ? <ConsentDialog pedido={pedido} onResponder={responder} /> : null;
  return { pedir, dialogo };
}

function ConsentDialog({ pedido, onResponder }: { pedido: Pedido; onResponder: (ok: boolean) => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  const config = useConfig();
  const proveedor = PROVEEDORES_IA[config?.ia.proveedor ?? "groq"] ?? PROVEEDORES_IA.groq;
  const esAudio = pedido.alcance === "transcripcion";

  useEffect(() => {
    const d = ref.current;
    if (d && !d.open) d.showModal?.();
    return () => d?.close?.();
  }, []);

  return (
    <dialog
      ref={ref}
      className="dialogo"
      aria-labelledby="consent-titulo"
      aria-describedby="consent-desc"
      onCancel={(e) => {
        e.preventDefault();
        onResponder(false);
      }}
    >
      <div className="dialogo-cuerpo">
        <h2 id="consent-titulo">{esAudio ? "¿Transcribimos tu audio?" : "¿Analizamos con IA y fuentes abiertas?"}</h2>
        <p id="consent-desc" className="aviso" style={{ marginTop: 0 }}>
          {esAudio
            ? "El audio sale de tu dispositivo solo para convertirlo en texto. Tú decides cada vez."
            : "El texto sale de tu dispositivo con tus datos personales ocultos. Tú decides cada vez."}
          {!esAudio && pedido.ocultos > 0 && (
            <>
              {" "}
              <strong>Ocultamos {pedido.ocultos} dato(s).</strong>
            </>
          )}
        </p>
        <details>
          <summary className="ayuda">Ver detalles: qué se envía, a quién y para qué</summary>
          <dl>
            <dt>Qué se envía</dt>
            <dd>
              {esAudio
                ? "El audio, solo para convertirlo en texto. No se guarda."
                : "El texto del mensaje con tus datos personales ocultos (claves, códigos, tarjetas, documentos, correos y parámetros de enlaces)."}
            </dd>
            {pedido.vistaPrevia && (
              <details>
                <summary className="ayuda">Ver exactamente lo que se envía</summary>
                <p className="log" style={{ whiteSpace: "pre-wrap" }}>{pedido.vistaPrevia}</p>
              </details>
            )}
            <dt>A quién</dt>
            <dd>
              Al servicio Identifica tu fraude y al proveedor de IA <strong>{proveedor.nombre}</strong>. {proveedor.datos}
              {!esAudio && " Los enlaces se consultan por su dominio en listas públicas de sitios peligrosos; nunca se abren."}
            </dd>
            <dt>Para qué</dt>
            <dd>
              {esAudio
                ? "Para que puedas revisar el texto y analizarlo."
                : "Para estimar el riesgo de fraude y orientarte. El mensaje no se guarda; si luego guardas el caso, solo queda su huella."}
            </dd>
          </dl>
        </details>
        <div className="fila-botones">
          <button type="button" className="btn secundario" onClick={() => onResponder(false)}>
            Solo en mi dispositivo
          </button>
          <button type="button" className="btn" onClick={() => onResponder(true)} autoFocus>
            Aceptar y {esAudio ? "transcribir" : "analizar"}
          </button>
        </div>
        <p className="ayuda" style={{ marginTop: 10 }}>
          Más detalles en la <a href={`${import.meta.env.BASE_URL}privacidad`} target="_blank" rel="noopener">política de privacidad</a>.
        </p>
      </div>
    </dialog>
  );
}
