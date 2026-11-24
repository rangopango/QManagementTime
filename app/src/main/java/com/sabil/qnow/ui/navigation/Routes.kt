package com.sabil.qnow.ui.navigation

import kotlinx.serialization.Serializable

// Customer
@Serializable data object HomeRoute
@Serializable data object MapRoute
@Serializable data object MyTokensRoute
@Serializable data class BusinessRoute(val businessId: String)
@Serializable data class TokenRoute(val businessId: String, val tokenId: String)
@Serializable data class ReviewRoute(val businessId: String, val tokenId: String, val businessName: String)
@Serializable data object TryOnRoute
@Serializable data object ProfileRoute
@Serializable data object HelpRoute
@Serializable data object FavouritesRoute
@Serializable data object FamilyRoute
@Serializable data object SupportRoute
@Serializable data object AiStyleRoute
@Serializable data object LiveMirrorRoute

// Business
@Serializable data object DashboardRoute
@Serializable data object EditBusinessRoute
