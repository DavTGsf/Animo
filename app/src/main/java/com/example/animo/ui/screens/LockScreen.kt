package com.example.animo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.animo.data.AppSettings
import com.example.animo.data.Mood
import com.example.animo.data.SettingsStore
import com.example.animo.data.SettingsStore.Companion.PIN_LENGTH
import com.example.animo.security.Biometrics
import com.example.animo.ui.AppViewModel
import com.example.animo.ui.components.BlobBackground
import com.example.animo.ui.components.Bobbing
import com.example.animo.ui.components.CartoonButton
import com.example.animo.ui.components.IconBubble
import com.example.animo.ui.components.MoodFace
import com.example.animo.ui.components.cartoonSurface
import com.example.animo.ui.theme.AnimoTheme
import com.example.animo.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface PinFeedback {
    data object Accept : PinFeedback
    data class Reject(val message: String, val lockedUntil: Long = 0L) : PinFeedback
}

// ------------------------------------------------------------------ Flujos

@Composable
fun UnlockScreen(vm: AppViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val canUseBiometric = settings.biometricEnabled && vm.biometricAvailable
    val biometric: () -> Unit = {
        Biometrics.authenticate(
            context,
            title = "Desbloquear Ánimo",
            subtitle = "Usa tu huella para abrir tu diario",
            onSuccess = vm::unlockWithBiometric,
        )
    }
    LaunchedEffect(Unit) {
        if (canUseBiometric) {
            delay(350)
            biometric()
        }
    }
    PinPad(
        title = "Introduce tu PIN",
        subtitle = "Tu diario está protegido",
        actionLabel = "Desbloquear",
        initialLockedUntil = vm.lockoutUntil(),
        onBiometric = if (canUseBiometric) biometric else null,
        onSubmit = { pin ->
            when (val r = vm.submitPin(pin)) {
                SettingsStore.PinResult.Ok -> PinFeedback.Accept
                is SettingsStore.PinResult.Wrong -> PinFeedback.Reject(
                    if (r.attemptsLeft > 1) "PIN incorrecto · te quedan ${r.attemptsLeft} intentos"
                    else "PIN incorrecto · último intento"
                )
                is SettingsStore.PinResult.LockedOut -> PinFeedback.Reject("Demasiados intentos", r.untilMillis)
            }
        },
    )
}

@Composable
fun CreatePinFlow(
    onCreated: (String) -> Unit,
    welcome: Boolean,
    onCancel: (() -> Unit)? = null,
) {
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    PinPad(
        title = if (first == null) (if (welcome) "¡Hola! Crea tu PIN" else "Nuevo PIN") else "Confirma tu PIN",
        subtitle = if (first == null) "4 dígitos para proteger tu diario. Tus datos se cifran y nunca salen de este teléfono."
        else "Escríbelo otra vez para confirmar",
        actionLabel = if (first == null) "Continuar" else "Guardar PIN",
        onCancel = onCancel,
        onSubmit = { pin ->
            val current = first
            when {
                current == null && isObviousPin(pin) -> PinFeedback.Reject("Ese PIN es muy fácil de adivinar")
                current == null -> { first = pin; PinFeedback.Accept }
                pin == current -> { onCreated(pin); PinFeedback.Accept }
                else -> { first = null; PinFeedback.Reject("No coinciden. Empecemos de nuevo") }
            }
        },
    )
}

@Composable
fun ChangePinFlow(vm: AppViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    var verified by rememberSaveable { mutableStateOf(false) }
    AnimatedContent(
        targetState = verified,
        transitionSpec = { (slideInVertically { it / 4 } + fadeIn()) togetherWith (slideOutVertically { -it / 4 } + fadeOut()) },
        label = "changePin",
    ) { ok ->
        if (!ok) {
            PinPad(
                title = "PIN actual",
                subtitle = "Confirma que eres tú",
                actionLabel = "Continuar",
                initialLockedUntil = vm.lockoutUntil(),
                onCancel = onCancel,
                onSubmit = { pin ->
                    when (val r = vm.verifyPin(pin)) {
                        SettingsStore.PinResult.Ok -> { verified = true; PinFeedback.Accept }
                        is SettingsStore.PinResult.Wrong -> PinFeedback.Reject("PIN incorrecto · te quedan ${r.attemptsLeft} intentos")
                        is SettingsStore.PinResult.LockedOut -> PinFeedback.Reject("Demasiados intentos", r.untilMillis)
                    }
                },
            )
        } else {
            CreatePinFlow(onCreated = { vm.changePin(it); onDone() }, welcome = false, onCancel = onCancel)
        }
    }
}

private fun isObviousPin(pin: String): Boolean {
    val d = pin.map { it - '0' }
    val allSame = d.all { it == d[0] }
    val ascending = d.zipWithNext().all { (a, b) -> b == a + 1 }
    val descending = d.zipWithNext().all { (a, b) -> b == a - 1 }
    return allSame || ascending || descending
}

// ------------------------------------------------------------------ Teclado de PIN

