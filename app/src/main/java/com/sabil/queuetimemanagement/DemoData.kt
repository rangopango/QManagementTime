package com.sabil.queuetimemanagement

// Placeholder content until the app has a backend.

internal val salons = listOf(
    Salon(
        "The Gentleman's Cut", "Jorhat Town", "4.8", "Haircut · Beard · Styling",
        services = listOf(
            Service("Haircut", 30, 120),
            Service("Beard trim", 15, 100),
            Service("Haircut + Beard", 45, 200),
            Service("Styling", 30, 150)
        ),
        barbers = listOf(
            Barber("Arjun Das", "Preferred barber · 5 years experience"),
            Barber("Rahul Bora", "Barber · 4 years experience"),
            Barber("Imran Hussain", "Barber · 5 years experience")
        )
    ),
    Salon(
        "Urban Trim Studio", "Gar Ali, Jorhat", "4.7", "Haircut · Shave · Facial",
        services = listOf(
            Service("Haircut", 30, 100),
            Service("Shaving", 15, 80),
            Service("Haircut + Shaving", 45, 160),
            Service("Facial", 30, 200)
        ),
        barbers = listOf(
            Barber("Bikash Saikia", "Barber · 6 years experience"),
            Barber("Nayan Gogoi", "Barber · 3 years experience")
        )
    ),
    Salon(
        "Classic Barber House", "Tarajan, Jorhat", "4.6", "Haircut · Beard · Massage",
        services = listOf(
            Service("Haircut", 30, 80),
            Service("Beard trim", 15, 70),
            Service("Haircut + Beard", 45, 140),
            Service("Head massage", 15, 70)
        ),
        barbers = listOf(
            Barber("Pranab Dutta", "Barber · 8 years experience"),
            Barber("Rituraj Borah", "Barber · 2 years experience")
        )
    )
)

/** The salon and barber shown by the demo staff dashboards. */
internal val demoSalon = salons[0]
internal val demoBarber = BarberKey(demoSalon.name, "Arjun Das")

internal fun demoQueueState(): QueueState {
    fun entry(token: Int, salon: Salon, barber: Int, service: Int, serving: Boolean = false) = QueueEntry(
        token, salon.name, salon.barbers[barber].name, salon.services[service],
        if (serving) EntryStatus.IN_SERVICE else EntryStatus.WAITING
    )
    val (gentleman, urban, classic) = salons
    return QueueState(
        entries = listOf(
            entry(7, gentleman, barber = 2, service = 0, serving = true),
            entry(8, gentleman, barber = 0, service = 0, serving = true),
            entry(9, gentleman, barber = 0, service = 1),
            entry(10, gentleman, barber = 0, service = 2),
            entry(11, gentleman, barber = 0, service = 0),
            entry(4, urban, barber = 0, service = 1, serving = true),
            entry(5, urban, barber = 1, service = 0, serving = true),
            entry(6, classic, barber = 0, service = 2, serving = true),
            entry(7, classic, barber = 1, service = 0, serving = true),
            entry(8, classic, barber = 1, service = 1)
        ),
        onBreak = setOf(BarberKey(gentleman.name, "Rahul Bora")),
        stats = mapOf(
            demoBarber to BarberStats(8, 1240),
            BarberKey(gentleman.name, "Rahul Bora") to BarberStats(10, 1180),
            BarberKey(gentleman.name, "Imran Hussain") to BarberStats(8, 1060)
        ),
        nextToken = mapOf(gentleman.name to 12, urban.name to 6, classic.name to 9)
    )
}
