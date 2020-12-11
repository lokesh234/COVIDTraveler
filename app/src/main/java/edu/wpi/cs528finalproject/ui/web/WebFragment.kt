package edu.wpi.cs528finalproject.ui.web

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import edu.wpi.cs528finalproject.R

class WebFragment : Fragment() {

    private lateinit var webViewModel: WebViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        webViewModel =
            ViewModelProvider(this).get(WebViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_web, container, false)

        val webView = root.findViewById<WebView>(R.id.webview)
        webView.webViewClient = WebViewClient()
        webView.loadUrl("https://www.cdc.gov/coronavirus/2019-ncov/index.html")
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true

//        val textView: TextView = root.findViewById(R.id.text_home)
//        homeViewModel.text.observe(viewLifecycleOwner, Observer {
//            textView.text = it
//        })
        return root
    }
}