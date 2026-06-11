package app.pursi.datasource.global

interface RainViewerTimestampSource {
    fun getNearestFrame(targetUnixTime: Long): Pair<Long, String>?
}
