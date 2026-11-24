package com.sabil.qnow.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sabil.qnow.domain.model.CustomerProfile
import com.sabil.qnow.domain.model.FamilyMember
import com.sabil.qnow.domain.model.Gender
import com.sabil.qnow.domain.repository.UserRepository
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.components.SecondaryButton
import com.sabil.qnow.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FamilyViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    val profile = users.observeProfile()
        .catch { emit(CustomerProfile(null, null, emptyList())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerProfile(null, null, emptyList()))

    private fun save(gender: Gender?, family: List<FamilyMember>) {
        viewModelScope.launch { runCatching { users.saveProfile(gender, family) } }
    }

    fun setMyGender(g: Gender) = save(g, profile.value.family)
    fun add(m: FamilyMember) = save(profile.value.gender, (profile.value.family + m).take(10))
    fun remove(id: String) = save(profile.value.gender, profile.value.family.filterNot { it.id == id })
}

/** Manage who the customer books for: their own gender, plus children and other adults. */
@Composable
fun FamilyScreen(onBack: () -> Unit, vm: FamilyViewModel = hiltViewModel()) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<Boolean?>(null) } // true = child, false = adult

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Family & kids", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "Book tokens for yourself or anyone in your family. Shops only see the first name of the person the token is for.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            QCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Space.lg)) {
                    Text("You", style = MaterialTheme.typography.titleMedium)
                    Text("Helps show services meant for you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Space.sm))
                    GenderChips(profile.gender) { vm.setMyGender(it) }
                }
            }
        }
        if (profile.family.isNotEmpty()) {
            item { Text("People you book for", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.sm)) }
        }
        items(profile.family, key = { it.id }) { m ->
            QCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                    MemberIcon(
                        when {
                            m.isChild -> Icons.Rounded.ChildCare
                            m.gender == Gender.FEMALE -> Icons.Rounded.Face3
                            else -> Icons.Rounded.Face
                        },
                    )
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(if (m.isChild) "Child" else "Adult", m.gender.label, m.age?.let { "$it yrs" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.remove(m.id) }) { Icon(Icons.Rounded.Delete, "Remove ${m.name}") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
                SecondaryButton("Add a child", onClick = { adding = true }, icon = Icons.Rounded.ChildCare, modifier = Modifier.weight(1f))
                SecondaryButton("Add an adult", onClick = { adding = false }, icon = Icons.Rounded.PersonAdd, modifier = Modifier.weight(1f))
            }
        }
    }

    adding?.let { child ->
        AddMemberDialog(child, onDismiss = { adding = null }) { vm.add(it); adding = null }
    }
}

@Composable
private fun MemberIcon(icon: ImageVector) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
}

@Composable
fun GenderChips(selected: Gender?, onPick: (Gender) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Gender.entries.forEach { g ->
            FilterChip(selected == g, onClick = { onPick(g) }, label = { Text(g.label) }, shape = CircleShape)
        }
    }
}

@Composable
private fun AddMemberDialog(child: Boolean, onDismiss: () -> Unit, onAdd: (FamilyMember) -> Unit) {
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<Gender?>(null) }
    var age by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (child) "Add a child" else "Add an adult") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("First name") }, singleLine = true)
                GenderChips(gender) { gender = it }
                if (child) {
                    OutlinedTextField(
                        age, { age = it.filter(Char::isDigit).take(2) }, label = { Text("Age (optional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && gender != null,
                onClick = { onAdd(FamilyMember(UUID.randomUUID().toString(), name.trim(), gender!!, child, age.toIntOrNull())) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
