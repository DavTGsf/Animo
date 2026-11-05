package com.example.animo.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animo.security.Biometrics
import com.example.animo.ui.components.BlobBackground
import com.example.animo.ui.components.CartoonDialog
import com.example.animo.ui.components.ConfettiBurst
import com.example.animo.ui.components.cartoonSurface
import com.example.animo.ui.components.inkOn
import com.example.animo.ui.screens.CalendarScreen
import com.example.animo.ui.screens.ChangePinFlow
import com.example.animo.ui.screens.CreatePinFlow
import com.example.animo.ui.screens.EditorScreen
import com.example.animo.ui.screens.InsightsScreen
import com.example.animo.ui.screens.SettingsScreen
import com.example.animo.ui.screens.TodayScreen
import com.example.animo.ui.screens.UnlockScreen
import com.example.animo.ui.theme.AnimoTheme
import com.example.animo.ui.theme.Palette

enum class Tab(val label: String, val icon: ImageVector, val color: Color) {
    HOY("Hoy", Icons.Rounded.WbSunny, Palette.Sun),
    CALENDARIO("Calendario", Icons.Rounded.CalendarMonth, Palette.Sky),
    ANALISIS("Análisis", Icons.Rounded.Insights, Palette.Mint),
    AJUSTES("Ajustes", Icons.Rounded.Settings, Palette.Pink),
}

@Composable
fun AnimoApp(vm: AppViewModel) {
    val lock by vm.lockState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val savedTick by vm.savedTick.collectAsStateWithLifecycle()
    val offerBiometric by vm.offerBiometric.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOY) }
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            // Cualquier toque reinicia el temporizador de bloqueo por inactividad (sin consumir el evento).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        vm.touch()
                    }
                }
            }
    ) {
        AnimatedContent(
            targetState = lock,
            transitionSpec = {
                (fadeIn(tween(350)) + scaleIn(initialScale = 0.94f, animationSpec = tween(350))) togetherWith
                    (fadeOut(tween(250)) + scaleOut(targetScale = 1.04f, animationSpec = tween(250)))
            },
            label = "lock",
        ) { state ->
            when (state) {
                LockState.Setup -> CreatePinFlow(onCreated = vm::createPin, welcome = true)
                LockState.Locked -> UnlockScreen(vm, settings)
                LockState.Unlocked -> MainShell(vm, tab) { tab = it }
            }
        }
        ConfettiBurst(savedTick)
    }

    if (offerBiometric && lock == LockState.Unlocked) {
        CartoonDialog(
            title = "¿Usar tu huella?",
            message = "Desbloquea Ánimo más rápido con tu huella digital. Tu PIN seguirá funcionando.",
            confirmLabel = "Activar",
            dismissLabel = "Ahora no",
            confirmColor = Palette.Mint,
            icon = Icons.Rounded.Fingerprint,
            onConfirm = {
                vm.dismissBiometricOffer()
                Biometrics.authenticate(
                    context, "Activar huella", "Confirma tu huella para usarla en Ánimo",
                    onSuccess = { vm.setBiometric(true) },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                )
            },
            onDismiss = vm::dismissBiometricOffer,
        )
    }
}