@Composable
fun PinPad(
    title: String,
    subtitle: String,
    actionLabel: String,
    onSubmit: (String) -> PinFeedback,
    modifier: Modifier = Modifier,
    initialLockedUntil: Long = 0L,
    onBiometric: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val colors = AnimoTheme.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // remember (no rememberSaveable): los dígitos del PIN nunca deben terminar en el estado guardado.
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var mascot by remember { mutableStateOf(Mood.FELIZ) }
    var lockedUntil by remember { mutableLongStateOf(initialLockedUntil) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(lockedUntil) {
        now = System.currentTimeMillis()
        while (now < lockedUntil) {
            delay(250)
            now = System.currentTimeMillis()
        }
        if (lockedUntil != 0L) message = null
    }
    val locked = now < lockedUntil
    val secondsLeft = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)

    fun press(d: Char) {
        if (locked || digits.length >= PIN_LENGTH) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        digits += d
        message = null
        mascot = Mood.FELIZ
    }

    fun submit() {
        if (digits.length < PIN_LENGTH || locked) return
        when (val result = onSubmit(digits)) {
            PinFeedback.Accept -> {
                mascot = Mood.EXCELENTE
                digits = ""
                message = null
            }
            is PinFeedback.Reject -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                mascot = Mood.PREOCUPADO
                message = result.message
                if (result.lockedUntil > 0) lockedUntil = result.lockedUntil
                scope.launch {
                    shake.animateTo(0f, keyframes {
                        durationMillis = 420
                        -18f at 50
                        18f at 120
                        -14f at 190
                        14f at 260
                        -6f at 330
                        0f at 420
                    })
                }
                digits = ""
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        BlobBackground()
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
            val keySize: Dp = if (landscape) {
                minOf(72.dp, (maxHeight - 140.dp) / 4.6f).coerceAtLeast(48.dp)
            } else {
                minOf(80.dp, (maxWidth - 80.dp) / 3.4f, (maxHeight - 330.dp) / 4.9f).coerceAtLeast(52.dp)
            }

            val header: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Bobbing { MoodFace(mascot, Modifier.size(if (landscape) 76.dp else 92.dp), alive = true) }
                        Box(
                            Modifier
                                .size(34.dp)
                                .cartoonSurface(Palette.Sun, colors.outline, CircleShape, 2.dp, 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (mascot == Mood.EXCELENTE) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = Palette.Ink,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    PinDots(digits.length, Modifier.graphicsLayer { translationX = shake.value * density })
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = when {
                            locked -> "Demasiados intentos. Espera $secondsLeft s"
                            else -> message ?: " "
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            val keypad: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Keypad(
                        keySize = keySize,
                        enabled = !locked,
                        onDigit = ::press,
                        onDelete = { if (digits.isNotEmpty()) digits = digits.dropLast(1) },
                        onBiometric = onBiometric,
                    )
                    Spacer(Modifier.height(14.dp))
                    val ready = digits.length == PIN_LENGTH && !locked
                    val pop by animateFloatAsState(
                        if (ready) 1f else 0.94f,
                        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                        label = "ready",
                    )
                    CartoonButton(
                        onClick = ::submit,
                        enabled = ready,
                        minHeight = 60.dp,
                        color = Palette.Pink,
                        modifier = Modifier
                            .width(keySize * 3 + 32.dp)
                            .graphicsLayer { scaleX = pop; scaleY = pop },
                    ) { Text(actionLabel) }
                }
            }

            if (landscape) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { header() }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { keypad() }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Spacer(Modifier.height(if (onCancel != null) 48.dp else 8.dp))
                    header()
                    Spacer(Modifier.height(16.dp))
                    // El teclado queda abajo, en la zona natural del pulgar.
                    keypad()
                }
            }

            if (onCancel != null) {
                IconBubble(
                    Icons.Rounded.Close,
                    contentDescription = "Cancelar",
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int, modifier: Modifier = Modifier) {
    val colors = AnimoTheme.colors
    Row(
        modifier.semantics { contentDescription = "$filled de $PIN_LENGTH dígitos" },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(PIN_LENGTH) { i ->
            val on = i < filled
            val size by animateDpAsState(
                if (on) 24.dp else 20.dp,
                spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium),
                label = "dot$i",
            )
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(size)
                        .cartoonSurface(
                            if (on) Palette.Grape else colors.surface,
                            colors.outline,
                            CircleShape,
                            shadow = if (on) 2.dp else 0.dp,
                            border = 2.5.dp,
                        )
                )
            }
        }
    }
}

@Composable
private fun Keypad(
    keySize: Dp,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onBiometric: (() -> Unit)?,
) {
    val colors = AnimoTheme.colors
    val rows = listOf("123", "456", "789")
    val keyShape = RoundedCornerShape(keySize * 0.34f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { d -> DigitKey(d, keySize, keyShape, enabled) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBiometric != null) {
                CartoonButton(
                    onClick = onBiometric,
                    modifier = Modifier.size(keySize + 4.dp),
                    color = Palette.Mint,
                    shape = keyShape,
                    minHeight = keySize,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Icon(Icons.Rounded.Fingerprint, "Usar huella digital", Modifier.size(keySize * 0.48f)) }
            } else {
                Spacer(Modifier.size(keySize + 4.dp))
            }
            DigitKey('0', keySize, keyShape, enabled) { onDigit('0') }
            CartoonButton(
                onClick = onDelete,
                modifier = Modifier.size(keySize + 4.dp),
                color = colors.surfaceAlt,
                shape = keyShape,
                minHeight = keySize,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Icon(Icons.AutoMirrored.Rounded.Backspace, "Borrar", Modifier.size(keySize * 0.4f)) }
        }
    }
}

@Composable
private fun DigitKey(d: Char, keySize: Dp, shape: RoundedCornerShape, enabled: Boolean, onClick: () -> Unit) {
    CartoonButton(
        onClick = onClick,
        modifier = Modifier.size(keySize + 4.dp),
        color = AnimoTheme.colors.surface,
        shape = shape,
        minHeight = keySize,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(d.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}
