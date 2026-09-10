package com.ogrchatai.app.util

sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>()
    data object Loading : Resource<Nothing>()

    val isSuccess get() = this is Success
    val isError get() = this is Error
    val isLoading get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw throwable ?: Exception(message)
        is Loading -> throw IllegalStateException("Resource is still loading")
    }

    fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(message, throwable)
        is Loading -> Loading
    }

    companion object {
        fun <T> success(data: T): Resource<T> = Success(data)
        fun error(message: String, throwable: Throwable? = null): Resource<Nothing> = Error(message, throwable)
        fun loading(): Resource<Nothing> = Loading
    }
}
