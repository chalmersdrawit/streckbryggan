package it.drawit.streckbryggan

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.util.*


class ShopActivity : AppCompatActivity() {

    private lateinit var shopView: WebView

    data class WebViewAuth(val username: String, val password: String): WebViewClient() {
        override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler, host: String?, realm: String?) {
            handler.proceed(username, password)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        val strecklistanBaseUri = getString(R.string.strecklistan_base_uri)
        val strecklistanUser = getString(R.string.strecklistan_http_user)
        val strecklistanPass = getString(R.string.strecklistan_http_pass)

        shopView = findViewById(R.id.shopView)

        shopView.webViewClient = WebViewAuth(strecklistanUser, strecklistanPass)
        shopView.settings.javaScriptEnabled = true
        shopView.loadUrl(strecklistanBaseUri)
        shopView.requestFocus()
    }
}

