data class ReleaseVersion(val name: String, val code: Int) {
    companion object {
        private val TAG = Regex("""v(\d+)\.(\d+)\.(\d+)""")

        fun fromTag(tag: String?): ReleaseVersion {
            if (tag.isNullOrEmpty()) return ReleaseVersion("0.0.0-dev", 1)

            val (major, minor, patch) = TAG.matchEntire(tag)?.destructured?.toList()?.map { it.toInt() }
                ?: throw IllegalArgumentException("Version tag must be vMAJOR.MINOR.PATCH: $tag")
            // 자릿수가 넘치면 v1.100.0 이 v2.0.0 과 같은 versionCode 가 되어 Play 가 업로드를 거부하거나 순서가 뒤집힌다.
            require(minor <= 99 && patch <= 99) { "MINOR and PATCH must be 0..99: $tag" }
            val code = major * 10000 + minor * 100 + patch
            require(code > 0) { "versionCode must be positive: $tag" }
            return ReleaseVersion("$major.$minor.$patch", code)
        }
    }
}
