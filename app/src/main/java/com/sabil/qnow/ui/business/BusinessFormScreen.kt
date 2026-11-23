package com.sabil.qnow.ui.business

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.sabil.qnow.domain.model.BusinessCategory
import com.sabil.qnow.domain.model.DayHours
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.ui.components.Pill
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.components.ShimmerList
import com.sabil.qnow.ui.components.formatTime
import com.sabil.qnow.ui.theme.Space

@Composable
fun BusinessFormScreen(onClose: (() -> Unit)?, onSaved: () -> Unit, vm: BusinessFormViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.saved.collect { onSaved() } }
    BackHandler(enabled = s.step.ordinal > 0) { vm.back() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        Row(Modifier.padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            when {
                s.step.ordinal > 0 -> IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                onClose != null -> IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
                else -> Spacer(Modifier.width(Space.lg))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (s.editing) "Edit your business" else "Set up your business",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(s.step.title, style = MaterialTheme.typography.titleLarge)
            }
            Text("${s.step.ordinal + 1}/${FormStep.entries.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = Space.lg))
        }
        val progress by animateFloatAsState((s.step.ordinal + 1f) / FormStep.entries.size, label = "progress")
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen).height(6.dp).clip(CircleShape),
            drawStopIndicator = {},
        )
        if (s.editing) StepTabs(s.step, vm::goTo)

        if (s.loading) {
            ShimmerList(4, modifier = Modifier.padding(Space.screen))
            return@Column
        }

        AnimatedContent(
            targetState = s.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it else it } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { step ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = step != FormStep.LOCATION)
                    .padding(Space.screen),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                when (step) {
                    FormStep.OWNER -> OwnerStep(s, vm)
                    FormStep.SHOP -> ShopStep(s, vm)
                    FormStep.LOCATION -> LocationStep(s, vm)
                    FormStep.SERVICES -> ServicesStep(s, vm)
                    FormStep.HOURS -> HoursStep(s, vm)
                    FormStep.CAPACITY -> CapacityStep(s, vm)
                }
            }
        }

        Column(Modifier.padding(Space.screen)) {
            s.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Space.sm))
            }
            val last = s.step == FormStep.entries.last()
            PrimaryButton(
                text = when {
                    s.editing -> "Save changes"
                    last -> "Publish my business"
                    else -> "Continue"
                },
                onClick = { if (s.editing) vm.save() else vm.next() },
                loading = s.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StepTabs(current: FormStep, onSelect: (FormStep) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        FormStep.entries.forEach { step ->
            FilterChip(selected = step == current, onClick = { onSelect(step) }, label = { Text(step.title) }, shape = CircleShape)
        }
    }
}


@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text, supporting: String? = null, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier,
    )
}

