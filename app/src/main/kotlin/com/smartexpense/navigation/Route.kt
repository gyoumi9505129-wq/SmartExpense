package com.smartexpense.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object ClubList : Route

    @Serializable
    data object ClubMain : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object ClubHistory : Route

    @Serializable
    data object BankParsingSettings : Route

    @Serializable
    data object ClubAccountManagement : Route
}
