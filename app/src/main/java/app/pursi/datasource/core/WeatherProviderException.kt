package app.pursi.datasource.core

/**
 * Provider-agnostic failure signal from any [WeatherProvider] (or related) method.
 *
 * Providers throw this on fetch/parse failures so that:
 *  - [WeatherRepository] can surface the error to the UI uniformly
 *  - [CompositeWeatherProvider] can fall back to the next provider (vs.
 *    only falling back on empty results)
 *
 * Providers should return an empty list when there is genuinely no data for
 * the requested region/time; throwing this exception means "something went
 * wrong, try the next provider or surface the error".
 */
class WeatherProviderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
