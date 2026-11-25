package com.sabil.qnow.ui.customer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import com.sabil.qnow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sabil.qnow.data.ai.FreeAiBusyException
import com.sabil.qnow.data.ai.FreeAiStylist
import com.sabil.qnow.data.ai.FreeStylist
import com.sabil.qnow.data.ai.GeminiStylist
import com.sabil.qnow.domain.ai.HairStyles
import com.sabil.qnow.domain.ai.StyleOption
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.components.SecondaryButton
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** A style plus the group it came from (men, women, kids, beard, colour). */
typealias Pick = Pair<StyleOption, String>

data class StudioState(
    val photo: Bitmap? = null,
    val loadingPhoto: Boolean = false,
    /** The finished look, or null to show the photo as it is. */
    val result: Bitmap? = null,
    val working: Boolean = false,
    /** The online AI is working (takes ~30 s), not the instant on-phone colour. */
    val workingSlow: Boolean = false,
    val group: String = HairStyles.men.id,
    val hair: Pick? = null,
    val beard: Pick? = null,
    val colour: Pick? = null,
    val consented: Boolean = false,
    /** A style waiting for consent before the photo is sent to Gemini. */
    val askConsent: Pick? = null,
    /** A style waiting for a photo to be added. */
    val pending: Pick? = null,
    /** Set when the automatic AI is unavailable, so we can offer the customer's own Gemini app. */
    val fallback: Pick? = null,
    /** A haircut or beard to open in the customer's own Gemini app (free mode). */
    val handoff: Pick? = null,
    val message: String? = null,
) {
    val picks: List<Pick> get() = listOfNotNull(hair, beard, colour)
}

@HiltViewModel
class AiStyleViewModel @Inject constructor(
    private val gemini: GeminiStylist,
    private val freeAi: FreeAiStylist,
    private val free: FreeStylist,
) : ViewModel() {
    val state = MutableStateFlow(StudioState())
    private val cache = HashMap<String, Bitmap>()
    private var job: Job? = null

    fun group(id: String) = state.update { it.copy(group = id) }
    fun consumeMessage() = state.update { it.copy(message = null) }
    fun say(m: String) = state.update { it.copy(message = m) }
    fun closeFallback() = state.update { it.copy(fallback = null) }
    fun cancelConsent() = state.update { it.copy(askConsent = null) }
    fun handoffDone() = state.update { it.copy(handoff = null) }
    fun waitForPhoto(p: Pick) = state.update { it.copy(pending = p) }

    fun usePhoto(uri: Uri) {
        state.update { it.copy(loadingPhoto = true) }
        viewModelScope.launch {
            runCatching { gemini.load(uri) }
                .onSuccess { b ->
                    cache.clear()
                    val waiting = state.value.pending
                    state.update { it.copy(photo = b, result = null, loadingPhoto = false, pending = null) }
                    if (waiting != null) choose(waiting) else if (state.value.picks.isNotEmpty()) generate()
                }
                .onFailure { state.update { it.copy(loadingPhoto = false, message = "Couldn't open that photo. Try another one.") } }
        }
    }

    fun consent() {
        val p = state.value.askConsent
        state.update { it.copy(consented = true, askConsent = null) }
        p?.let(::choose)
    }

    /** Tapping a style selects it (tap again to remove it) and makes the look straight away. */
    fun choose(p: Pick) {
        val s = state.value
        val (o, g) = p
        // Colours are made on the phone; anything else uses the online AI, so ask once first.
        if (g != HairStyles.colours.id && !s.consented) { state.update { it.copy(askConsent = p) }; return }
        state.update {
            when (g) {
                HairStyles.beard.id -> it.copy(beard = if (it.beard?.first?.id == o.id) null else p)
                HairStyles.colours.id -> it.copy(colour = if (it.colour?.first?.id == o.id) null else p)
                else -> it.copy(hair = if (it.hair?.first?.id == o.id) null else p)
            }
        }
        generate()
    }

    companion object {
        /** True once Google billing is on for QNow: Gemini then makes the looks instead of the free AI. */
        const val AUTO_AI = false
    }

    fun clearLook() {
        job?.cancel()
        state.update { it.copy(hair = null, beard = null, colour = null, result = null, working = false) }
    }

    private fun generate() {
        val s = state.value
        val photo = s.photo ?: return
        val picks = s.picks
        job?.cancel()
        if (picks.isEmpty()) { state.update { it.copy(result = null, working = false) }; return }
        val key = picks.joinToString("+") { it.first.id }
        cache[key]?.let { hit -> state.update { it.copy(result = hit, working = false) }; return }
        val onlyColour = s.colour != null && s.hair == null && s.beard == null
        state.update { it.copy(working = true, workingSlow = !onlyColour) }
        job = viewModelScope.launch {
            val made = runCatching {
                when {
                    onlyColour -> free.startSession(photo).apply { dyeAllHair(s.colour!!.first.id) }.bitmap
                    AUTO_AI -> gemini.restyle(photo, HairStyles.promptFor(picks.map { it.first }))
                    else -> freeAi.restyle(photo, freeAi.instruction(picks.map { it.first.prompt }))
                }
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) android.util.Log.w("QNowLooks", "Look failed", it) }
            ensureActive()
            made.onSuccess { b ->
                cache[key] = b
                state.update { it.copy(result = b, working = false) }
            }.onFailure { e ->
                val why = when (e) {
                    is FreeAiBusyException -> e.message
                    is java.io.IOException -> "No connection to the free AI. Check your internet and try again."
                    else -> "Couldn't make this look. Try a clearer, front-facing photo."
                }
                // Unselect what failed so the screen doesn't look stuck.
                state.update { st ->
                    st.copy(
                        working = false, message = why,
                        hair = if (st.hair?.first?.id in picks.map { it.first.id } && st.result == null) null else st.hair,
                        beard = if (st.beard?.first?.id in picks.map { it.first.id } && st.result == null) null else st.beard,
                        colour = if (onlyColour && st.result == null) null else st.colour,
                    )
                }
            }
        }
    }
}

