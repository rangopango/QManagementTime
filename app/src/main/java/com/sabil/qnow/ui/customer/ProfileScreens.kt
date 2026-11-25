package com.sabil.qnow.ui.customer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.sabil.qnow.BuildConfig
import com.sabil.qnow.data.local.AppSettings
import com.sabil.qnow.data.local.ThemeMode
import com.sabil.qnow.domain.repository.AccountInfo
import com.sabil.qnow.domain.repository.AuthRepository
import com.sabil.qnow.domain.repository.RecentLoginRequiredException
import com.sabil.qnow.domain.repository.UserRepository
import com.sabil.qnow.ui.components.EmptyState
import com.sabil.qnow.ui.components.Pill
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.components.ShimmerList
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Public project page, shared from "Share QNow". */
private const val SHARE_URL = "https://github.com/rangopango/QManagementTime"

/** Where "Report a problem" emails go. Empty = the user picks the address in their mail app. */
private const val SUPPORT_EMAIL = ""

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val settings: AppSettings,
    demo: com.sabil.qnow.data.demo.DemoMode,
    promos: com.sabil.qnow.domain.repository.PromoRepository,
) : ViewModel() {
    val isDemo = demo.enabled
    /** True for QNow team accounts (listed in the `admins` collection). */
    val isAdmin = promos.observeIsAdmin().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val account = MutableStateFlow(auth.account())
    val theme = settings.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val language = settings.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.sabil.qnow.data.local.AppLanguage.EN)

    fun setLanguage(lang: com.sabil.qnow.data.local.AppLanguage) {
        viewModelScope.launch { settings.setLanguage(lang) }
    }
    val busy = MutableStateFlow(false)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { users.updateDisplayName(name) }
                .onSuccess { account.value = auth.account(); _messages.send("Name updated") }
                .onFailure { _messages.send("Couldn't update your name. Check your connection.") }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    fun deleteAccount() {
        busy.value = true
        viewModelScope.launch {
            runCatching { users.deleteCustomerAccount() }
                .onFailure { e ->
                    busy.value = false
                    _messages.send(
                        if (e is RecentLoginRequiredException) {
                            "For your safety, sign out and sign in again, then delete your account within a few minutes."
                        } else {
                            "Couldn't delete your account. Check your connection and try again."
                        },
                    )
                }
            // On success the sign-in state changes and the app returns to the sign-in screen.
        }
    }
}

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onMyTokens: () -> Unit,
    onFavourites: () -> Unit,
    onHelp: () -> Unit,
    onTryOn: () -> Unit,
    onFamily: () -> Unit = {},
    onSupport: () -> Unit = {},
    onAds: () -> Unit = {},
    vm: ProfileViewModel = hiltViewModel(),
) {
    val account by vm.account.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(contentPadding = PaddingValues(bottom = Space.xxl)) {
            item { ProfileHeader(account, onBack, onEdit = { dialog = ProfileDialog.Rename }) }
            item {
                Row(
                    Modifier.padding(horizontal = Space.screen).padding(top = Space.lg),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    QuickTile(Icons.Rounded.ConfirmationNumber, "My tokens", Modifier.weight(1f), onMyTokens)
                    QuickTile(Icons.Rounded.Favorite, "Favourites", Modifier.weight(1f), onFavourites)
                    QuickTile(Icons.AutoMirrored.Rounded.HelpOutline, "Help", Modifier.weight(1f), onHelp)
                }
            }

            item { SectionTitle("Account") }
            item {
                SettingsGroup {
                    SettingRow(Icons.Rounded.Edit, "Edit name", account?.name ?: "Add your name") { dialog = ProfileDialog.Rename }
                    SettingRow(Icons.Rounded.VerifiedUser, "Signed in with", account?.signedInWith ?: "-", showArrow = false)
                    SettingRow(Icons.Rounded.FamilyRestroom, "Family & kids", "Gender, children and others you book for", onClick = onFamily)
                }
            }

            item { SectionTitle("Preferences") }
            item {
                SettingsGroup {
                    SettingRow(Icons.Rounded.DarkMode, "Appearance", theme.label) { dialog = ProfileDialog.Theme }
                    SettingRow(Icons.Rounded.Notifications, "Notifications", "Queue alerts and live token") { openNotificationSettings(context) }
                    SettingRow(Icons.Rounded.LocationOn, "Location permission", "Used to find shops near you") { openAppSettings(context) }
                    SettingRow(Icons.Rounded.Language, "Language", language.nativeName) { dialog = ProfileDialog.Language }
                }
            }

            item { SectionTitle("Explore") }
            item {
                SettingsGroup {
                    SettingRow(Icons.Rounded.AutoAwesome, "Look Studio", "Try colours, cuts and beards on your photo", trailing = { Pill("New", MaterialTheme.colorScheme.primary) }, onClick = onTryOn)
                    SettingRow(Icons.Rounded.Storefront, "List your business", "Use a separate business account") { dialog = ProfileDialog.Business }
                }
            }

            if (isAdmin) {
                item { SectionTitle("QNow team") }
                item {
                    SettingsGroup {
                        SettingRow(Icons.Rounded.Campaign, "Ads manager", "Targeted ads, sponsored shops, views and taps", onClick = onAds)
                    }
                }
            }

            item { SectionTitle("Help & about") }
            item {
                SettingsGroup {
                    SettingRow(Icons.AutoMirrored.Rounded.HelpOutline, "Help centre", "Answers to common questions", onClick = onHelp)
                    SettingRow(Icons.Rounded.SupportAgent, "Customer helpline", "Report a problem or ask us anything", onClick = onSupport)
                    SettingRow(Icons.Rounded.PrivacyTip, "Privacy", "What others can and can't see") { dialog = ProfileDialog.Privacy }
                    SettingRow(Icons.Rounded.Share, "Share QNow", "Invite friends") { share(context) }
                    SettingRow(Icons.Rounded.Star, "Rate QNow", "Available once we're on Google Play", trailing = { Pill("Soon", MaterialTheme.colorScheme.primary) })
                    SettingRow(Icons.Rounded.Info, "App version", BuildConfig.VERSION_NAME, showArrow = false)
                }
            }

            item { Spacer(Modifier.height(Space.lg)) }
            item {
                SettingsGroup {
                    SettingRow(Icons.AutoMirrored.Rounded.Logout, if (vm.isDemo) "Exit demo" else "Sign out", if (vm.isDemo) "Back to the real app" else null, tint = MaterialTheme.colorScheme.primary) { if (vm.isDemo) vm.signOut() else dialog = ProfileDialog.SignOut }
                    SettingRow(Icons.Rounded.DeleteForever, "Delete account", "Permanently remove your data", tint = MaterialTheme.colorScheme.error, enabled = !busy) { dialog = ProfileDialog.Delete }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Space.lg))
    }

    when (dialog) {
        ProfileDialog.Rename -> RenameDialog(account?.name.orEmpty(), onDismiss = { dialog = null }) { vm.rename(it); dialog = null }
        ProfileDialog.Theme -> ThemeDialog(theme, onDismiss = { dialog = null }) { vm.setTheme(it); dialog = null }
        ProfileDialog.SignOut -> ConfirmDialog(
            "Sign out?", "Your live token keeps its place. Sign in again with the same account to see it.",
            "Sign out", onDismiss = { dialog = null },
        ) { dialog = null; vm.signOut() }
        ProfileDialog.Delete -> ConfirmDialog(
            "Delete your account?",
            "This permanently removes your profile, favourites and token history, and signs you out. Reviews you wrote stay, without your name. This can't be undone.",
            "Delete forever", destructive = true, onDismiss = { dialog = null },
        ) { dialog = null; vm.deleteAccount() }
        ProfileDialog.Privacy -> InfoDialog(
            "Your privacy",
            "• Other customers only ever see your token number.\n" +
                "• Only the shop you book with sees your first name.\n" +
                "• Your phone number and email are never shown to shops or customers.\n" +
                "• Reviews are anonymous.\n" +
                "• Ads: the app picks which banners to show using the gender and area in your profile, on your phone. Advertisers never get your details, only totals of views and taps. Children's profiles never get targeted ads.\n" +
                "• Data on this phone is encrypted.",
            onDismiss = { dialog = null },
        )
        ProfileDialog.Business -> InfoDialog(
            "List your business",
            "QNow keeps customer and business accounts separate. Sign out, then sign in with a different Google account or phone number and choose \"I run a business\".",
            onDismiss = { dialog = null },
        )
        ProfileDialog.Language -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Choose language") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.sabil.qnow.data.local.AppLanguage.entries.forEach { lang ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = lang == language, onClick = { vm.setLanguage(lang); dialog = null }).padding(vertical = Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = lang == language, onClick = null)
                            Spacer(Modifier.width(Space.sm))
                            Column {
                                androidx.compose.material3.Text(lang.nativeName, style = MaterialTheme.typography.titleMedium)
                                if (lang != com.sabil.qnow.data.local.AppLanguage.EN) {
                                    androidx.compose.material3.Text(lang.englishName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } },
        )
        null -> Unit
    }
}

