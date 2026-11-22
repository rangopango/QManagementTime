package com.sabil.queuetimemanagement

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

private val Ink = Color(0xFF17211F)
private val Green = Color(0xFF176B55)
private val Pale = Color(0xFFF4F6F3)
private val Muted = Color(0xFF71807A)
private val Line = Color(0xFFE4EAE5)
private val Amber = Color(0xFFB16B25)

class MainActivity : ComponentActivity() {
    private val viewModel: SalonViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SalonQueueApp(viewModel) }
    }
}

@Composable
internal fun SalonQueueApp(vm: SalonViewModel) {
    val page = vm.page

    // System back button / gesture: go one step back instead of closing the app
    BackHandler(enabled = vm.canGoBack) { vm.back() }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            background = Pale,
            surface = Color.White,
            onSurface = Ink
        )
    ) {
        Scaffold(
            containerColor = Pale,
            bottomBar = {
                if (page in listOf(AppPage.CUSTOMER_HOME, AppPage.SALONS, AppPage.QUEUE, AppPage.PROFILE)) {
                    NavigationBar(containerColor = Color.White) {
                        NavItem("Home", page == AppPage.CUSTOMER_HOME) { vm.openFromHome(AppPage.CUSTOMER_HOME) }
                        NavItem("Find", page == AppPage.SALONS) { vm.openFromHome(AppPage.SALONS) }
                        NavItem("Queue", page == AppPage.QUEUE) { vm.openFromHome(AppPage.QUEUE) }
                        NavItem("Profile", page == AppPage.PROFILE) { vm.openFromHome(AppPage.PROFILE) }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (page) {
                    AppPage.CUSTOMER_HOME -> CustomerHome(
                        queue = vm.queue,
                        today = vm.today,
                        onFind = { vm.openFromHome(AppPage.SALONS) },
                        onQueue = { vm.openFromHome(AppPage.QUEUE) },
                        onProfile = { vm.openFromHome(AppPage.PROFILE) },
                        onSalon = vm::openSalon,
                        onRole = { vm.open(if (it == "Barber") AppPage.BARBER_DASHBOARD else AppPage.OWNER_DASHBOARD) }
                    )
                    AppPage.SALONS -> SalonList(
                        queue = vm.queue,
                        onBack = vm::back,
                        onSelect = vm::openSalon
                    )
                    AppPage.BARBER -> BarberDetails(
                        salon = vm.selectedSalon,
                        queue = vm.queue,
                        selectedBarber = vm.selectedBarber,
                        onBarber = vm::selectBarber,
                        onBack = vm::back,
                        onBook = vm::openBooking
                    )
                    AppPage.BOOKING -> BookingScreen(
                        services = vm.selectedSalon.services,
                        service = vm.selectedService,
                        onService = vm::selectService,
                        today = vm.today,
                        date = vm.selectedDate,
                        onDate = vm::selectDate,
                        slots = vm.slots,
                        time = vm.selectedTime,
                        onTime = vm::selectTime,
                        existing = vm.queue.booking,
                        error = vm.bookingError,
                        onBack = vm::back,
                        onConfirm = vm::confirmBooking
                    )
                    AppPage.QUEUE -> QueueScreen(
                        queue = vm.queue,
                        today = vm.today,
                        onBack = vm::back,
                        onFind = { vm.openFromHome(AppPage.SALONS) },
                        onCancel = vm::cancelBooking
                    )
                    AppPage.PROFILE -> ProfileScreen(onBack = vm::back)
                    AppPage.BARBER_DASHBOARD -> BarberDashboard(
                        queue = vm.queue,
                        onToggleBreak = vm::toggleBreak,
                        onStart = vm::startNext,
                        onComplete = vm::completeCurrent,
                        onWalkIn = vm::addWalkIn,
                        onRemove = vm::removeLastWaiting,
                        onBack = vm::back
                    )
                    AppPage.OWNER_DASHBOARD -> OwnerDashboard(queue = vm.queue, onBack = vm::back)
                }
            }
        }
    }
}

// NavigationBarItem must be called from a RowScope, so this is a RowScope extension
@Composable
private fun RowScope.NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Text(when (label) { "Home" -> "⌂"; "Find" -> "⌕"; "Queue" -> "▤"; else -> "●" }, fontSize = 19.sp) },
        label = { Text(label) }
    )
}

