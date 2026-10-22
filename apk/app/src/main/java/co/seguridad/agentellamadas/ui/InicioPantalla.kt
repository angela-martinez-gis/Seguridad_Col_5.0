package co.seguridad.agentellamadas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.seguridad.agentellamadas.datos.Escenario
import co.seguridad.agentellamadas.datos.Escenarios
import co.seguridad.agentellamadas.llamada.EstadoLlamada
import co.seguridad.agentellamadas.llamada.Fase
import co.seguridad.agentellamadas.llamada.LlamadaViewModel
import co.seguridad.agentellamadas.motor.Categoria

@Composable
fun InicioPantalla(
    estado: EstadoLlamada,
    enVivo: Boolean,
    conRetraso: Boolean,
    alCambiarModo: (Boolean) -> Unit,
    alCambiarRetraso: (Boolean) -> Unit,
    alElegir: (Escenario) -> Unit,
    alAleatoria: () -> Unit,
    alCancelar: () -> Unit,
    alAbrirReales: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Colores.fondo).systemBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Encabezado() }
            item {
                OutlinedButton(onClick = alAbrirReales, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PhoneInTalk, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Llamadas reales (experimental)")
                }
            }
            item {
                Opciones(enVivo, conRetraso, alCambiarModo, alCambiarRetraso, alAleatoria)
            }
            for (categoria in listOf(Categoria.FRAUDE, Categoria.SPAM, Categoria.LEGITIMO)) {
                item {
                    Text(
                        text = when (categoria) {
                            Categoria.FRAUDE -> "Fraude"
                            Categoria.SPAM -> "Spam"
                            else -> "Legítimas"
                        },
                        color = Colores.de(categoria),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                items(Escenarios.todos.filter { it.esperado == categoria }, key = { it.id }) { e ->
                    TarjetaEscenario(e, enVivo) { alElegir(e) }
                }
            }
            item {
                Text(
                    "Durante la llamada, mantén presionada la barra de estado para abrir el Modo Director.",
                    color = Colores.textoSuave,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        if (estado.fase == Fase.PROGRAMADA) {
            Programada(estado.cuentaRegresiva, alCancelar)
        }
    }
}

@Composable
private fun Encabezado() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Colores.azul),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Shield, null, tint = Colores.texto) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Agente Antifraude de Llamadas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Colores.texto)
            Text("Prueba de concepto · llamadas simuladas", fontSize = 13.sp, color = Colores.textoSuave)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Opciones(
    enVivo: Boolean,
    conRetraso: Boolean,
    alCambiarModo: (Boolean) -> Unit,
    alCambiarRetraso: (Boolean) -> Unit,
    alAleatoria: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Colores.superficie).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Respuestas del emisor", color = Colores.textoSuave, fontSize = 13.sp)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !enVivo,
                onClick = { alCambiarModo(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Guion automático") }
            SegmentedButton(
                selected = enVivo,
                onClick = { alCambiarModo(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("En vivo (micrófono)") }
        }
        Text(
            if (enVivo) "Una persona hace de emisor y habla cerca del teléfono."
            else "El emisor habla con una voz sintética distinta a la del asistente.",
            color = Colores.textoSuave,
            fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recibir la llamada en ${LlamadaViewModel.RETRASO_SEGUNDOS} s", color = Colores.texto)
                Text("Tiempo para preparar la presentación", color = Colores.textoSuave, fontSize = 13.sp)
            }
            Switch(checked = conRetraso, onCheckedChange = alCambiarRetraso)
        }
        Button(
            onClick = alAleatoria,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Colores.azul, contentColor = Color.White),
        ) {
            Icon(Icons.Filled.Casino, null)
            Spacer(Modifier.width(8.dp))
            Text("Llamada sorpresa")
        }
    }
}

@Composable
private fun TarjetaEscenario(e: Escenario, enVivo: Boolean, alTocar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Colores.superficie)
            .clickable(onClick = alTocar)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Colores.de(e.esperado)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.titulo, color = Colores.texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(if (enVivo) "En vivo · ${e.numero}" else e.descripcion, color = Colores.textoSuave, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Programada(segundos: Int, alCancelar: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Colores.fondo.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Llamada entrante en", color = Colores.textoSuave, fontSize = 16.sp)
            Text("$segundos", color = Colores.texto, fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(24.dp))
            Button(onClick = alCancelar, colors = ButtonDefaults.buttonColors(containerColor = Colores.superficieAlta)) {
                Text("Cancelar")
            }
        }
    }
}
