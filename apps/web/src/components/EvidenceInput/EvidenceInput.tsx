import type { EvidenciaTipo } from "@itf/core";
import { extraerEnlaces } from "@itf/core";
import { useEffect, useState } from "react";
import { leerCaptura } from "../../services/ocr";
import { huellaCaptura, leerQr } from "../../services/capturaEvidencia";
import { EJEMPLO } from "../../textos";
import { AudioInput } from "./AudioInput";
import type { AlcanceConsentimiento } from "../ConsentDialog";

export const MAX_CARACTERES = 5000;

interface Props {
  onAnalizar: (tipo: EvidenciaTipo, texto: string) => void;
  ocupado: boolean;
  textoInicial?: string;
  audioInicial?: File;
  pedirConsentimiento: (alcance: AlcanceConsentimiento, ocultos: number) => Promise<boolean>;
}

/* Entrada de evidencia: texto/enlace, captura (OCR en el dispositivo) o audio (transcripción con consentimiento). */
export function EvidenceInput({ onAnalizar, ocupado, textoInicial, audioInicial, pedirConsentimiento }: Props) {
  const [texto, setTexto] = useState(textoInicial ?? "");
  const [tipo, setTipo] = useState<EvidenciaTipo>("texto");
  const [estadoOcr, setEstadoOcr] = useState<string>("La captura se lee en tu dispositivo: la imagen no se envía.");
  const [leyendo, setLeyendo] = useState(false);
  const [huella, setHuella] = useState<string | null>(null);

  useEffect(() => {
    if (textoInicial) setTexto(textoInicial);
  }, [textoInicial]);

  const largo = texto.length > MAX_CARACTERES;

  async function alElegirCaptura(e: React.ChangeEvent<HTMLInputElement>) {
    const archivo = e.target.files?.[0];
    e.target.value = "";
    if (!archivo) return;
    setLeyendo(true);
    setHuella(null);
    setEstadoOcr("Leyendo la captura en tu dispositivo…");
    try {
      const [leido, qr, hash] = await Promise.all([
        leerCaptura(archivo, (p) => setEstadoOcr(`Leyendo la captura en tu dispositivo… ${p}%`)),
        leerQr(archivo).catch(() => null),
        huellaCaptura(archivo).catch(() => null),
      ]);
      const partes = [leido, qr ? `Código QR de la captura: ${qr}` : ""].filter(Boolean);
      if (partes.length === 0) {
        setEstadoOcr("No encontramos texto ni código QR en la imagen. Intenta con una captura más nítida o pega el texto.");
      } else {
        setTexto(partes.join("\n\n"));
        setTipo("captura");
        setHuella(hash);
        setEstadoOcr(qr ? "Texto y código QR extraídos. Revísalos antes de analizar." : "Texto extraído. Revísalo y corrígelo si hace falta antes de analizar.");
      }
    } catch {
      setEstadoOcr("No pudimos leer la captura. Intenta con una imagen más nítida o pega el texto.");
    } finally {
      setLeyendo(false);
    }
  }

  function enviar(e: React.FormEvent) {
    e.preventDefault();
    const limpio = texto.trim();
    if (!limpio) return;
    const t: EvidenciaTipo = tipo === "texto" && extraerEnlaces(limpio).length && limpio.split(/\s+/).length <= 3 ? "enlace" : tipo;
    onAnalizar(t, limpio.slice(0, MAX_CARACTERES));
  }

  return (
    <form className="tarjeta" onSubmit={enviar} aria-labelledby="titulo-evidencia">
      <h2 id="titulo-evidencia">1. Pega el mensaje, el enlace o sube una captura</h2>
      <label className="sr-only" htmlFor="entrada">Mensaje o enlace sospechoso</label>
      <textarea
        id="entrada"
        value={texto}
        onChange={(e) => {
          setTexto(e.target.value);
          if (tipo === "captura" && !e.target.value) {
            setTipo("texto");
            setHuella(null);
          }
        }}
        placeholder="Ej: Su cuenta será bloqueada hoy. Ingrese aquí para verificar: http://bancolombia-seguro.xyz/login"
        aria-describedby="ayuda-entrada"
      />
      <p id="ayuda-entrada" className={largo ? "aviso alerta" : "ayuda"} style={{ margin: "4px 0 0" }}>
        {largo
          ? `El texto es muy largo: analizaremos solo los primeros ${MAX_CARACTERES.toLocaleString("es-CO")} caracteres.`
          : "Nunca abrimos los enlaces que pegues: los revisamos sin visitarlos."}
      </p>

      <div className="fila-botones">
        <label className="btn secundario" style={{ cursor: leyendo ? "progress" : "pointer" }}>
          📷 Subir captura
          <input type="file" accept="image/*" onChange={alElegirCaptura} className="sr-only" disabled={leyendo} />
        </label>
        <AudioInput
          audioInicial={audioInicial}
          pedirConsentimiento={pedirConsentimiento}
          onTranscripcion={(t) => {
            setTexto(t);
            setTipo("audio");
          }}
        />
      </div>
      <p className="ayuda" role="status" aria-live="polite">
        {leyendo && <span className="cargando" aria-hidden="true" />} {estadoOcr}
      </p>
      {huella && (
        <p className="ayuda" style={{ margin: "4px 0 0", wordBreak: "break-all" }}>
          Huella SHA-256 de la captura: <code>{huella}</code>
        </p>
      )}

      <button className="btn" type="submit" disabled={ocupado || leyendo || !texto.trim()}>
        {ocupado ? <span className="cargando" aria-hidden="true" /> : "🔎"} Analizar
      </button>
      <button
        className="btn secundario"
        type="button"
        onClick={() => {
          setTexto(EJEMPLO);
          setTipo("texto");
        }}
      >
        Cargar ejemplo
      </button>
    </form>
  );
}
