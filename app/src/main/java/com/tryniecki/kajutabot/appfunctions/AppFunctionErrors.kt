package com.tryniecki.kajutabot.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionNotSupportedException
import com.tryniecki.kajutabot.auth.SessionSignedOutException
import com.tryniecki.kajutabot.ui.player.LocalVolumeError
import com.tryniecki.kajutabot.ui.player.localVolumeError
import kotlinx.coroutines.TimeoutCancellationException
import retrofit2.HttpException

/** Tells agents the cause; anything else would reach them as an error without a message. */
internal fun Exception.toAppFunctionException(): AppFunctionException = when (this) {
    is AppFunctionException -> this
    is SessionSignedOutException -> AppFunctionDeniedException("Not signed in to KajutaBot.")
    is HttpException -> when (code()) {
        401, 403 -> AppFunctionDeniedException("KajutaBot refused this action for the signed-in account.")
        404 -> AppFunctionElementNotFoundException("The server or track no longer exists.")
        else -> AppFunctionAppUnknownException("The KajutaBot service failed with status ${code()}.")
    }
    is TimeoutCancellationException -> AppFunctionAppUnknownException("KajutaBot did not respond in time.")
    else -> when (localVolumeError(this)) {
        LocalVolumeError.UNSUPPORTED ->
            AppFunctionNotSupportedException("The KajutaBot service does not support volume control.")
        LocalVolumeError.ACCESS_DENIED -> AppFunctionDeniedException("This account cannot change the volume.")
        LocalVolumeError.FAILED -> AppFunctionAppUnknownException("KajutaBot could not be reached.")
        else -> AppFunctionAppUnknownException(
            "Volume control is unavailable. Check the KajutaBot helper next to Discord on the computer.",
        )
    }
}
