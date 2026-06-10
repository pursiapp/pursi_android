package fi.pursi.data

sealed class DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>()
    data class Error(val exception: Throwable, val message: String? = null) : DataResult<Nothing>()
}

fun <T> DataResult<T>.dataOrNull(): T? = (this as? DataResult.Success)?.data

fun <T> DataResult<T>.errorOrNull(): Throwable? = (this as? DataResult.Error)?.exception
