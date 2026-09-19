package com.tryniecki.kajutabot.ui.app

import com.tryniecki.kajutabot.ui.player.GuildAccessState

internal enum class AuthenticatedGate {
    CHECKING_ACCESS,
    ACCESS_ERROR,
    NO_ACCESS,
    ONBOARDING,
    CONTENT,
}

internal fun resolveAuthenticatedGate(
    guildAccessState: GuildAccessState,
    onboardingCompleted: Boolean,
    manualOnboardingRequested: Boolean,
): AuthenticatedGate = when (guildAccessState) {
    GuildAccessState.CHECKING -> AuthenticatedGate.CHECKING_ACCESS
    GuildAccessState.ERROR -> AuthenticatedGate.ACCESS_ERROR
    GuildAccessState.NONE -> AuthenticatedGate.NO_ACCESS
    GuildAccessState.AVAILABLE -> {
        if (!onboardingCompleted || manualOnboardingRequested) {
            AuthenticatedGate.ONBOARDING
        } else {
            AuthenticatedGate.CONTENT
        }
    }
}