@Composable
private fun CustomerHome(
    queue: QueueState, today: LocalDate,
    onFind: () -> Unit, onQueue: () -> Unit, onProfile: () -> Unit,
    onSalon: (Salon) -> Unit, onRole: (String) -> Unit
) {
    val booking = queue.booking
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("GOOD DAY", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("Find your barber.", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(44.dp).clickable { onProfile() }) {
                    Box(contentAlignment = Alignment.Center) { Text("S", fontWeight = FontWeight.Bold, color = Green, fontSize = 20.sp) }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Ink, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("YOUR NEXT CUT, WITHOUT THE WAIT", color = Color(0xFFB9D8CB), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Book a seat.\nSkip the guessing.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                    Text("See barber availability and queue estimates before you go.", color = Color(0xFFD6E1DC), fontSize = 13.sp)
                    Button(onClick = onFind, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB8E0CE)), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                        Text("Find a salon  →", color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { SectionTitle("YOUR QUEUE") }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth().clickable { onQueue() }) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (booking == null) {
                            Text("No active booking", color = Ink, fontWeight = FontWeight.SemiBold)
                            Text("Your token and wait estimate will appear here.", color = Muted, fontSize = 12.sp)
                        } else {
                            val visit = "${booking.service.name} with ${booking.barber}"
                            if (booking.token != null) {
                                Text("Token ${tokenLabel(booking.token)} · ${booking.salon.name}", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text("$visit · ${waitLabel(queue.bookingWait())}", color = Muted, fontSize = 12.sp)
                            } else {
                                Text("${dateLabel(booking.date, today)}, ${timeLabel(booking.time)} · ${booking.salon.name}", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(visit, color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    Text("→", color = Green, fontSize = 22.sp)
                }
            }
        }
        item { SectionTitle("POPULAR NEAR YOU") }
        items(salons.take(2)) { salon -> SalonCard(salon, queue.salonWait(salon)) { onSalon(salon) } }
        item { SectionTitle("QUICK ACCESS") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallAction("My haircut profile", "Saved styles & history", Modifier.weight(1f), onProfile)
                SmallAction("Home service", "Request a barber", Modifier.weight(1f), onFind)
            }
        }
        // Fixed: Text + Row are wrapped in a Column so they stack instead of overlapping
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DEMO ROLE SWITCH", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onRole("Barber") }, modifier = Modifier.weight(1f)) { Text("Barber view") }
                    OutlinedButton(onClick = { onRole("Owner") }, modifier = Modifier.weight(1f)) { Text("Owner view") }
                }
            }
        }
    }
}

@Composable
private fun SalonList(queue: QueueState, onBack: () -> Unit, onSelect: (Salon) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header("Find a salon", onBack)
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search salon or area") },
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
        Spacer(Modifier.height(14.dp))
        Text("SALONS AROUND JORHAT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(salons.filter { it.name.contains(search, true) || it.area.contains(search, true) }) {
                SalonCard(it, queue.salonWait(it)) { onSelect(it) }
            }
        }
    }
}