private val groupEmoji = mapOf("men" to "💇‍♂️", "women" to "💇‍♀️", "kids" to "🧒", "beard" to "🧔", "colour" to "🎨")

/**
 * Look Studio: add your photo, tap any haircut, beard or colour, and see it on you.
 * Haircut, beard and colour can be combined. Photo on one side, every style on the other.
 */
@Composable
fun AiStyleScreen(onFindBarber: () -> Unit, onLiveMirror: () -> Unit = {}, vm: AiStyleViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var geminiMissing by remember { mutableStateOf<Pick?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::usePhoto) }
    val camera = rememberLauncherForActivityResult(TakeSelfie()) { ok -> if (ok) cameraUri?.let(vm::usePhoto) }
    fun openCamera() {
        runCatching { val uri = newCameraUri(context); cameraUri = uri; camera.launch(uri) }
            .onFailure { vm.say("Couldn't open the camera. Use Gallery instead.") }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) openCamera() else vm.say("Camera permission is needed to take a selfie. You can still use Gallery.")
    }
    fun selfie() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) openCamera() else cameraPermission.launch(android.Manifest.permission.CAMERA)
    }
    fun pickPhoto() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun tapStyle(p: Pick) {
        if (s.photo == null) { vm.waitForPhoto(p); vm.say("Add your photo first. Your look appears right after."); pickPhoto() } else vm.choose(p)
    }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(s.handoff) {
        val p = s.handoff ?: return@LaunchedEffect
        vm.handoffDone()
        val b = s.photo ?: return@LaunchedEffect
        // Include the colour picked here too, so Gemini makes the whole look in one go.
        val text = HairStyles.promptFor(listOfNotNull(p.first, s.colour?.first))
        when (GeminiHandoff.openText(context, b, text)) {
            GeminiHandoff.Result.OPENED_GEMINI -> vm.say("Gemini is open with your photo. Paste the instruction and tap send.")
            GeminiHandoff.Result.NEEDS_GEMINI_APP -> geminiMissing = p
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(QNowTheme.extended.heroGradient).statusBarsPadding()
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text("Look Studio", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Add your photo, tap a style, see it on you", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
                Surface(onClick = onLiveMirror, shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AddAPhoto, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Live mirror", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth > maxHeight
                val photo = @Composable { m: Modifier ->
                    PhotoPanel(
                        s, m, onPick = ::pickPhoto, onSelfie = ::selfie, onClear = vm::clearLook,
                        onSave = { b -> vm.say(if (saveToGallery(context, b)) "Saved to your gallery (Pictures/QNow). Show it to your barber!" else "Couldn't save the picture") },
                    )
                }
                val styles = @Composable { m: Modifier -> StylePanel(s, m, onGroup = vm::group, onStyle = ::tapStyle, onFindBarber = onFindBarber) }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        photo(Modifier.weight(1f).fillMaxHeight())
                        styles(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    val styleHeight = maxHeight * 0.48f
                    Column(Modifier.fillMaxSize()) {
                        photo(Modifier.weight(1f).fillMaxWidth())
                        styles(Modifier.height(styleHeight).fillMaxWidth())
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Space.lg))
    }

    s.askConsent?.let { p ->
        AlertDialog(
            onDismissRequest = vm::cancelConsent,
            title = { Text("See \"${p.first.name}\" on you") },
            text = {
                Text(
                    if (AiStyleViewModel.AUTO_AI) {
                        "To make a real-looking result, your photo is sent to Google's Gemini AI, which creates the new look. " +
                            "QNow doesn't keep your photo. Only use photos of yourself, or of your child with their permission."
                    } else {
                        "Haircuts and beards are made by a free online AI (FLUX Kontext on Hugging Face). It takes about 30 seconds.\n\n" +
                            "To do this, your photo is sent to Hugging Face's servers. QNow doesn't keep it. " +
                            "There's a small free limit each day, so if it's busy, try again later.\n\n" +
                            "Only use photos of yourself, or of your child with their permission."
                    },
                )
            },
            confirmButton = { TextButton(onClick = vm::consent) { Text(if (AiStyleViewModel.AUTO_AI) "Continue" else "Agree & try it") } },
            dismissButton = { TextButton(onClick = vm::cancelConsent) { Text("Cancel") } },
        )
    }

    s.fallback?.let { p ->
        AlertDialog(
            onDismissRequest = vm::closeFallback,
            title = { Text("Open in your Gemini app?") },
            text = {
                Text(
                    "The automatic look isn't available right now. You can still see \"${p.first.name}\" for free in your own Gemini app: " +
                        "we open it with your photo and copy the instruction. Just paste and send.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.closeFallback()
                    val b = s.photo ?: return@TextButton
                    when (GeminiHandoff.open(context, b, p.first, p.second)) {
                        GeminiHandoff.Result.OPENED_GEMINI -> vm.say("Instruction copied. Paste it in Gemini and tap send.")
                        GeminiHandoff.Result.NEEDS_GEMINI_APP -> geminiMissing = p
                    }
                }) { Text("Open Gemini") }
            },
            dismissButton = { TextButton(onClick = vm::closeFallback) { Text("Not now") } },
        )
    }

    geminiMissing?.let { p ->
        AlertDialog(
            onDismissRequest = { geminiMissing = null },
            title = { Text("Get the free Gemini app") },
            text = {
                Text(
                    "Best: install Google's free Gemini app. Then your photo and the instruction go in by themselves; you just tap send.\n\n" +
                        "Or use Gemini in the browser, in 4 steps:\n" +
                        "1. Tap + in the Gemini box\n" +
                        "2. Pick your photo (saved in Pictures › QNow)\n" +
                        "3. Long-press the box and tap Paste\n" +
                        "4. Tap send",
                )
            },
            confirmButton = { TextButton(onClick = { geminiMissing = null; GeminiHandoff.installGemini(context) }) { Text("Install Gemini") } },
            dismissButton = {
                TextButton(onClick = {
                    geminiMissing = null
                    val b = s.photo ?: return@TextButton
                    val saved = GeminiHandoff.openInBrowserText(context, b, HairStyles.promptFor(listOfNotNull(p.first, s.colour?.first)))
                    vm.say(if (saved) "Photo saved to Pictures/QNow and instruction copied. Attach the photo in Gemini and paste." else "Instruction copied. Attach your photo in Gemini and paste.")
                }) { Text("Use in browser") }
            },
        )
    }
}

@Composable
private fun PhotoPanel(
    s: StudioState,
    modifier: Modifier,
    onPick: () -> Unit,
    onSelfie: () -> Unit,
    onClear: () -> Unit,
    onSave: (Bitmap) -> Unit,
) {
    var comparing by remember { mutableStateOf(false) }
    Box(
        modifier.padding(Space.sm).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val photo = s.photo
        when {
            s.loadingPhoto -> CircularProgressIndicator()
            photo == null -> Column(Modifier.padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(QNowTheme.extended.heroGradient),
                    contentAlignment = Alignment.Center,
                ) { Text("🙂", style = MaterialTheme.typography.displaySmall) }
                Spacer(Modifier.height(Space.lg))
                Text("Add your photo", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "A clear, front-facing photo works best. Then tap any style to see it on you.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.lg))
                PrimaryButton("Choose photo", onClick = onPick, icon = Icons.Rounded.PhotoLibrary, modifier = Modifier.width(240.dp))
                Spacer(Modifier.height(Space.sm))
                SecondaryButton("Take selfie", onClick = onSelfie, icon = Icons.Rounded.AddAPhoto, modifier = Modifier.width(240.dp))
            }
            else -> {
                val shown = if (comparing) photo else s.result ?: photo
                Image(
                    shown.asImageBitmap(), contentDescription = "Your look", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().pointerInput(s.result) {
                        if (s.result != null) detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false })
                    },
                )
                // What's applied, top-left.
                val label = when {
                    comparing -> "Before"
                    s.result != null -> "After · hold to see before"
                    else -> "Your photo · tap a style"
                }
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.TopStart).padding(Space.md)) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                if (s.picks.isNotEmpty()) {
                    Surface(
                        onClick = onClear, shape = CircleShape, color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(Space.md),
                    ) {
                        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.picks.joinToString(" + ") { it.first.name }, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Close, "Clear look", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (s.working) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(Space.sm))
                            Text("Creating your look…", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(if (s.workingSlow) "Free AI at work: about 30 seconds" else "Just a moment", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    s.result?.let { r -> RoundAction(Icons.Rounded.Download, "Save") { onSave(r) } }
                    RoundAction(Icons.Rounded.PhotoLibrary, "New photo", onPick)
                    RoundAction(Icons.Rounded.AddAPhoto, "Take selfie", onSelfie)
                }
            }
        }
    }
}