@Composable
private fun MainShell(vm: AppViewModel, tab: Tab, onTab: (Tab) -> Unit) {
    val journal by vm.journal.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var changingPin by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Conserva el último borrador para que la animación de salida del editor tenga contenido.
    val lastDraft = remember { mutableStateOf(draft) }
    if (draft != null) lastDraft.value = draft

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        // En pantallas anchas el contenido se centra con un ancho máximo legible.
        val sidePad: Dp = if (maxWidth > 820.dp) (maxWidth - (if (wide) 100.dp else 0.dp) - 720.dp) / 2 else 16.dp
        val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp
        val contentPadding = PaddingValues(start = sidePad, end = sidePad, top = topPad, bottom = 24.dp)

        BlobBackground(tint = null)

        val content: @Composable (Modifier) -> Unit = { m ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(300)) { dir * it / 6 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(250)) { -dir * it / 6 } + fadeOut(tween(200)))
                },
                modifier = m,
                label = "tabs",
            ) { t ->
                when (t) {
                    Tab.HOY -> TodayScreen(
                        journal = journal,
                        today = today,
                        contentPadding = contentPadding,
                        onPickMood = { vm.openEditor(today, it) },
                        onEdit = { vm.openEditor(it) },
                        onDelete = vm::deleteEntry,
                        onSeeInsights = { onTab(Tab.ANALISIS) },
                    )
                    Tab.CALENDARIO -> CalendarScreen(
                        journal = journal,
                        today = today,
                        allowPastDays = settings.allowPastDays,
                        contentPadding = contentPadding,
                        onCreateOrEdit = { vm.openEditor(it) },
                        onDelete = vm::deleteEntry,
                    )
                    Tab.ANALISIS -> InsightsScreen(journal, today, contentPadding)
                    Tab.AJUSTES -> SettingsScreen(
                        settings = settings,
                        customActivities = journal.customActivities,
                        biometricAvailable = vm.biometricAvailable,
                        contentPadding = contentPadding,
                        onChangePin = { changingPin = true },
                        onBiometric = vm::setBiometric,
                        onAutoLock = vm::setAutoLock,
                        onSecureScreen = vm::setSecureScreen,
                        onMaxChars = vm::setMaxChars,
                        onAllowPast = vm::setAllowPastDays,
                        onAddActivity = vm::addCustomActivity,
                        onRemoveActivity = vm::removeCustomActivity,
                        onLockNow = vm::lockNow,
                        onWipe = vm::wipeJournal,
                        onLoadDemo = vm::loadDemoData,
                        )
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                NavRail(tab, onTab)
                content(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxWidth())
                BottomBar(tab, onTab)
            }
        }

        if (tab != Tab.HOY && draft == null && !changingPin) {
            BackHandler { onTab(Tab.HOY) }
        }

        AnimatedVisibility(
            visible = draft != null,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(260)),
        ) {
            lastDraft.value?.let { d ->
                EditorScreen(
                    draft = d,
                    activities = journal.allActivities,
                    maxChars = settings.maxReflectionChars,
                    onMood = vm::setMood,
                    onEnergy = vm::setEnergy,
                    onToggleActivity = vm::toggleActivity,
                    onAddActivity = vm::addCustomActivity,
                    onReflection = vm::setReflection,
                    onSave = vm::saveDraft,
                    onClose = vm::closeEditor,
                )
            }
        }

        AnimatedVisibility(
            visible = changingPin,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            BackHandler { changingPin = false }
            ChangePinFlow(
                vm = vm,
                onDone = {
                    changingPin = false
                    Toast.makeText(context, "PIN actualizado", Toast.LENGTH_SHORT).show()
                },
                onCancel = { changingPin = false },
            )
        }
    }
}

// ------------------------------------------------------------------ Navegación

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    val colors = AnimoTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 4.dp, bottom = 4.dp)
                .cartoonSurface(colors.surface, colors.outline, RoundedCornerShape(28.dp), 4.dp)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { t -> NavItem(t, t == tab, { onTab(t) }, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun NavRail(tab: Tab, onTab: (Tab) -> Unit) {
    val colors = AnimoTheme.colors
    Box(
        Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .padding(12.dp)
    ) {
        Column(
            Modifier
                .width(92.dp)
                .fillMaxHeight()
                .padding(end = 4.dp, bottom = 4.dp)
                .cartoonSurface(colors.surface, colors.outline, RoundedCornerShape(28.dp), 4.dp)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Tab.entries.forEach { t -> NavItem(t, t == tab, { onTab(t) }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = AnimoTheme.colors
    val fill by animateColorAsState(if (selected) tab.color else Color.Transparent, label = "navFill")
    val iconScale by animateFloatAsState(
        if (selected) 1.18f else 1f,
        spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium),
        label = "navIcon",
    )
    val tilt by animateFloatAsState(
        if (selected) -8f else 0f,
        spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessLow),
        label = "navTilt",
    )
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .defaultMinSize(minHeight = 60.dp)
            .clip(shape)
            .then(if (selected) Modifier.cartoonSurface(fill, colors.outline, shape, 0.dp, 2.dp) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = if (selected) inkOn(tab.color) else colors.textMuted,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; rotationZ = tilt },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) inkOn(tab.color) else colors.textMuted,
            maxLines = 1,
        )
    }
}
