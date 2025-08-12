package com.dismal.ifireworks;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Main extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Request storage permissions
        requestStoragePermissions();

        webView = findViewById(R.id.webView);
        setupWebView();
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();

        // Enable JavaScript and DOM storage
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        // Enable local storage and database storage
        webSettings.setDatabaseEnabled(true);

        // Note: setAppCacheEnabled() and setAppCachePath() are deprecated since API 18
        // Modern WebView uses HTTP cache and DOM storage automatically

        // Enable mixed content (if needed)
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Enable file access for local files
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        // Set cache mode to load from cache when offline
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Enable cookies
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Add JavaScript interface for handling generated file downloads
        webView.addJavascriptInterface(new DownloadInterface(), "Android");

        webView.setWebViewClient(new WebViewClient());

        // Set up download listener
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                downloadFile(url, userAgent, contentDisposition, mimeType);
            }
        });

        // Support file upload
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (Main.this.filePathCallback != null) {
                    Main.this.filePathCallback.onReceiveValue(null);
                }
                Main.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (Exception e) {
                    Main.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
    }

    private void downloadFile(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

            // Handle different types of URLs
            if (url.startsWith("blob:")) {
                downloadBlobFile(url, fileName, mimeType);
            } else if (url.startsWith("data:")) {
                downloadDataUri(url, fileName, mimeType);
            } else if (url.startsWith("file:///android_asset/")) {
                downloadLocalAssetFile(url, fileName);
            } else if (url.startsWith("file://")) {
                downloadLocalFile(url, fileName);
            } else {
                // Handle HTTP/HTTPS downloads with DownloadManager
                downloadHttpFile(url, userAgent, contentDisposition, mimeType, fileName);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadBlobFile(String url, String fileName, String mimeType) {
        // Blob URLs need to be handled by injecting JavaScript to convert blob to data URI
        String script =
                "(function() {" +
                        "var xhr = new XMLHttpRequest();" +
                        "xhr.open('GET', '" + url + "', true);" +
                        "xhr.responseType = 'blob';" +
                        "xhr.onload = function() {" +
                        "var reader = new FileReader();" +
                        "reader.onload = function() {" +
                        "Android.downloadDataUri(reader.result, '" + fileName + "', '" + mimeType + "');" +
                        "};" +
                        "reader.readAsDataURL(xhr.response);" +
                        "};" +
                        "xhr.send();" +
                        "})();";

        webView.evaluateJavascript(script, null);
    }

    private void downloadDataUri(String dataUri, String fileName, String mimeType) {
        try {
            // Parse data URI: data:mimeType;base64,data
            if (!dataUri.startsWith("data:")) {
                throw new IllegalArgumentException("Invalid data URI");
            }

            String[] parts = dataUri.split(",", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid data URI format");
            }

            String header = parts[0]; // data:mimeType;base64
            String data = parts[1];   // base64 encoded data

            // Check if it's base64 encoded
            byte[] fileData;
            if (header.contains("base64")) {
                fileData = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
            } else {
                // URL encoded data
                fileData = java.net.URLDecoder.decode(data, "UTF-8").getBytes();
            }

            // Save to Downloads folder
            saveGeneratedFile(fileData, fileName);

        } catch (Exception e) {
            Toast.makeText(this, "Failed to download generated file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveGeneratedFile(byte[] fileData, String fileName) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+ (Scoped Storage)
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            android.content.ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                try (java.io.OutputStream outputStream = resolver.openOutputStream(uri)) {
                    outputStream.write(fileData);
                    outputStream.flush();

                    Toast.makeText(this, "Downloaded " + fileName + " to Downloads", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            // Legacy method for Android 9 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission required for downloads", Toast.LENGTH_SHORT).show();
                requestStoragePermissions();
                return;
            }

            java.io.File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            java.io.File outputFile = new java.io.File(downloadsDir, fileName);

            try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(outputFile)) {
                outputStream.write(fileData);
                outputStream.flush();

                // Notify media scanner so file appears in Downloads app
                android.media.MediaScannerConnection.scanFile(this,
                        new String[]{outputFile.getAbsolutePath()}, null, null);

                Toast.makeText(this, "Downloaded " + fileName + " to Downloads", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void downloadLocalAssetFile(String url, String fileName) {
        // Extract asset path from file:///android_asset/path
        String assetPath = url.substring("file:///android_asset/".length());

        try {
            // For Android 11+, use MediaStore API for better compatibility
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                copyAssetToDownloadsModern(assetPath, fileName);
            } else {
                // For older Android versions, check permissions first
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Storage permission required for downloads", Toast.LENGTH_SHORT).show();
                    requestStoragePermissions();
                    return;
                }
                copyAssetToDownloadsLegacy(assetPath, fileName);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to download asset: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadLocalFile(String url, String fileName) {
        // Handle other local file:// URLs (not from assets)
        Toast.makeText(this, "Local file downloads not supported for: " + url, Toast.LENGTH_SHORT).show();
    }

    private void downloadHttpFile(String url, String userAgent, String contentDisposition, String mimeType, String fileName) {
        // For Android 11+, DownloadManager works without WRITE_EXTERNAL_STORAGE permission
        // For Android 10 and below, check permissions
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission required for downloads", Toast.LENGTH_SHORT).show();
                requestStoragePermissions();
                return;
            }
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setMimeType(mimeType);
        request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url));
        request.addRequestHeader("User-Agent", userAgent);
        request.setDescription("Downloading file...");
        request.setTitle(fileName);
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        downloadManager.enqueue(request);

        Toast.makeText(this, "Downloading " + fileName, Toast.LENGTH_SHORT).show();
    }

    private void requestStoragePermissions() {
        // For Android 11+ (API 30+), WRITE_EXTERNAL_STORAGE doesn't grant access to external storage
        // Downloads work without explicit permissions when using DownloadManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ - No permission needed for DownloadManager downloads
            return;
        }

        // For Android 10 and below
        String[] permissions = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        boolean needsPermission = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needsPermission = true;
                break;
            }
        }

        if (needsPermission) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Storage permissions are required for downloads", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                Uri dataUri = data.getData();
                if (dataUri != null) {
                    results = new Uri[]{dataUri};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void copyAssetToDownloadsModern(String assetPath, String fileName) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+ (Scoped Storage)
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            android.content.ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                try (java.io.InputStream inputStream = getAssets().open(assetPath);
                     java.io.OutputStream outputStream = resolver.openOutputStream(uri)) {

                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    outputStream.flush();

                    Toast.makeText(this, "Downloaded " + fileName + " to Downloads", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void copyAssetToDownloadsLegacy(String assetPath, String fileName) throws Exception {
        // Legacy method for Android 9 and below
        java.io.File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        java.io.File outputFile = new java.io.File(downloadsDir, fileName);

        try (java.io.InputStream inputStream = getAssets().open(assetPath);
             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(outputFile)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();

            // Notify media scanner so file appears in Downloads app
            android.media.MediaScannerConnection.scanFile(this,
                    new String[]{outputFile.getAbsolutePath()}, null, null);

            Toast.makeText(this, "Downloaded " + fileName + " to Downloads", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        // Don't clear WebView data on destroy to preserve local storage
        super.onDestroy();
    }

    // JavaScript interface for handling generated file downloads
    private class DownloadInterface {
        @android.webkit.JavascriptInterface
        public void downloadDataUri(String dataUri, String fileName, String mimeType) {
            runOnUiThread(() -> {
                Main.this.downloadDataUri(dataUri, fileName, mimeType);
            });
        }
    }
}