@Composable
private fun StylePanel(s: StudioState, modifier: Modifier, onGroup: (String) -> Unit, onStyle: (Pick) -> Unit, onFindBarber: () -> Unit) {
    Column(modifier.padding(vertical = Space.sm)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            HairStyles.groups.forEach { g ->
                FilterChip(
                    selected = s.group == g.id, onClick = { onGroup(g.id) }, shape = CircleShape,
                    label = { Text("${groupEmoji[g.id].orEmpty()}  ${g.title}", style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        val group = HairStyles.groups.first { it.id == s.group }
        val instant = group.id == HairStyles.colours.id
        Surface(
            shape = CircleShape,
            color = if (instant) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.padding(start = Space.md, end = Space.md, top = Space.sm),
        ) {
            Text(
                if (group.id == HairStyles.colours.id) "⚡ Tap a colour: it appears on your photo instantly, free"
                else "✨ Tap a style: free AI makes it on your photo in about 30 seconds",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        val selectedIds = s.picks.map { it.first.id }.toSet()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            items(group.options, key = { it.id }) { o ->
                StyleTile(o, group.id, selected = o.id in selectedIds, busy = s.working) { onStyle(o to group.id) }
            }
        }
        Row(Modifier.padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Tip: pick a colour first, then a haircut or beard: the AI makes them together.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onFindBarber) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Find a barber")
            }
        }
    }
}

@Composable
private fun StyleTile(o: StyleOption, group: String, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val swatch = if (group == HairStyles.colours.id) FreeStylist.dye(o.id) else null
    Surface(
        onClick = onClick, enabled = !busy,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(
                    if (swatch != null) Brush.linearGradient(listOf(Color(swatch), Color(swatch).copy(alpha = 0.7f), Color.Black.copy(alpha = 0.5f)))
                    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.tertiaryContainer)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (swatch == null) Text(groupEmoji[group].orEmpty(), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(o.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(o.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
    }
}

/**
 * Opens the camera app on the FRONT camera. Android has no single standard for this, so we send
 * every hint the common camera apps (Google, Samsung, Xiaomi, MediaTek-based tablets) understand.
 */
private class TakeSelfie : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): android.content.Intent =
        super.createIntent(context, input).apply {
            putExtra("android.intent.extras.CAMERA_FACING", 1)
            putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            putExtra("camerafacing", "front")
            putExtra("previous_mode", "front")
            putExtra("default_camera", "1")
            putExtra("com.google.assistant.extra.USE_FRONT_CAMERA", true)
        }
}

private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "ai").apply { mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "selfie.jpg"))
}

private fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "QNow-look-${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QNow")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    true
}.getOrDefault(false)