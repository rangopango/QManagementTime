package com.sabil.qnow.ui.customer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sabil.qnow.data.ai.FreeStylist
import com.sabil.qnow.data.ai.GeminiStylist
import com.sabil.qnow.domain.ai.HairStyles
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.components.SecondaryButton
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class StudioState(
    val photo: Bitmap? = null,
    val session: FreeStylist.LookSession? = null,
    val preparing: Boolean = false,
    val tool: FreeStylist.Tool = FreeStylist.Tool.DYE,
    val dyeId: String = "burgundy",
    val brush: Float = 0.04f,       // brush radius as a share of photo width
    val ideasGroup: String = HairStyles.men.id,
    val consented: Boolean = false,
    /** First-time tip on the photo; hidden once the user paints or closes it. */
    val tipSeen: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AiStyleViewModel @Inject constructor(
    private val loader: GeminiStylist,
    private val free: FreeStylist,
) : ViewModel() {
    val state = MutableStateFlow(StudioState())

    fun consent() = state.update { it.copy(consented = true) }
    fun tipSeen() = state.update { if (it.tipSeen) it else it.copy(tipSeen = true) }
    fun tool(t: FreeStylist.Tool) = state.update { it.copy(tool = t) }
    fun dye(id: String) = state.update { it.copy(dyeId = id) }
    fun brush(v: Float) = state.update { it.copy(brush = v) }
    fun ideas(id: String) = state.update { it.copy(ideasGroup = id) }
    fun consumeMessage() = state.update { it.copy(message = null) }
    fun say(m: String) = state.update { it.copy(message = m) }

    fun usePhoto(uri: Uri) {
        state.update { it.copy(preparing = true, session = null) }
        viewModelScope.launch {
            runCatching {
                val b = loader.load(uri)
                b to free.startSession(b)
            }.onSuccess { (b, s) -> state.update { it.copy(photo = b, session = s, preparing = false) } }
                .onFailure { state.update { it.copy(preparing = false, message = "Couldn't open that photo. Try another one.") } }
        }
    }

    fun dyeAll() {
        val s = state.value
        s.session?.dyeAllHair(s.dyeId)
    }
}

private data class ToolInfo(val tool: FreeStylist.Tool, val label: String, val icon: ImageVector, val hint: String)

private val tools = listOf(
    ToolInfo(FreeStylist.Tool.DYE, "Dye", Icons.Rounded.Palette, "Pick a colour, then tap \"Colour all hair\" or brush colour onto parts of your hair."),
    ToolInfo(FreeStylist.Tool.TRIM, "Trim", Icons.Rounded.ContentCut, "Brush over hair you want gone. The wall or skin behind it is filled in."),
    ToolInfo(FreeStylist.Tool.GROW, "Grow", Icons.Rounded.Grass, "Brush where you want more hair. It copies your own hair texture."),
    ToolInfo(FreeStylist.Tool.SHAVE, "Shave", Icons.Rounded.FaceRetouchingNatural, "Brush over beard or moustache to see a clean shave."),
    ToolInfo(FreeStylist.Tool.BEARD, "Beard", Icons.Rounded.Brush, "Brush on stubble or a beard. Brush again to make it thicker."),
    ToolInfo(FreeStylist.Tool.RESTORE, "Restore", Icons.Rounded.Restore, "Brush to bring back the original photo in that spot."),
)

/**
 * Look Studio: edit your own photo by hand to try colours, cuts and beards. Free and private.
 * Laid out like a photo editor: the photo fills the middle and the tools stay pinned at the
 * bottom, so the page never scrolls and a finger on the photo always paints.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AiStyleScreen(onFindBarber: () -> Unit, onLiveMirror: () -> Unit = {}, vm: AiStyleViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var comparing by remember { mutableStateOf(false) }
    var showIdeas by remember { mutableStateOf(false) }
    var ideaShown by remember { mutableStateOf<Pair<com.sabil.qnow.domain.ai.StyleOption, String>?>(null) }
    // Where the photo is drawn on screen, so touches map back to photo pixels.
    val frame = remember { floatArrayOf(0f, 0f, 1f) } // left, top, scale

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::usePhoto) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let(vm::usePhoto) }
    // Android only lets an app with the camera permission open the camera app, so ask first.
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
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            // ---------------- Top bar
            Row(
                Modifier.fillMaxWidth().background(QNowTheme.extended.heroGradient).statusBarsPadding()
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
                Spacer(Modifier.width(Space.sm))
                Text("Look Studio", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                TopPill("Ideas", Icons.Rounded.Image) { showIdeas = true }
                Spacer(Modifier.width(Space.sm))
                TopPill("Live mirror", Icons.Rounded.AddAPhoto, onClick = onLiveMirror)
            }

            // ---------------- Photo (fills the free space)
            val session = s.session
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(Space.sm).clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (session != null) {
                    val brushPx = s.brush * session.w
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(session, s.tool, s.dyeId, s.brush) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                // Ghost-touch guard: a real finger stays down for a moment and is
                                // alone; phantom touches flicker for milliseconds, often several at
                                // once. Ignore anything that isn't one steady finger.
                                var steady = false
                                var startPos = down.position
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val pressed = ev.changes.filter { it.pressed }
                                    if (pressed.size != 1) break
                                    startPos = pressed[0].position
                                    if (pressed[0].uptimeMillis - down.uptimeMillis >= 90) { steady = true; break }
                                }
                                if (!steady) return@awaitEachGesture
                                vm.tipSeen()
                                fun toPhoto(o: Offset) = Offset((o.x - frame[0]) / frame[2], (o.y - frame[1]) / frame[2])
                                val dye = FreeStylist.dye(s.dyeId)
                                session.beginStroke()
                                var last = toPhoto(startPos)
                                session.stamp(s.tool, last.x, last.y, brushPx, dye)
                                version++
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    if (ev.changes.count { it.pressed } > 1) break   // a second finger is almost always a ghost
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    val p = toPhoto(ch.position)
                                    // A dab every third of a brush width keeps strokes smooth.
                                    val dist = hypot(p.x - last.x, p.y - last.y)
                                    val steps = max(1, (dist / (brushPx / 3f)).toInt())
                                    for (k in 1..steps) {
                                        val t = k / steps.toFloat()
                                        session.stamp(s.tool, last.x + (p.x - last.x) * t, last.y + (p.y - last.y) * t, brushPx, dye)
                                    }
                                    last = p
                                    version++
                                    ch.consume()
                                }
                            }
                        },
                    ) {
                        // Reading version here makes this canvas redraw after every edit.
                        if (version < 0) return@Canvas
                        val sc = min(size.width / session.w, size.height / session.h)
                        val left = (size.width - session.w * sc) / 2
                        val top = (size.height - session.h * sc) / 2
                        frame[0] = left; frame[1] = top; frame[2] = sc
                        val bmp = if (comparing) s.photo ?: session.bitmap else session.bitmap
                        drawIntoCanvas {
                            it.nativeCanvas.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + session.w * sc, top + session.h * sc), null)
                        }
                        // Brush size preview in the corner.
                        drawCircle(Color.White, radius = brushPx * sc, center = Offset(size.width - brushPx * sc - 16f, brushPx * sc + 16f), style = Stroke(3f))
                    }
                    Surface(
                        shape = CircleShape, color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.TopStart).padding(Space.md)
                            .pointerInput(Unit) { detectTapGestures(onPress = { comparing = true; version++; tryAwaitRelease(); comparing = false; version++ }) },
                    ) {
                        Text(if (comparing) "Original" else "Hold to compare", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                    if (!s.tipSeen) {
                        Surface(
                            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.inverseSurface,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(Space.lg).padding(bottom = 48.dp),
                        ) {
                            Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                                Text("👆", style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.width(Space.sm))
                                Text(
                                    "Pick a tool below, then brush on your hair with one finger",
                                    color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                TextButton(onClick = vm::tipSeen) { Text("Got it", color = MaterialTheme.colorScheme.inversePrimary) }
                            }
                        }
                    }
                    Row(Modifier.align(Alignment.BottomEnd).padding(Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        RoundAction(Icons.Rounded.PhotoLibrary, "New photo") { pickPhoto() }
                        RoundAction(Icons.Rounded.AddAPhoto, "Take selfie") { selfie() }
                    }
                } else {
                    if (s.preparing) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(Space.sm))
                            Text("Getting your photo ready…", style = MaterialTheme.typography.titleMedium)
                            Text("Finding your hair and face", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        StartScreen(
                            onPhoto = { pickPhoto() }, onSelfie = { selfie() }, onLiveMirror = onLiveMirror,
                            onTool = { vm.tool(it); pickPhoto() },
                            onColour = { vm.dye(it); vm.tool(FreeStylist.Tool.DYE); pickPhoto() },
                            onIdea = { o, g -> ideaShown = o to g },
                        )
                    }
                }
            }

            // ---------------- Pinned controls
            if (session != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(vertical = Space.sm)) {
                        // Tool-specific row: colours for Dye, a short hint for the others.
                        if (s.tool == FreeStylist.Tool.DYE) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.md),
                                horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(onClick = { vm.dyeAll(); version++ }, shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                    Text("Colour all hair", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                                }
                                HairStyles.colours.options.forEach { o ->
                                    val c = FreeStylist.dye(o.id) ?: return@forEach
                                    val selected = s.dyeId == o.id
                                    Box(
                                        Modifier.size(if (selected) 40.dp else 34.dp).clip(CircleShape).background(Color(c))
                                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                            .clickable { vm.dye(o.id) },
                                    )
                                }
                            }
                            Text(
                                "Colour: " + (HairStyles.colours.options.firstOrNull { it.id == s.dyeId }?.name ?: "") + "  ·  brush only changes hair",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Space.lg, vertical = 2.dp),
                            )
                        } else {
                            Text(
                                tools.first { it.tool == s.tool }.hint, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                            )
                        }

                        // Brush size + actions.
                        Row(Modifier.padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Brush, "Brush size", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(value = s.brush, onValueChange = vm::brush, valueRange = 0.01f..0.1f, modifier = Modifier.weight(1f).padding(horizontal = Space.sm))
                            RoundAction(Icons.AutoMirrored.Rounded.Undo, "Undo") { session.undo(); version++ }
                            RoundAction(Icons.Rounded.RestartAlt, "Start over") { session.reset(); version++ }
                            RoundAction(Icons.Rounded.Download, "Save") {
                                vm.say(if (saveToGallery(context, session.bitmap)) "Saved to your gallery (Pictures/QNow). Show it to your barber!" else "Couldn't save the picture")
                            }
                        }

                        // Tools, always visible.
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.md),
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            tools.forEach { t ->
                                ToolButton(t, selected = s.tool == t.tool) { vm.tool(t.tool) }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }

    if (showIdeas) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showIdeas = false }) {
            Column(Modifier.padding(bottom = Space.xl)) {
                Text("Style ideas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.screen))
                Text(
                    "Tap a style to see what to ask your barber and how to try it here.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.xs),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    HairStyles.groups.forEach { g -> FilterChip(s.ideasGroup == g.id, onClick = { vm.ideas(g.id) }, label = { Text(g.title) }, shape = CircleShape) }
                }
                Spacer(Modifier.height(Space.md))
                val group = HairStyles.groups.first { it.id == s.ideasGroup }
                FlowRow(
                    Modifier.padding(horizontal = Space.screen).heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    group.options.forEach { o ->
                        QCard(onClick = { ideaShown = o to group.id }) {
                            Column(Modifier.width(160.dp).padding(Space.md)) {
                                Text(o.name, style = MaterialTheme.typography.titleSmall)
                                Text(o.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.md))
                SecondaryButton("Find a barber for this look", onClick = { showIdeas = false; onFindBarber() }, icon = Icons.Rounded.Search, modifier = Modifier.padding(horizontal = Space.screen).fillMaxWidth())
            }
        }
    }

    ideaShown?.let { (o, group) ->
        AlertDialog(
            onDismissRequest = { ideaShown = null },
            title = { Text(o.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(o.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("What to tell your barber", style = MaterialTheme.typography.titleSmall)
                    Text(barberAsk(o, group), style = MaterialTheme.typography.bodyMedium)
                    Text("Try it in the studio", style = MaterialTheme.typography.titleSmall)
                    Text(studioTip(o, group), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (group == "colour") { vm.dye(o.id); vm.tool(FreeStylist.Tool.DYE) }
                    else vm.tool(if (group == "beard") (if (o.id == "clean") FreeStylist.Tool.SHAVE else FreeStylist.Tool.BEARD) else FreeStylist.Tool.TRIM)
                    ideaShown = null; showIdeas = false
                }) { Text(if (group == "colour") "Use this colour" else "Pick the right tool") }
            },
            dismissButton = { TextButton(onClick = { ideaShown = null }) { Text("Close") } },
        )
    }

}

/** Colours for the tool tiles on the start screen. */
private val toolColours = mapOf(
    FreeStylist.Tool.DYE to listOf(Color(0xFFB0306A), Color(0xFF6B1F7A)),
    FreeStylist.Tool.TRIM to listOf(Color(0xFF7C63FF), Color(0xFF3A22C4)),
    FreeStylist.Tool.GROW to listOf(Color(0xFF1FB57E), Color(0xFF0B7A5C)),
    FreeStylist.Tool.SHAVE to listOf(Color(0xFF2E8BF0), Color(0xFF1A4FB0)),
    FreeStylist.Tool.BEARD to listOf(Color(0xFFFF8A5B), Color(0xFFD9502E)),
    FreeStylist.Tool.RESTORE to listOf(Color(0xFF8A8F9E), Color(0xFF555A68)),
)

private val toolBlurb = mapOf(
    FreeStylist.Tool.DYE to "16 colours, real-looking",
    FreeStylist.Tool.TRIM to "Go shorter, try a fade",
    FreeStylist.Tool.GROW to "Add volume or length",
    FreeStylist.Tool.SHAVE to "See yourself clean-shaven",
    FreeStylist.Tool.BEARD to "Stubble to full beard",
    FreeStylist.Tool.RESTORE to "Undo any spot",
)

/** Start screen: shows everything Look Studio can do, so it's clear what to try first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartScreen(
    onPhoto: () -> Unit,
    onSelfie: () -> Unit,
    onLiveMirror: () -> Unit,
    onTool: (FreeStylist.Tool) -> Unit,
    onColour: (String) -> Unit,
    onIdea: (com.sabil.qnow.domain.ai.StyleOption, String) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.md)) {
        // Hero
        Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(QNowTheme.extended.heroGradient)) {
            Box(Modifier.size(180.dp).align(Alignment.TopEnd).padding(start = 40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
            Column(Modifier.padding(Space.xl)) {
                Text("✨ YOUR NEXT LOOK", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(Space.xs))
                Text("See it on you\nbefore the chair", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(Space.xs))
                Text("Dye, trim, grow or shave, right on your own photo. Free and private.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(Space.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Surface(onClick = onPhoto, shape = CircleShape, color = Color.White, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Space.xs))
                            Text("Choose photo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Surface(onClick = onSelfie, shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AddAPhoto, null, tint = Color.White)
                            Spacer(Modifier.width(Space.xs))
                            Text("Take selfie", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Tools
        Spacer(Modifier.height(Space.xl))
        Text("What you can do", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.xs))
        Spacer(Modifier.height(Space.sm))
        tools.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                pair.forEach { t ->
                    val c = toolColours.getValue(t.tool)
                    Surface(onClick = { onTool(t.tool) }, shape = MaterialTheme.shapes.large, color = Color.Transparent, modifier = Modifier.weight(1f)) {
                        Row(
                            Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(c)).padding(Space.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Icon(t.icon, null, tint = Color.White)
                            }
                            Spacer(Modifier.width(Space.md))
                            Column {
                                Text(t.label, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Text(toolBlurb[t.tool].orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                    }
                }
            }
        }

        // Colours
        Spacer(Modifier.height(Space.xl))
        Text("16 colours to try", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.xs))
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            HairStyles.colours.options.forEach { o ->
                val c = FreeStylist.dye(o.id) ?: return@forEach
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable { onColour(o.id) }) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(c), Color(c).copy(alpha = 0.7f), Color.Black.copy(alpha = 0.6f))))
                            .border(2.dp, Color.White, CircleShape),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(o.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }

        // Live mirror
        Spacer(Modifier.height(Space.xl))
        Surface(onClick = onLiveMirror, shape = MaterialTheme.shapes.extraLarge, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.background(QNowTheme.extended.accentGradient).padding(Space.xl), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LIVE", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                    Text("Live mirror", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text("Point the camera at yourself and swipe through hair colours live", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                }
                Spacer(Modifier.width(Space.md))
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AddAPhoto, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        // Trending looks
        Spacer(Modifier.height(Space.xl))
        Text("Trending looks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.xs))
        Text("Tap for what to ask your barber", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.xs))
        Spacer(Modifier.height(Space.sm))
        val trending = listOf(
            HairStyles.men to "low_fade", HairStyles.men to "textured_fringe", HairStyles.women to "butterfly",
            HairStyles.women to "curtain_bangs", HairStyles.men to "modern_mullet", HairStyles.colours to "balayage",
            HairStyles.beard to "short_boxed", HairStyles.women to "wolf", HairStyles.kids to "kids_fade",
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            trending.forEach { (g, id) ->
                val o = g.options.firstOrNull { it.id == id } ?: return@forEach
                QCard(onClick = { onIdea(o, g.id) }) {
                    Column(Modifier.width(150.dp).padding(Space.md)) {
                        Text(g.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(o.name, style = MaterialTheme.typography.titleSmall)
                        Text(o.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.lg))
        Text(
            "🔒 Private: your photo stays on your phone. Only use photos of yourself, or your child with their permission.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = Space.lg),
        )
    }
}
@Composable
private fun TopPill(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun ToolButton(t: ToolInfo, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.width(64.dp).clip(MaterialTheme.shapes.medium).background(bg).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(t.icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(t.label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

/** Plain words a customer can say to their barber, from the style's description. */
private fun barberAsk(o: com.sabil.qnow.domain.ai.StyleOption, group: String): String {
    val detail = o.prompt.substringAfter(": ", o.prompt).substringAfter("to ", "").ifBlank { o.prompt.substringAfter(": ", o.prompt) }
        .removeSuffix(". Keep the current haircut shape.").removeSuffix(". Do not change the hair on the head.").removeSuffix(".")
    return when (group) {
        "colour" -> "\"I'd like my hair coloured $detail.\" Ask how many sessions it needs and how to care for it."
        "beard" -> "\"Could you give me ${o.name.lowercase()}: ${o.note.lowercase()}.\""
        else -> "\"I'd like a ${o.name.lowercase()}: $detail.\" Show your saved Look Studio photo too."
    }
}

/** Which Look Studio tool recreates the style on your own photo. */
private fun studioTip(o: com.sabil.qnow.domain.ai.StyleOption, group: String): String = when {
    group == "colour" -> "Choose Dye, pick ${o.name}, then tap Colour all hair. Brush with Restore to keep some areas natural."
    group == "beard" && o.id == "clean" -> "Choose Shave and brush over your beard and moustache."
    group == "beard" -> "Choose Beard and brush the shape (${o.note.lowercase()}). Use Shave to tidy the edges."
    o.note.contains("long", true) || o.note.contains("shoulder", true) -> "Choose Grow and brush down past your ears to add length. Trim the ends to shape it."
    else -> "Choose Trim and brush away hair outside the shape (${o.note.lowercase()}). Use Grow to fill gaps."
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
