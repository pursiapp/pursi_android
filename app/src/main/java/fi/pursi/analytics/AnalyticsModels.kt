package fi.pursi.analytics

import kotlinx.serialization.Serializable

@Serializable
data class EventPayload(
    val website: String,
    val hostname: String,
    val url: String,
    val name: String,
    val language: String = "",
    val screen: String = "",
    val title: String = "",
    val referrer: String = "",
    val data: Map<String, String>? = null,
    val id: String? = null,
)

@Serializable
data class AnalyticsEvent(
    val type: String,
    val payload: EventPayload,
)