@Composable
private fun OwnerStep(s: BusinessFormState, vm: BusinessFormViewModel) {
    Hint("These details stay private. Customers never see them unless you choose to show your phone.")
    Field(s.ownerName, { v -> vm.edit { copy(ownerName = v.take(80)) } }, "Your full name")
    Field(s.phone, { v -> vm.edit { copy(phone = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(20)) } }, "Business phone", KeyboardType.Phone)
    ToggleRow("Show this phone number on my public page", s.showPhonePublicly) { vm.edit { copy(showPhonePublicly = it) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopStep(s: BusinessFormState, vm: BusinessFormViewModel) {
    Field(s.name, { v -> vm.edit { copy(name = v.take(80)) } }, "Shop name")
    Text("Type of business", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        BusinessCategory.entries.forEach { c ->
            FilterChip(
                selected = s.category == c,
                enabled = c.enabled,
                onClick = { vm.edit { copy(category = c) } },
                label = { Text(if (c.enabled) c.label else "${c.label} · soon") },
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun LocationStep(s: BusinessFormState, vm: BusinessFormViewModel) {
    val camera = rememberCameraPositionState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) vm.useMyLocation()
    }
    LaunchedEffect(s.location) {
        s.location?.let { camera.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(LatLng(it.lat, it.lng), 16f))) }
    }
    Hint("Tap the map where your shop's entrance is. Customers use this pin to find you.")
    Box(Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.large)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            onMapClick = { vm.setLocation(GeoLocation(it.latitude, it.longitude)) },
        ) {
            s.location?.let { Marker(state = rememberUpdatedMarkerState(LatLng(it.lat, it.lng)), title = s.name) }
        }
        Surface(
            onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 4.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Space.md),
        ) {
            Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MyLocation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("I'm at my shop", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    OutlinedTextField(
        value = s.address,
        onValueChange = { v -> vm.edit { copy(address = v.take(300)) } },
        label = { Text("Shop address") },
        minLines = 2,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ServicesStep(s: BusinessFormState, vm: BusinessFormViewModel) {
    Hint("Average time per service is used to estimate customer waits, so be realistic.")
    s.services.forEach { d ->
        QCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(d.name, { vm.updateService(d.copy(name = it.take(80))) }, "Service name", modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.removeService(d.id) }, enabled = s.services.size > 1) { Icon(Icons.Rounded.Delete, "Remove service") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Field(d.price, { vm.updateService(d.copy(price = it.filter { c -> c.isDigit() || c == '.' }.take(8))) }, "Price", KeyboardType.Decimal, modifier = Modifier.weight(1f))
                    Field(d.minutes, { vm.updateService(d.copy(minutes = it.filter(Char::isDigit).take(3))) }, "Minutes", KeyboardType.Number, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    TextButton(onClick = vm::addService) {
        Icon(Icons.Rounded.Add, null)
        Spacer(Modifier.width(Space.xs))
        Text("Add a service")
    }
}

private val dayLabels = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoursStep(s: BusinessFormState, vm: BusinessFormViewModel) {
    var editing by remember { mutableStateOf<Pair<DayHours, Boolean>?>(null) } // day, editing opening time?
    s.hours.forEach { d ->
        QCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabels[d.day - 1], style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (d.open) {
                    TextButton(onClick = { editing = d to true }) { Text(formatTime(d.opensAt)) }
                    Text("–")
                    TextButton(onClick = { editing = d to false }) { Text(formatTime(d.closesAt)) }
                } else {
                    Text("Closed", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Space.sm))
                }
                Switch(checked = d.open, onCheckedChange = { vm.updateDay(d.copy(open = it)) })
            }
        }
    }
    editing?.let { (day, opening) ->
        val initial = if (opening) day.opensAt else day.closesAt
        val picker = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (opening) "Opens at" else "Closes at") },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(onClick = {
                    val m = picker.hour * 60 + picker.minute
                    vm.updateDay(if (opening) day.copy(opensAt = m) else day.copy(closesAt = m))
                    editing = null
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapacityStep(s: BusinessFormState, vm: BusinessFormViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris -> vm.addPhotos(uris) }
    QCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg)) {
            Text("Chairs / staff serving at once", style = MaterialTheme.typography.titleSmall)
            Text("Waits are shared across this many chairs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Space.md))
            Stepper(s.chairs, 1, 50) { vm.edit { copy(chairs = it) } }
        }
    }
    QCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg)) {
            Text("No-show grace period", style = MaterialTheme.typography.titleSmall)
            Text("After you tap No-show, the customer has this long to arrive before they're skipped.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Space.md))
            Stepper(s.graceMinutes, 1, 15, suffix = " min") { vm.edit { copy(graceMinutes = it) } }
        }
    }
    Text("Photos (${s.photoCount}/8)", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        s.existingPhotos.forEach { url -> PhotoTile(url) { vm.removeExistingPhoto(url) } }
        s.newPhotos.forEach { uri -> PhotoTile(uri) { vm.removeNewPhoto(uri) } }
        if (s.photoCount < 8) {
            Surface(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AddPhotoAlternate, "Add photos", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    Hint("Photos are resized and stripped of location data before upload.")
}

@Composable
private fun PhotoTile(model: Any, onRemove: () -> Unit) {
    Box(Modifier.size(96.dp).clip(MaterialTheme.shapes.medium)) {
        AsyncImage(model, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
        ) { Icon(Icons.Rounded.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun Stepper(value: Int, min: Int, max: Int, suffix: String = "", onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(Icons.Rounded.Remove, "Fewer", Modifier.padding(Space.sm))
        }
        Text("$value$suffix", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = Space.xl))
        Surface(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max, shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Icon(Icons.Rounded.Add, "More", Modifier.padding(Space.sm), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun Hint(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)).padding(Space.md),
    ) {
        Pill("Tip", MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.sm))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

