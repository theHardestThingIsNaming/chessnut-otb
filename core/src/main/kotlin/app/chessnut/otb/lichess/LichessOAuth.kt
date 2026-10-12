package app.chessnut.otb.lichess

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

/**
 * One Lichess login. The site does not give apps a password login, so the
 * browser login returns a long-lived token and that token is what we keep.
 */
object LichessOAuth {
    const val CLIENT_ID = "chessnut-otb"
    const val REDIRECT = "app.chessnut.otb://oauth"
    /** Board play, and reading private studies for the repertoire drill. */
    const val SCOPE = "board:play study:read challenge:write"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    fun verifier(random: Random = Random.Default): String =
        buildString(64) { repeat(64) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** S256 challenge, without padding, as Lichess requires. */
    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun authorizeUrl(state: String, challenge: String): String =
        "https://lichess.org/oauth?" + form(
            "response_type" to "code",
            "client_id" to CLIENT_ID,
            "redirect_uri" to REDIRECT,
            "code_challenge_method" to "S256",
            "code_challenge" to challenge,
            "scope" to SCOPE,
            "state" to state,
        )

    fun tokenForm(code: String, verifier: String): String = form(
        "grant_type" to "authorization_code",
        "code" to code,
        "code_verifier" to verifier,
        "redirect_uri" to REDIRECT,
        "client_id" to CLIENT_ID,
    )

    fun accessToken(json: String): String {
        val token = (JsonLite.parse(json) as? Json.Obj)?.str("access_token")
        require(!token.isNullOrBlank()) { "Lichess did not return a login." }
        return token
    }

    data class Account(val id: String, val username: String)

    fun account(json: String): Account {
        val obj = JsonLite.parse(json) as? Json.Obj ?: throw IllegalArgumentException("No Lichess account")
        val id = obj.str("id") ?: throw IllegalArgumentException("No Lichess account")
        val username = obj.str("username") ?: id
        return Account(id, username)
    }

    data class Redirect(val code: String?, val state: String?, val error: String?)

    fun redirect(url: String): Redirect {
        val query = url.substringAfter('?', "")
        val params = query.split('&').mapNotNull { part ->
            val key = part.substringBefore('=')
            if (key.isEmpty()) null else key to decode(part.substringAfter('=', ""))
        }.toMap()
        return Redirect(params["code"], params["state"], params["error"])
    }

    fun errorText(body: String): String {
        val parsed = runCatching { JsonLite.parse(body) as? Json.Obj }.getOrNull()
        val message = parsed?.str("error") ?: parsed?.str("global")
        return message?.take(160)?.ifBlank { null } ?: "Lichess refused the request."
    }

    private fun form(vararg fields: Pair<String, String>): String =
        fields.joinToString("&") { (key, value) ->
            "$key=" + URLEncoder.encode(value, StandardCharsets.UTF_8)
        }

    private fun decode(value: String): String =
        java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)
}
