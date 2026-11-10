package app.amisles.hanime.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 登录 WebView 的白名单与完成信号是登录能不能走通的关键：收紧错了会把 Google 授权链路挡在
 * 外面（会话拿不回来），放宽错了会让持有登录 Cookie 的 WebView 去渲染任意站点。
 */
class WebLoginPolicyTest {

    @Test
    fun `official hosts and subdomains are allowed`() {
        assertTrue(WebLoginPolicy.isAllowedInWebView("https://hanime1.me/login"))
        assertTrue(WebLoginPolicy.isAllowedInWebView("https://hanimeone.me/login"))
        assertTrue(WebLoginPolicy.isAllowedInWebView("https://www.hanime1.me/login"))
    }

    @Test
    fun `google oauth hosts are allowed so the callback can come back`() {
        assertTrue(WebLoginPolicy.isAllowedInWebView("https://accounts.google.com/o/oauth2/v2/auth?client_id=x"))
        assertTrue(WebLoginPolicy.isAllowedInWebView("https://ssl.gstatic.com/gb/images/favicon.ico"))
        assertTrue(WebLoginPolicy.isAllowedInWebView("https://play.google.com/log?format=json"))
    }

    @Test
    fun `regional google domains are allowed`() {
        // Google 按账号地区会跳到本地域名，漏掉这些会在授权中途被甩进系统浏览器
        assertTrue(WebLoginPolicy.isAllowedInWebView("https://accounts.google.com.hk/signin"))
        assertTrue(WebLoginPolicy.isAllowedInWebView("https://www.google.co.jp/accounts"))
        assertTrue(WebLoginPolicy.isAllowedInWebView("https://accounts.withgoogle.com/x"))
        assertFalse(WebLoginPolicy.isAllowedInWebView("https://google.evil.com/accounts"))
    }

    @Test
    fun `scheme is used to keep intent handoffs inside the webview`() {
        assertTrue(WebLoginPolicy.schemeOf("https://hanime1.me/login") == "https")
        // Google 授权收尾时可能用 intent:// 指向 Chrome：这类跳转必须识别出来并拦下
        assertTrue(
            WebLoginPolicy.schemeOf(
                "intent://accounts.google.com/o/oauth2/v2/auth#Intent;scheme=https;package=com.android.chrome;end"
            ) == "intent"
        )
        assertEquals("about", WebLoginPolicy.schemeOf("about:blank"))
        assertEquals("", WebLoginPolicy.schemeOf("not a url"))
    }

    @Test
    fun `google sso youtube hop stays in the webview`() {
        // 授权成功后 Google 会经 accounts.youtube.com/accounts/SetSID 建立 YouTube 的 SSO Cookie，
        // 这一跳被拦就意味着 code 永远回不到站点
        assertTrue(
            WebLoginPolicy.isAllowedInWebView(
                "https://accounts.youtube.com/accounts/SetSID?ssdc=1&sidt=TOKEN&continue=https%3A%2F%2Faccounts.google.com%2Faccounts%2FSetSID"
            )
        )
        assertTrue(WebLoginPolicy.isOAuthHost("https://accounts.youtube.com/accounts/SetSID"))
        assertFalse(WebLoginPolicy.isAllowedInWebView("https://youtube.com.evil.com/accounts/SetSID"))
    }

    @Test
    fun `lookalike hosts are rejected`() {
        assertFalse(WebLoginPolicy.isAllowedInWebView("https://hanime1.me.evil.com/login"))
        assertFalse(WebLoginPolicy.isAllowedInWebView("https://evilhanime1.me/login"))
        assertFalse(WebLoginPolicy.isAllowedInWebView("https://example.com/hanime1.me/login"))
        assertFalse(WebLoginPolicy.isAllowedInWebView("about:blank"))
    }

    @Test
    fun `oauth callback is detected on official hosts only`() {
        assertTrue(WebLoginPolicy.isOAuthCallback("https://hanime1.me/auth/google/callback?code=x&state=y"))
        assertTrue(WebLoginPolicy.isOAuthCallback("https://hanimeone.me/auth/google/callback"))
        // 登录入口本身不是回调：此时会话还没建立
        assertFalse(WebLoginPolicy.isOAuthCallback("https://hanime1.me/auth/google"))
        assertFalse(WebLoginPolicy.isOAuthCallback("https://evil.com/auth/google/callback"))
    }

    @Test
    fun `oauth entry pages are not verified sites`() {
        // 入口只做 302 跳转，此时会话还没建立，在这里校验只会得到假失败
        assertTrue(WebLoginPolicy.isOAuthEntry("https://hanime1.me/auth/google"))
        assertTrue(WebLoginPolicy.isOAuthEntry("https://hanime1.me/auth/google/callback?code=x"))
        assertFalse(WebLoginPolicy.isOAuthEntry("https://hanime1.me/"))
        assertFalse(WebLoginPolicy.isOAuthEntry("https://evil.com/auth/google"))
    }

    @Test
    fun `login and logout pages are not treated as completion targets`() {
        assertTrue(WebLoginPolicy.isLoginPage("https://hanime1.me/login"))
        assertTrue(WebLoginPolicy.isLoginPage("https://hanime1.me/login?redirect=%2F"))
        assertFalse(WebLoginPolicy.isLoginPage("https://hanime1.me/auth/google"))
        assertTrue(WebLoginPolicy.isLogoutUrl("https://hanime1.me/logout"))
    }

    @Test
    fun `google rejection page is recognised`() {
        assertTrue(WebLoginPolicy.isOAuthRejectedPage("https://accounts.google.com/signin/rejected?continue=x"))
        assertTrue(WebLoginPolicy.isOAuthRejectedPage("https://accounts.google.com/v3/signin/rejected"))
        assertFalse(WebLoginPolicy.isOAuthRejectedPage("https://accounts.google.com/v3/signin/identifier"))
    }

    @Test
    fun `session cookie presence does not mean logged in`() {
        // 游客也会拿到 laravel_session：这条判据只能当作「值得去校验」的前置条件
        assertTrue(WebLoginPolicy.hasSessionCookie("laravel_session=abc; XSRF-TOKEN=def"))
        assertFalse(WebLoginPolicy.hasSessionCookie("XSRF-TOKEN=def"))
        assertFalse(WebLoginPolicy.hasSessionCookie(null))
    }

    @Test
    fun `login landing url is the official login page`() {
        assertTrue(WebLoginPolicy.isLoginPage(WebLoginPolicy.LOGIN_URLS.first()))
        assertTrue(WebLoginPolicy.isOfficialHost(WebLoginPolicy.LOGIN_URLS.first()))
    }
}
