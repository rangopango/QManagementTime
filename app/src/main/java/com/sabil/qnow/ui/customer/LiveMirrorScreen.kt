package com.sabil.qnow.ui.customer

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.sabil.qnow.data.ai.FreeStylist
import com.sabil.qnow.domain.ai.HairStyles
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class LiveMirrorViewModel @Inject constructor(val stylist: FreeStylist) : ViewModel()

/**
 * Live mirror: the front camera with hair recoloured in real time, like a camera filter.
 * Frames are processed on the phone and never stored or uploaded unless you tap the shutter.
 */
@Composable
fun LiveMirrorScreen(onClose: () -> Unit, vm: LiveMirrorViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            LiveView(vm.stylist)
        } else {
            Column(Modifier.align(Alignment.Center).padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Allow the camera to use the live mirror", color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.md))
                PrimaryButton("Allow camera", onClick = { permission.launch(Manifest.permission.CAMERA) })
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(Space.sm)) {
            Icon(Icons.Rounded.Close, "Close", tint = Color.White)
        }
    }
}

@Composable
private fun LiveView(stylist: FreeStylist) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var selected by remember { mutableStateOf<String?>("burgundy") }
    val colourRef = remember { AtomicReference<String?>("burgundy") }
    var flash by remember { mutableStateOf(false) }
    val shutterScale by animateFloatAsState(if (flash) 0.85f else 1f, label = "shutter")

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            provider = future.get()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(480, 640), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    ).build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                runCatching {
                    val raw = proxy.toBitmap()
                    val rotation = proxy.imageInfo.rotationDegrees
                    // Upright and mirrored (like a real mirror), and small enough to stay smooth.
                    val s = 480f / max(raw.width, raw.height)
                    val m = Matrix().apply { postRotate(rotation.toFloat()); postScale(-s, s) }
                    val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                    frame = stylist.liveFrame(upright, colourRef.get())
                }
                proxy.close()
            }
            runCatching {
                provider?.unbindAll()
                provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        val f = frame
        if (f != null) {
            Image(f.asImageBitmap(), "Live mirror", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(Space.sm))
                Text("Starting camera…", color = Color.White)
            }
        }

        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = Space.md)) {
            Text(
                selected?.let { id -> HairStyles.colours.options.firstOrNull { it.id == id }?.name } ?: "Original",
                color = Color.White, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f)).navigationBarsPadding().padding(vertical = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyRow(contentPadding = PaddingValues(horizontal = Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                item {
                    Swatch(null, "Original", selected == null) { selected = null; colourRef.set(null) }
                }
                items(HairStyles.colours.options) { o ->
                    Swatch(FreeStylist.dye(o.id), o.name, selected == o.id) { selected = o.id; colourRef.set(o.id) }
                }
            }
            Spacer(Modifier.height(Space.md))
            // Shutter: saves what you see to your gallery.
            Box(
                Modifier.size(72.dp).scale(shutterScale).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))
                    .border(4.dp, Color.White, CircleShape)
                    .clickable {
                        val shot = frame ?: return@clickable
                        flash = true
                        val ok = saveShot(context, shot)
                        scope.launch {
                            kotlinx.coroutines.delay(150); flash = false
                            snackbar.showSnackbar(if (ok) "Saved to your gallery (Pictures/QNow)" else "Couldn't save")
                        }
                    },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun Swatch(colour: Int?, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(if (selected) 50.dp else 42.dp).clip(CircleShape)
                .background(colour?.let { Color(it) } ?: Color.DarkGray)
                .border(if (selected) 3.dp else 1.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (colour == null) Icon(Icons.Rounded.DoNotDisturbOn, null, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

private fun saveShot(context: Context, bitmap: Bitmap): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "QNow-mirror-${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QNow")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    true
}.getOrDefault(false)
