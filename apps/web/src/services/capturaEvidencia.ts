import jsQR from "jsqr";

/* Lectura de códigos QR y huella de una captura, en el dispositivo. La imagen nunca se envía:
   solo viaja el texto que la persona revisa y, si existe, el contenido del QR. */

export async function leerQr(archivo: File): Promise<string | null> {
  const mapa = await createImageBitmap(archivo);
  try {
    const lienzo = document.createElement("canvas");
    lienzo.width = mapa.width;
    lienzo.height = mapa.height;
    const ctx = lienzo.getContext("2d", { willReadFrequently: true });
    if (!ctx) return null;
    ctx.drawImage(mapa, 0, 0);
    const pixeles = ctx.getImageData(0, 0, lienzo.width, lienzo.height);
    const r = jsQR(pixeles.data, pixeles.width, pixeles.height, { inversionAttempts: "attemptBoth" });
    return r?.data?.trim() || null;
  } finally {
    mapa.close();
  }
}

/** Huella SHA-256 de los bytes de la captura (trazabilidad, sin enviar la imagen). */
export async function huellaCaptura(archivo: File): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", await archivo.arrayBuffer());
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}
