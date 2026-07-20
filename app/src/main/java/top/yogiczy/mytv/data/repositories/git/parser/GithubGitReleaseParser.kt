package top.yogiczy.mytv.data.repositories.git.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.yogiczy.mytv.data.entities.GitRelease
import top.yogiczy.mytv.data.utils.Constants

class GithubGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("github.com")
    }

    override suspend fun parse(data: String): GitRelease {
        val json = Json.parseToJsonElement(data).jsonObject

        // GitHub 限流/异常时返回 {"message": ...}，release 无附件时 assets 为空，给出准确错误
        val version = json["tag_name"]?.jsonPrimitive?.content?.trimStart('v', 'V')
            ?: throw Exception("release 信息不完整: ${json["message"]?.jsonPrimitive?.content ?: "缺少 tag_name"}")
        val downloadUrl = json["assets"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("browser_download_url")?.jsonPrimitive?.content
            ?: throw Exception("release 没有可下载的附件")
        val description = json["body"]?.jsonPrimitive?.content ?: ""

        return GitRelease(
            version = version,
            downloadUrl = Constants.GITHUB_PROXY + downloadUrl,
            description = description,
        )
    }
}