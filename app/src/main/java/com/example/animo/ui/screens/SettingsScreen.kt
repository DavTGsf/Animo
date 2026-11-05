package com.example.animo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.animo.data.AppSettings
import com.example.animo.data.AutoLockOptions
import com.example.animo.data.MaxCharsOptions
import com.example.animo.security.Biometrics
import com.example.animo.ui.components.AddActivityDialog
import com.example.animo.ui.components.CartoonButton
import com.example.animo.ui.components.CartoonCard
import com.example.animo.ui.components.CartoonChip
import com.example.animo.ui.components.CartoonDialog
import com.example.animo.ui.components.CartoonIconButton
import com.example.animo.ui.components.SectionTitle
import com.example.animo.ui.components.activityVisual
import com.example.animo.ui.theme.AnimoTheme
import com.example.animo.ui.theme.Palette
import androidx.compose.material.icons.rounded.Science

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    customActivities: List<String>,
    biometricAvailable: Boolean,
    contentPadding: PaddingValues,
    onChangePin: () -> Unit,
    onBiometric: (Boolean) -> Unit,
    onAutoLock: (Int) -> Unit,
    onSecureScreen: (Boolean) -> Unit,
    onMaxChars: (Int) -> Unit,
    onAllowPast: (Boolean) -> Unit,
    onAddActivity: (String) -> String?,
    onRemoveActivity: (String) -> Unit,
    onLockNow: () -> Unit,
    onWipe: () -> Unit,
    onLoadDemo: () -> Unit,
) {
    val colors = AnimoTheme.colors
    val context = LocalContext.current
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    var confirmDemo by rememberSaveable { mutableStateOf(false) }
    var removeActivity by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Ajustes", style = MaterialTheme.typography.displaySmall, color = colors.text) }

        // ------------------------------------------------ Seguridad
        item {
            CartoonCard(Modifier.fillMaxWidth()) {
                SectionTitle("Privacidad y seguridad", icon = Icons.Rounded.Shield, tint = Palette.Grape)
                Spacer(Modifier.height(8.dp))
                NavRow(Icons.Rounded.Password, "Cambiar PIN", "PIN de 4 dígitos", onChangePin)
                SwitchRow(
                    title = "Huella digital",
                    subtitle = if (biometricAvailable) "Desbloquea con tu huella o rostro"
                    else "No hay biometría configurada en este dispositivo",
                    checked = settings.biometricEnabled && biometricAvailable,
                    enabled = biometricAvailable,
                    onChange = { on ->
                        if (on) {
                            // Confirmamos que la huella funciona antes de activarla.
                            Biometrics.authenticate(
                                context, "Activar huella", "Confirma tu huella para usarla en Ánimo",
                                onSuccess = { onBiometric(true) },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                            )
                        } else onBiometric(false)
                    },
                )
                SwitchRow(
                    title = "Ocultar en recientes y bloquear capturas",
                    subtitle = "Evita que tu diario aparezca en la vista de apps recientes",
                    checked = settings.secureScreen,
                    onChange = onSecureScreen,
                )
                Spacer(Modifier.height(10.dp))
                Text("Bloqueo automático", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text(
                    "Tiempo fuera de la app antes de pedir el PIN otra vez. Tras 1 min sin tocar la pantalla también se bloquea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AutoLockOptions.forEach { s ->
                        CartoonChip(
                            text = autoLockLabel(s),
                            selected = settings.autoLockSeconds == s,
                            onClick = { onAutoLock(s) },
                            selectedColor = Palette.Grape,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                CartoonIconButton("Bloquear ahora", Icons.Rounded.Lock, onLockNow, Modifier.fillMaxWidth(), color = Palette.Sun)
            }
        }

        // ------------------------------------------------ Diario
        item {
            CartoonCard(Modifier.fillMaxWidth()) {
                SectionTitle("Diario", icon = Icons.Rounded.EditNote, tint = Palette.Sky)
                Spacer(Modifier.height(10.dp))
                Text("Máximo de caracteres en la reflexión", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MaxCharsOptions.forEach { n ->
                        CartoonChip(
                            text = "$n",
                            selected = settings.maxReflectionChars == n,
                            onClick = { onMaxChars(n) },
                            selectedColor = Palette.Sky,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "Registrar días anteriores",
                    subtitle = "Por defecto solo puedes registrar el día de hoy. Actívalo para completar días que olvidaste.",
                    checked = settings.allowPastDays,
                    onChange = onAllowPast,
                )
            }
        }

        // ------------------------------------------------ Actividades
        item {
            CartoonCard(Modifier.fillMaxWidth()) {
                SectionTitle("Actividades personalizadas", icon = Icons.Rounded.Interests, tint = Palette.Mint)
                Spacer(Modifier.height(10.dp))
                if (customActivities.isEmpty()) {
                    Text(
                        "Aún no creas etiquetas propias. Puedes hacerlo aquí o al registrar tu día.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        customActivities.forEach { name ->
                            val v = activityVisual(name)
                            CartoonButton(
                                onClick = { removeActivity = name },
                                color = v.color,
                                minHeight = 48.dp,
                                shadow = 3.dp,
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(start = 14.dp, end = 10.dp),
                            ) {
                                Text(name, style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Rounded.Close, "Quitar $name", Modifier.padding(2.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                CartoonIconButton("Agregar actividad", Icons.Rounded.Add, { showAdd = true }, Modifier.fillMaxWidth(), color = Palette.Mint)
            }
        }

        // ------------------------------------------------ Datos
        item {
            CartoonCard(Modifier.fillMaxWidth()) {
                SectionTitle("Tus datos", icon = Icons.Rounded.Info, tint = Palette.Pink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tu diario se guarda cifrado (AES-256-GCM) con una llave protegida por el Android Keystore. " +
                        "Nunca se envía a internet ni se incluye en respaldos.\n\n" +
                            "Ánimo no realiza diagnósticos médicos ni sustituye atención profesional: solo te ayuda a identificar patrones personales con tus propios datos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(18.dp))
                CartoonIconButton(
                    "Borrar todo el diario",
                    Icons.Rounded.DeleteForever,
                    { confirmWipe = true },
                    Modifier.fillMaxWidth(),
                    color = colors.danger,
                )
                Spacer(Modifier.height(10.dp))
                CartoonIconButton(
                    "Cargar datos de prueba (90 días)",
                    Icons.Rounded.Science,
                    { confirmDemo = true },
                    Modifier.fillMaxWidth(),
                    color = Palette.Lilac,
                )
            }
        }
    }

    if (showAdd) AddActivityDialog(onAdd = onAddActivity, onDismiss = { showAdd = false })

    removeActivity?.let { name ->
        CartoonDialog(
            title = "¿Quitar “$name”?",
            message = "Ya no aparecerá al registrar tu día. Tus registros anteriores con esta actividad se conservan.",
            confirmLabel = "Quitar",
            confirmColor = colors.danger,
            onConfirm = { onRemoveActivity(name); removeActivity = null },
            onDismiss = { removeActivity = null },
        )
    }

    if (confirmWipe) {
        CartoonDialog(
            title = "¿Borrar todo?",
            message = "Se eliminarán permanentemente todos tus registros y actividades personalizadas. Esta acción no se puede deshacer.",
            confirmLabel = "Borrar todo",
            confirmColor = colors.danger,
            icon = Icons.Rounded.DeleteForever,
            onConfirm = { onWipe(); confirmWipe = false },
            onDismiss = { confirmWipe = false },
        )
    }

    if (confirmDemo) {
        CartoonDialog(
            title = "¿Cargar datos de prueba?",
            message = "Se reemplazará tu diario por 90 días generados con patrones conocidos para probar el análisis. Tus registros actuales se perderán.",
            confirmLabel = "Cargar",
            confirmColor = Palette.Lilac,
            icon = Icons.Rounded.Science,
            onConfirm = { onLoadDemo(); confirmDemo = false },
            onDismiss = { confirmDemo = false },
        )
    }
}

private fun autoLockLabel(seconds: Int) = when {
    seconds == 0 -> "Al salir"
    seconds < 60 -> "$seconds s"
    else -> "${seconds / 60} min"
}

/** Toda la fila es el objetivo táctil del interruptor (más fácil de alcanzar que el switch solo). */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = AnimoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) colors.text else colors.textMuted)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Grape,
                checkedThumbColor = Palette.White,
                checkedBorderColor = colors.outline,
                uncheckedBorderColor = colors.outline,
                uncheckedThumbColor = colors.outline,
                uncheckedTrackColor = colors.surfaceAlt,
            ),
        )
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = AnimoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textMuted)
    }
}
