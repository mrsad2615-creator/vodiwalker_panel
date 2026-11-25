package com.neurox.common.result

/**
 * Minimal explicit result type used across domain boundaries.
 *
 * Domain code must not throw for expected failures (invalid input, missing
 * profile, corrupted backup). It returns [Outcome]. Exceptions remain acceptable
 * for programming errors.
 */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: DomainError) : Outcome<Nothing>
}

inline fun <T> Outcome<T>.onOk(block: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Ok) block(value)
    return this
}

inline fun <T> Outcome<T>.onErr(block: (DomainError) -> Unit): Outcome<T> {
    if (this is Outcome.Err) block(error)
    return this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Ok)?.value

fun <T> Outcome<T>.getOrElse(fallback: T): T = (this as? Outcome.Ok)?.value ?: fallback

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

/**
 * Errors the domain can return. Codes are stable and are used for localized
 * user-facing messages in the presentation layer.
 */
sealed class DomainError(val code: String) {
    data object ProfileNotFound : DomainError("profile_not_found")
    data object ProfileInactive : DomainError("profile_inactive")
    data object ProfileNameBlank : DomainError("profile_name_blank")
    data object ProfileNameTooLong : DomainError("profile_name_too_long")
    data object ProfileLimitReached : DomainError("profile_limit_reached")
    data object PinRequired : DomainError("pin_required")
    data object PinInvalid : DomainError("pin_invalid")
    data object SessionNotRunning : DomainError("session_not_running")
    data object SessionAlreadyFinished : DomainError("session_already_finished")
    data object ExerciseNotFound : DomainError("exercise_not_found")
    data object InvalidConfiguration : DomainError("invalid_configuration")
    data class ConfigurationField(val field: String) : DomainError("configuration_field_invalid")
    data object BackupFormatUnsupported : DomainError("backup_format_unsupported")
    data object BackupVersionUnsupported : DomainError("backup_version_unsupported")
    data object BackupCorrupted : DomainError("backup_corrupted")
    data object BackupPasswordWrong : DomainError("backup_password_wrong")
    data object BackupContentInvalid : DomainError("backup_content_invalid")
    data object RestoreVerifyFailed : DomainError("restore_verify_failed")
    data object PlanConflict : DomainError("plan_conflict")
    data object PlanInvalid : DomainError("plan_invalid")
    data object StorageFailure : DomainError("storage_failure")
    data class Unknown(val detail: String) : DomainError("unknown")
}