@Composable
private fun SalonCard(salon: Salon, wait: Int?, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE6F0EA), modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("✂", color = Green, fontSize = 22.sp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(salon.name, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
                    Text(salon.area, color = Muted, fontSize = 12.sp)
                }
                Text("★ ${salon.rating}", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Text(salon.tags, color = Muted, fontSize = 12.sp)
            Rule()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(waitLabel(wait), color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("From ${rupees(salon.fromPrice)}",color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BarberDetails(
    salon: Salon,
    queue: QueueState,
    selectedBarber: String?,
    onBarber: (String) -> Unit,
    onBack: () -> Unit,
    onBook: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Header("Salon & barber", onBack)
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(salon.name, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("${salon.area}  ·  ★ ${salon.rating}", color = Muted, fontSize = 13.sp)
                Text("LIVE QUEUE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val waiting = queue.waitingCount(salon.name)
                    Text("$waiting", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Green)
                    Spacer(Modifier.width(8.dp))
                    Text("${if (waiting == 1) "person" else "people"} waiting at this salon", color = Ink, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionTitle("CHOOSE YOUR BARBER")
        salon.barbers.forEach { barber ->
            val key = BarberKey(salon.name, barber.name)
            val available = queue.isAvailable(key)
            val selected = barber.name == selectedBarber
            Surface(
                color = if (selected) Color(0xFFE6F1EA) else Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable(enabled = available) { onBarber(barber.name) }
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color(0xFFE8EFEA), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(barber.name.take(1), color = Green, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(barber.name, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(barber.info, color = Muted, fontSize = 11.sp)
                        if (available) Text(waitLabel(queue.barberWait(key)), color = Green, fontSize = 11.sp)
                    }
                    Text(
                        when {
                            !available -> "On break"
                            selected -> "Selected ✓"
                            else -> "Available"
                        },
                        color = if (available) Green else Amber,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBook, enabled = selectedBarber != null, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Choose services & book")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingScreen(
    services: List<Service>,
    service: Service, onService: (Service) -> Unit,
    today: LocalDate, date: LocalDate, onDate: (LocalDate) -> Unit,
    slots: List<LocalTime?>, time: LocalTime?, onTime: (LocalTime?) -> Unit,
    existing: Booking?, error: String?,
    onBack: () -> Unit, onConfirm: () -> Unit
) {
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    if (confirmReplace && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace your booking?") },
            text = {
                Text(
                    "You already have ${existing.service.name} at ${existing.salon.name} " +
                        "(${dateLabel(existing.date, today)}, ${timeLabel(existing.time)}). " +
                        "Continuing cancels it" + if (existing.token != null) " and gives up your place in the queue." else "."
                )
            },
            confirmButton = { TextButton(onClick = { confirmReplace = false; onConfirm() }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Keep current") } }
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Header("Book your visit", onBack)
        Text("SELECT SERVICE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        services.forEach { item ->
            Surface(
                color = if (service == item) Color(0xFFE6F1EA) else Color.White,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onService(item) }
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("${item.duration} min", color = Muted, fontSize = 12.sp)
                    }
                    Text("₹${item.price}", color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    RadioButton(selected = service == item, onClick = { onService(item) })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("WHEN WOULD YOU LIKE TO VISIT?", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(today, today.plusDays(1)).forEach { value ->
                FilterChip(selected = date == value, onClick = { onDate(value) }, label = { Text(dateLabel(value, today)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            slots.forEach { value ->
                FilterChip(selected = time == value, onClick = { onTime(value) }, label = { Text(timeLabel(value), fontSize = 11.sp) })
            }
        }
        if (error != null) Text(error, color = Amber, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("BOOKING SUMMARY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(service.name, color = Ink)
                    Text("₹${service.price}", color = Ink, fontWeight = FontWeight.Bold)
                }
                Text("${service.duration} minutes · ${dateLabel(date, today)} · ${timeLabel(time)}",color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { if (existing != null) confirmReplace = true else onConfirm() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Confirm demo booking")
        }
        Text(
            "Demo only: booking is not sent to a real salon yet.",
            color = Muted, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
        )
    }
}

@Composable
private fun QueueScreen(queue: QueueState, today: LocalDate, onBack: () -> Unit, onFind: () -> Unit, onCancel: () -> Unit) {
    val booking = queue.booking
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Header("Your live queue", onBack)

        if (booking == null) {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No active booking", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Book a seat at a salon and your token will show up here.", color = Muted, fontSize = 13.sp)
                    Button(onClick = onFind, shape = RoundedCornerShape(12.dp)) { Text("Find a salon") }
                }
            }
            return@Column
        }

        Surface(color = Ink, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (booking.token != null) {
                    Text("YOUR TOKEN", color = Color(0xFFB9D8CB), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(tokenLabel(booking.token), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("YOUR APPOINTMENT", color = Color(0xFFB9D8CB), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(timeLabel(booking.time), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
                Text("${booking.salon.name} · ${booking.barber}", color = Color(0xFFD6E1DC), fontSize = 12.sp)
                Text("${booking.service.name} · ${dateLabel(booking.date, today)} · ${timeLabel(booking.time)}", color = Color(0xFFD6E1DC), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (booking.token != null) {
            LiveQueue(queue, booking.token)
        } else {
            Text("This booking is for a set time, so it has no token or place in the live queue.", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel booking (demo)") }
        if (booking.token != null) {
            Spacer(Modifier.height(6.dp))
            Text("Privacy: this public queue displays token numbers only.", color = Green, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LiveQueue(queue: QueueState, myToken: Int) {
    val line = queue.bookingQueue()
    val serving = line.firstOrNull { it.status == EntryStatus.IN_SERVICE }
    val wait = queue.bookingWait()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("NOW SERVING", serving?.let { tokenLabel(it.token) } ?: "—", Modifier.weight(1f))
        MetricCard("AHEAD OF YOU", queue.waitingAheadOfBooking().toString(), Modifier.weight(1f))
        MetricCard("EST. WAIT", if (wait == 0) "None" else "$wait min", Modifier.weight(1f))
    }
    Spacer(Modifier.height(20.dp))
    SectionTitle("PUBLIC QUEUE")
    line.forEach { entry ->
        val inService = entry.status == EntryStatus.IN_SERVICE
        val mine = entry.token == myToken
        val label = when {
            mine && inService -> "Your token · In service"
            mine -> "Your token"
            inService -> "In service"
            else -> "Waiting"
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(if (inService) Green else Color(0xFFCAD5CE), CircleShape))
            Spacer(Modifier.width(12.dp))
            Text("${tokenLabel(entry.token)}  $label", color = Ink, fontWeight = if (inService || mine) FontWeight.Bold else FontWeight.Normal)
        }
        Rule()
    }
    Spacer(Modifier.height(14.dp))
    Text("Queue estimates can change if a service takes longer or the barber takes a break.", color = Muted, fontSize = 12.sp)
}

@Composable
private fun ProfileScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Header("My profile", onBack)
        Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Sabil", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Customer account · Phone number private", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        listOf(
            "My haircut profile" to "Saved styles, reference photos and preferences",
            "Favourite barbers" to "Your preferred barbers and salons",
            "Booking history" to "Past visits, services and receipts",
            "Payments" to "UPI payments and payment records",
            "Privacy & account" to "Phone number and profile visibility"
        ).forEach { (title, subtitle) ->
            Surface(color = Color.White, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { }) {
                Column(Modifier.padding(14.dp)) {
                    Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun BarberDashboard(
    queue: QueueState,
    onToggleBreak: () -> Unit, onStart: () -> Unit, onComplete: () -> Unit,
    onWalkIn: () -> Unit, onRemove: () -> Unit,
    onBack: () -> Unit
) {
    val line = queue.queueFor(demoBarber)
    val stats = queue.statsFor(demoBarber)
    val working = queue.isAvailable(demoBarber)
    val waiting = line.count { it.status == EntryStatus.WAITING }
    val chairFree = line.none { it.status == EntryStatus.IN_SERVICE }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Header("Barber dashboard", onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Good afternoon, Arjun", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("The Gentleman's Cut", color = Muted, fontSize = 12.sp)
            }
            Text(if (working) "✂ Working" else "Ⅱ On hold", color = if (working) Green else Amber, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onToggleBreak,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (working) Color(0xFF9B5B31) else Green)
        ) {
            Text(if (working) "Pause / take a break" else "Resume work")
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("WAITING", waiting.toString(), Modifier.weight(1f))
            MetricCard("COMPLETED", stats.completed.toString(), Modifier.weight(1f))
            MetricCard("EARNED", rupees(stats.earned), Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        SectionTitle("CURRENT QUEUE")
        if (line.isEmpty()) Text("No one in the queue.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        line.forEachIndexed { index, entry ->
            val inService = entry.status == EntryStatus.IN_SERVICE
            Surface(color = Color.White, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${tokenLabel(entry.token)} · ${entry.service.name} · ${entry.service.duration} min", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(if (inService) "Now serving" else "Waiting · token only", color = Muted, fontSize = 11.sp)
                    }
                    if (inService) TextButton(onClick = onComplete) { Text("Complete") }
                    else if (index == 0 && chairFree) TextButton(onClick = onStart, enabled = working) { Text("Start") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onWalkIn, modifier = Modifier.weight(1f)) { Text("+ Walk-in") }
            OutlinedButton(onClick = onRemove, enabled = waiting > 0, modifier = Modifier.weight(1f)) { Text("− Remove last") }
        }
        Spacer(Modifier.height(6.dp))
        Text("Demo controls only. In the live app, removal will require selecting a token and a reason.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun OwnerDashboard(queue: QueueState, onBack: () -> Unit) {
    val staff = demoSalon.barbers.map { BarberKey(demoSalon.name, it.name) }
    val visits = staff.sumOf { queue.statsFor(it).completed }
    val revenue = staff.sumOf { queue.statsFor(it).earned }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Header("Salon owner", onBack)
        Text("Business overview", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("The Gentleman's Cut", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("TODAY'S VISITS", visits.toString(), Modifier.weight(1f))
            MetricCard("REVENUE", rupees(revenue), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("ACTIVE BARBERS", staff.count(queue::isAvailable).toString(), Modifier.weight(1f))
            MetricCard("WAITING", queue.waitingCount(demoSalon.name).toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle("MANAGE YOUR SALON")
        listOf(
            "Barbers & staff" to "Add barbers, profiles and working hours",
            "Services & pricing" to "Set rates and service durations",
            "Appointments" to "Advance bookings and home-service requests",
            "Walk-in queue" to "Monitor each barber's queue",
            "Business profile" to "Photos, address, hours and contact settings"
        ).forEach { (title, subtitle) ->
            Surface(color = Color.White, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { }) {
                Column(Modifier.padding(14.dp)) {
                    Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
            Spacer(Modifier.height(5.dp))
            Text(value, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SmallAction(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = modifier.clickable { onClick() }) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

// Simple divider that works on every Material3 version (HorizontalDivider needs 1.2+)
@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = Green, fontSize = 30.sp, modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
        Text(title, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}