private enum class ProfileDialog { Rename, Theme, SignOut, Delete, Privacy, Business, Language }

@Composable
private fun ProfileHeader(account: AccountInfo?, onBack: () -> Unit, onEdit: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(QNowTheme.extended.heroGradient)) {
        Column(Modifier.statusBarsPadding().padding(start = Space.sm, end = Space.screen, bottom = Space.xl)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Row(Modifier.padding(start = Space.md), verticalAlignment = Alignment.CenterVertically) {
                Avatar(account, 72)
                Spacer(Modifier.width(Space.lg))
                Column(Modifier.weight(1f)) {
                    Text(
                        account?.name ?: "Hello there",
                        style = MaterialTheme.typography.headlineSmall, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    (account?.phone?.let(::maskPhone) ?: account?.email?.let(::maskEmail))?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                Surface(onClick = onEdit, shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Round profile picture (Google photo) or initial. Also used as the Home top-right button. */
@Composable
fun Avatar(account: AccountInfo?, sizeDp: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(sizeDp.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))
            .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (account?.photoUrl != null) {
            AsyncImage(account.photoUrl, "Profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                (account?.name?.take(1) ?: "Q").uppercase(),
                style = if (sizeDp > 48) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

// Show only part of contact details on screen, in case someone is looking over a shoulder.
private fun maskPhone(p: String) = if (p.length > 6) p.take(3) + " ••••• " + p.takeLast(3) else p
private fun maskEmail(e: String): String {
    val at = e.indexOf('@')
    return if (at > 2) e.take(2) + "•••" + e.substring(at) else e
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    QCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(Space.sm))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.screen + Space.xs, top = Space.xl, bottom = Space.sm),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    QCard(Modifier.padding(horizontal = Space.screen).fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    showArrow: Boolean = true,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (tint == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            trailing != null -> trailing()
            showArrow && onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                label = { Text("First name") },
                supportingText = { Text("Only the shop you book with sees this.") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ThemeDialog(current: ThemeMode, onDismiss: () -> Unit, onPick: (ThemeMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().selectable(current == mode) { onPick(mode) }.padding(vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(current == mode, onClick = { onPick(mode) })
                        Text(mode.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, destructive: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}

private fun share(context: Context) {
    val text = "I use QNow to skip the wait at barbers and salons. Get it here: $SHARE_URL"
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share QNow"))
}

/** Prefills app and device details (no personal data) so problems are easier to fix. */
private fun reportProblem(context: Context) {
    val body = "Describe what went wrong:\n\n\n---\nQNow ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))
        .putExtra(Intent.EXTRA_SUBJECT, "QNow problem report")
        .putExtra(Intent.EXTRA_TEXT, body)
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Report a problem"))
    }
}

// ---------------------------------------------------------------- Favourites

@Composable
fun FavouritesScreen(onBack: () -> Unit, onBusiness: (String) -> Unit, onFind: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { BackTitle("Favourites", onBack) }
        when {
            state.loading -> item { ShimmerList(3) }
            state.favourites.isEmpty() -> item {
                EmptyState(Icons.Rounded.FavoriteBorder, "No favourites yet", "Tap the heart on any shop to keep it here.", actionLabel = "Explore nearby", onAction = onFind)
            }
            else -> items(state.favourites, key = { it.business.id }) { live ->
                FavouriteRow(live, Modifier.animateItem()) { onBusiness(live.business.id) }
            }
        }
    }
}

@Composable
private fun BackTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

// ---------------------------------------------------------------- Help centre

private val faqs = listOf(
    "How does booking a token work?" to "Open a shop, pick a service and tap Book token. You get the next number in that shop's queue for today. Numbers restart every day.",
    "How is my waiting time worked out?" to "QNow adds up the time of each service ahead of you and shares it across the chairs that are working. It updates live whenever the queue moves.",
    "When will I get notified?" to "At about 15 minutes to go, about 5 minutes to go, and when it's your turn. While you hold a token, a silent notification shows your place in line.",
    "I'm not getting alerts. What should I do?" to "Make sure notifications are on for QNow (Profile → Notifications), keep your phone online, and turn off battery saving for QNow if your phone has it.",
    "Can I cancel my token?" to "Yes. Open the token and tap Cancel my token while you're still waiting. You can book again later, but you'll join at the back.",
    "What if I'm late when called?" to "The shop gives you a few minutes' grace. If you don't arrive in time, your token is skipped and you can book again.",
    "Who can see my details?" to "Other customers only see token numbers. The shop sees your first name. Your phone number and email are never shown.",
    "How do I list my shop?" to "Use a separate account: sign out, sign in with another Google account or number, and choose \"I run a business\".",
    "How do I delete my account?" to "Profile → Delete account. For safety you may need to sign in again first.",
)

@Composable
fun HelpScreen(onBack: () -> Unit, onSupport: () -> Unit = {}) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        item { BackTitle("Help centre", onBack) }
        items(faqs) { (q, a) -> FaqItem(q, a) }
        item {
            Spacer(Modifier.height(Space.lg))
            QCard(Modifier.fillMaxWidth(), onClick = onSupport, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.BugReport, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text("Still need help?", style = MaterialTheme.typography.titleSmall)
                        Text("Contact the customer helpline", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                }
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var open by remember { mutableStateOf(false) }
    QCard(Modifier.fillMaxWidth().animateContentSize(), onClick = { open = !open }) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            AnimatedVisibility(open) {
                Text(answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Space.sm))
            }
        }
    }
}
