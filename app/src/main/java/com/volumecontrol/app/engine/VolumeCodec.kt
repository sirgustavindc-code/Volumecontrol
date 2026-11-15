package com.volumecontrol.app.engine

/** Formato de gravação das configurações. Tolerante a dados corrompidos: ignora o que não entende. */
object VolumeCodec {
    private val PACKAGE_REGEX = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    fun isValidPackage(name: String): Boolean = PACKAGE_REGEX.matches(name)

    /** "pacote=25;outro=60". Volumes em 100% não são gravados (100 é o padrão). */
    fun encodeVolumes(volumes: Map<String, Int>): String =
        volumes.entries
            .filter { VolumeMath.clampPercent(it.value) != 100 && isValidPackage(it.key) }
            .sortedBy { it.key }
            .joinToString(";") { "${it.key}=${VolumeMath.clampPercent(it.value)}" }

    fun decodeVolumes(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for (part in raw.split(';')) {
            val i = part.lastIndexOf('=')
            if (i <= 0) continue
            val pkg = part.substring(0, i).trim()
            val value = part.substring(i + 1).trim().toIntOrNull() ?: continue
            if (!isValidPackage(pkg)) continue
            val percent = VolumeMath.clampPercent(value)
            if (percent != 100) out[pkg] = percent
        }
        return out
    }

    fun encodeSet(packages: Set<String>): String =
        packages.filter { isValidPackage(it) }.sorted().joinToString(";")

    fun decodeSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(';').map { it.trim() }.filter { isValidPackage(it) }.toSet()
    }
}
