package co.seguridad.agentellamadas.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import co.seguridad.agentellamadas.motor.Categoria

object Colores {
    val fondo = Color(0xFF0E1621)
    val superficie = Color(0xFF17212B)
    val superficieAlta = Color(0xFF202B36)
    val texto = Color(0xFFE9EEF3)
    val textoSuave = Color(0xFF8B9AA8)
    val azul = Color(0xFF1E88E5)
    val rojo = Color(0xFFE53935)
    val ambar = Color(0xFFFFB300)
    val verde = Color(0xFF43A047)
    val burbujaEmisor = Color(0xFF2B3742)

    fun de(categoria: Categoria?): Color = when (categoria) {
        Categoria.FRAUDE -> rojo
        Categoria.SPAM -> ambar
        Categoria.LEGITIMO -> verde
        else -> azul
    }
}

@Composable
fun TemaAgente(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Colores.azul,
            background = Colores.fondo,
            surface = Colores.superficie,
            surfaceVariant = Colores.superficieAlta,
            onBackground = Colores.texto,
            onSurface = Colores.texto,
            onSurfaceVariant = Colores.textoSuave,
            error = Colores.rojo,
        ),
        content = contenido,
    )
}
