package com.yrsel.archive.presentation

import com.yrsel.archive.R
import com.yrsel.archive.domain.DataError
import com.yrsel.archive.domain.Result

fun DataError.asUiText(): UiText =
    when (this) {
        DataError.Local.DISK_FULL -> UiText.StringResource(R.string.app_name)
        DataError.Network.REQUEST_TIMEOUT -> TODO()
        DataError.Network.NO_INTERNET -> TODO()
        DataError.Network.PAYLOAD_TOO_LARGE -> TODO()
        DataError.Network.SERVER_ERROR -> TODO()
        DataError.Network.SERIALIZATION -> TODO()
        DataError.Network.UNKNOWN -> TODO()
    }

fun Result.Error<*, DataError>.asErrorUiText(): UiText = error.asUiText()
