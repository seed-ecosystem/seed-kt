package org.example.rsocket

import io.rsocket.kotlin.RSocketError

private const val failureErrorCode = RSocketError.Custom.MinAllowedCode

fun failRSocket(): Nothing {
    throw RSocketError.Custom(
        errorCode = failureErrorCode,
        message = "failure"
    )
}
