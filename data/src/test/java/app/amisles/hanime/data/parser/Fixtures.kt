package app.amisles.hanime.data.parser

internal object Fixtures {

    fun html(name: String): String =
        checkNotNull(Fixtures::class.java.getResourceAsStream("/fixtures/$name")) {
            "缺少测试夹具 /fixtures/$name：抓取真实页面后放入 data/src/test/resources/fixtures/"
        }.use { it.readBytes().toString(Charsets.UTF_8) }
}
