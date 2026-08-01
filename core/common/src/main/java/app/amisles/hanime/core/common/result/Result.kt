package app.amisles.hanime.core.common.result

/**
 * 统一结果封装，替代直接返回 null 或抛异常
 */
sealed class Result<T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error<T>(val message: String, val exception: Throwable? = null) : Result<T>()
    data class Loading<T>(val progress: Float = 0f) : Result<T>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception ?: IllegalStateException(message)
        is Loading -> throw IllegalStateException("Result is still loading")
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(message, exception)
        is Loading -> Loading(progress)
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (String, Throwable?) -> Unit): Result<T> {
        if (this is Error) action(message, exception)
        return this
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun <T> error(message: String, exception: Throwable? = null): Result<T> = Error(message, exception)
        fun <T> loading(progress: Float = 0f): Result<T> = Loading(progress)
    }
}