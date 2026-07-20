package top.yogiczy.mytv.data.repositories.git.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.yogiczy.mytv.data.entities.GitRelease

class GiteeGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("gitee.com")
    }

    override suspend fun parse(data: String): GitRelease {
        val json = Json.parseToJsonElement(data).jsonObject

        val version = json["tag_name"]?.jsonPrimitive?.content?.trimStart('v', 'V')
            ?: throw Exception("release 信息不完整: ${json["message"]?.jsonPrimitive?.content ?: "缺少 tag_name"}")
        val downloadUrl = json["assets"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("browser_download_url")?.jsonPrimitive?.content
            ?: throw Exception("release 没有可下载的附件")
        val description = json["body"]?.jsonPrimitive?.content ?: ""

        return GitRelease(
            version = version,
            downloadUrl = downloadUrl,
            description = description,
        )
    }
}