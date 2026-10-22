package co.seguridad.agentellamadas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LlamadasRealesPantalla(
    esPredeterminada: Boolean,
    permisosCompletos: Boolean,
    asistenteActivo: Boolean,
    vozPorLlamada: Boolean,
    numero: String,
    alCambiarNumero: (String) -> Unit,
    alHacerPredeterminada: () -> Unit,
    alPedirPermisos: () -> Unit,
    alCambiarAsistente: (Boolean) -> Unit,
    alCambiarVoz: (Boolean) -> Unit,
    alLlamar: () -> Unit,
    alRestaurarTelefono: () -> Unit,
    alVolver: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Colores.fondo)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Colores.texto) }
            Column {
                Text("Llamadas reales", color = Colores.texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Experimental · el asistente contesta llamadas de la SIM", color = Colores.textoSuave, fontSize = 13.sp)
            }
        }

        Tarjeta {
            Paso(
                hecho = esPredeterminada,
                titulo = "Teléfono predeterminado",
                detalle = if (esPredeterminada) "Esta app maneja las llamadas del teléfono."
                else "Para contestar y colgar, la app debe reemplazar a la app de Teléfono mientras dure la prueba.",
            )
            if (!esPredeterminada) Accion("Hacer predeterminada", alHacerPredeterminada)
            Paso(
                hecho = permisosCompletos,
                titulo = "Permisos",
                detalle = "Micrófono (escuchar al emisor), llamar y notificaciones (llamada entrante con la pantalla bloqueada).",
            )
            if (!permisosCompletos) Accion("Conceder permisos", alPedirPermisos)
        }

        Tarjeta {
            Interruptor("El asistente contesta las llamadas entrantes", "Si se apaga, suenan normal y contestas tú.", asistenteActivo, alCambiarAsistente)
            Interruptor(
                "Voz del asistente por el canal de llamada",
                "Diagnóstico: si el emisor no escucha las preguntas, prueba apagarlo.",
                vozPorLlamada,
                alCambiarVoz,
            )
        }

        Tarjeta {
            Text("Hacer una llamada", color = Colores.texto, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = numero,
                    onValueChange = { alCambiarNumero(it.filter { c -> c.isDigit() || c in "+*#" }) },
                    label = { Text("Número") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = alLlamar,
                    enabled = numero.isNotBlank() && esPredeterminada,
                    colors = ButtonDefaults.buttonColors(containerColor = Colores.verde, contentColor = Color.White),
                ) { Icon(Icons.Filled.Call, "Llamar") }
            }
        }

        Tarjeta {
            Text("Prueba en el teléfono de la demo", color = Colores.texto, fontWeight = FontWeight.SemiBold)
            Text(
                "Pide a alguien que te llame desde otro teléfono y verifica:",
                color = Colores.textoSuave,
                fontSize = 14.sp,
            )
            for (p in listOf(
                "A. El asistente contesta solo a los 4 segundos.",
                "B. Lo que dice quien llama aparece en las burbujas grises.",
                "C. Quien llama escucha las preguntas del asistente.",
                "D. Con un relato de fraude, el asistente cuelga.",
            )) Text(p, color = Colores.texto, fontSize = 14.sp)
            Text(
                "Si B falla, este teléfono no deja escuchar durante la llamada: usa el Modo Director (mantén presionada la barra de color).",
                color = Colores.ambar,
                fontSize = 13.sp,
            )
        }

        OutlinedButton(onClick = alRestaurarTelefono, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a la app de Teléfono original")
        }
    }
}

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Colores.superficie).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { contenido() }
}

@Composable
private fun Paso(hecho: Boolean, titulo: String, detalle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (hecho) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            null,
            tint = if (hecho) Colores.verde else Colores.ambar,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(titulo, color = Colores.texto, fontWeight = FontWeight.SemiBold)
            Text(detalle, color = Colores.textoSuave, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Accion(texto: String, alTocar: () -> Unit) {
    Button(
        onClick = alTocar,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Colores.azul, contentColor = Color.White),
    ) { Text(texto) }
}

@Composable
private fun Interruptor(titulo: String, detalle: String, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(titulo, color = Colores.texto)
            Text(detalle, color = Colores.textoSuave, fontSize = 13.sp)
        }
        Switch(checked = activo, onCheckedChange = alCambiar)
    }
}
