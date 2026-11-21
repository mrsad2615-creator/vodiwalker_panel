package dev.mrsad.app.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * One-shot read of a Room flow inside a coroutine (e.g. a repository
 * snapshot). ViewModels call this from Dispatchers.IO/Default, never on main.
 */
suspend fun <T> Flow<T>.firstValue(): T = first()
