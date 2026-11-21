package app.amisles.hanime.data.remote

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthApi @Inject constructor(
    private val transport: HttpTransport
) {
    suspend fun fetchLoginPage(): FetchResult = transport.fetchLoginPage()

    suspend fun postLoginForm(
        csrfToken: String,
        email: String,
        password: String
    ): LoginFormResult = transport.postLoginForm(csrfToken, email, password)

    suspend fun fetchHomePage(): FetchResult {
        val baseUrl = transport.baseUrl
        return FetchResult(transport.get("$baseUrl/"), baseUrl)
    }

    suspend fun fetchHomePageWithCookie(cookie: String, domain: String): String =
        transport.fetchHomePageWithCookie(cookie, domain)

    suspend fun fetchAccountEditPage(userId: String): FetchResult {
        val baseUrl = transport.baseUrl
        return FetchResult(transport.get("$baseUrl/user/$userId/edit"), baseUrl)
    }

    suspend fun updateAccountProfile(
        userId: String,
        csrfToken: String,
        name: String,
        email: String
    ): Int = transport.updateAccountProfile(userId, csrfToken, name, email)
}
