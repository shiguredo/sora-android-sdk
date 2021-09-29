package jp.shiguredo.sora.sdk.util

import org.webrtc.RTCStatsReport

fun convertStats(report: RTCStatsReport): Any {
    var entries = mutableListOf<Any>()
    for ((_, stats) in report.statsMap) {
        val entry = mutableMapOf<String, Any>()
        entry["id"] = stats.id
        entry["type"] = stats.type
        entry["timestamp"] = stats.timestampUs
        entry.putAll(stats.members)
        entries.add(entry)
    }
    return entries
}