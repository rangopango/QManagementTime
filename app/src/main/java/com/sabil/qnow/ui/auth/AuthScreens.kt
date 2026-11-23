package com.sabil.qnow.ui.auth

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabil.qnow.domain.model.UserRole
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space

@Composable
fun BrandMark(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 64.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size * 0.56f).border(size * 0.08f, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(size * 0.17f).clip(CircleShape).background(com.sabil.qnow.ui.theme.Brand.Coral))
        }
    }
}

@Composable
fun AuthScreen(vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity

    Box(Modifier.fillMaxSize().background(QNowTheme.extended.heroGradient)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
        ) {
            Column(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.screen, vertical = Space.xxl),
            ) {
                BrandMark()
                Spacer(Modifier.height(Space.xl))
                Text("Your place in line,\nlive.", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Book a token, see the queue move in real time, and walk in right when it's your turn.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Space.xl).navigationBarsPadding()) {
                    AnimatedContent(
                        targetState = state.verificationId != null,
                        transitionSpec = {
                            (slideInHorizontally { if (targetState) it else -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { if (targetState) -it else it } + fadeOut())
                        },
                        label = "step",
                    ) { codeStep ->
                        if (!codeStep) PhoneStep(state, vm, activity) else CodeStep(state, vm)
                    }
                    AnimatedVisibility(state.error != null) {
                        Text(
                            state.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = Space.md),
                        )
                    }
                    if (state.verificationId == null) {
                        Row(Modifier.padding(vertical = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text("  or  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(Modifier.weight(1f))
                        }
                        OutlinedButton(
                            onClick = { vm.google(activity) },
                            enabled = !state.googleLoading,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            GoogleG()
                            Spacer(Modifier.width(Space.md))
                            Text(if (state.googleLoading) "Connecting…" else "Continue with Google", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.height(Space.lg))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Other customers only ever see your token number.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneStep(state: AuthUiState, vm: AuthViewModel, activity: Activity) {
    Column {
        Text("Sign in", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Space.xs))
        Text("We'll text you a 6-digit code.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            OutlinedTextField(
                value = state.countryCode,
                onValueChange = vm::onCountryCode,
                singleLine = true,
                modifier = Modifier.width(92.dp),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                label = { Text("Code") },
            )
            OutlinedTextField(
                value = state.phone,
                onValueChange = vm::onPhone,
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                label = { Text("Mobile number") },
            )
        }
        Spacer(Modifier.height(Space.lg))
        PrimaryButton(
            "Send code",
            onClick = { vm.sendCode(activity) },
            enabled = state.canSend,
            loading = state.loading,
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CodeStep(state: AuthUiState, vm: AuthViewModel) {
    LaunchedEffect(state.code) { if (state.code.length == 6 && !state.loading) vm.verify() }
    Column {
        Text("Enter the code", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sent to ${state.countryCode} ${state.phone}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = vm::editNumber) { Text("Change") }
        }
        Spacer(Modifier.height(Space.md))
        OtpField(state.code, vm::onCode)
        Spacer(Modifier.height(Space.xl))
        PrimaryButton("Verify", onClick = vm::verify, enabled = state.canVerify, loading = state.loading, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun OtpField(code: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = code,
        onValueChange = onChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                repeat(6) { i ->
                    val char = code.getOrNull(i)?.toString().orEmpty()
                    val focused = i == code.length
                    Box(
                        Modifier.weight(1f).height(58.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                if (focused) 2.dp else 0.dp,
                                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                MaterialTheme.shapes.medium,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(char, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        },
    )
}

@Composable
private fun GoogleG() {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(Color.White).border(1.dp, Color(0xFFE0E0E0), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RoleScreen(suggestedName: String?, onSignOut: () -> Unit, vm: RoleViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(suggestedName) { vm.prefill(suggestedName) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(Space.screen),
    ) {
        Spacer(Modifier.height(Space.xl))
        Text("How will you use QNow?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Pick one. You can't switch this account later, so business owners may want a separate account for booking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xl))
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::onName,
            label = { Text("Your first name (optional)") },
            supportingText = { Text("Only the business you book with sees this. Other customers never do.") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.lg))
        RoleCard(
            icon = Icons.Rounded.Person,
            title = "I'm a customer",
            body = "Find barbers, salons and parlours nearby, book a token and track your turn live.",
            accent = MaterialTheme.colorScheme.primary,
            enabled = !state.saving,
        ) { vm.choose(UserRole.CUSTOMER) }
        Spacer(Modifier.height(Space.md))
        RoleCard(
            icon = Icons.Rounded.Storefront,
            title = "I run a business",
            body = "List your shop, manage your live queue, add walk-ins and keep customers in the loop.",
            accent = MaterialTheme.colorScheme.secondary,
            enabled = !state.saving,
        ) { vm.choose(UserRole.BUSINESS) }
        state.error?.let {
            Spacer(Modifier.height(Space.md))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Space.xl))
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Use a different account") }
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, body: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FirebaseNotConfiguredScreen() {
    Column(
        Modifier.fillMaxSize().background(QNowTheme.extended.heroGradient).statusBarsPadding().padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(size = 80.dp)
        Spacer(Modifier.height(Space.xl))
        Text("Almost ready", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(Space.sm))
        Text(
            "This build of QNow isn't connected to Firebase yet. Add app/google-services.json and rebuild. The README explains how.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(QNowTheme.extended.heroGradient), contentAlignment = Alignment.Center) {
        BrandMark(size = 88.dp)
    }
}
