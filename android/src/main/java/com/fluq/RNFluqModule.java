package com.fluq;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.HashMap;
import java.util.Map;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;

public class RNFluqModule extends ReactContextBaseJavaModule implements InstallReferrerStateListener {
  private static final String TAG = "RNFluq";
  private static final String MODULE_NAME = "RNFluq";
  private final ReactApplicationContext reactContext;
  private InstallReferrerClient referrerClient;
  private Uri pendingUri;
  private Promise pendingPromise;
  private String initialLink = null;
  private Map<String, String> initialParams = null;

  public RNFluqModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    
    // Initialize the Install Referrer client
    referrerClient = InstallReferrerClient.newBuilder(reactContext).build();
    referrerClient.startConnection(this);
  }

  @Override
  public String getName() {
    return MODULE_NAME;
  }

  @Override
  public void onInstallReferrerSetupFinished(int responseCode) {
    switch (responseCode) {
      case InstallReferrerClient.InstallReferrerResponse.OK:
        // Connection established
        try {
          ReferrerDetails response = referrerClient.getInstallReferrer();
          String referrerUrl = response.getInstallReferrer();
          handleReferrerData(referrerUrl);
        } catch (RemoteException e) {
          Log.e(TAG, "Error getting install referrer: " + e.getMessage(), e);
        }
        break;
      case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
        Log.w(TAG, "Install referrer not supported on this device");
        break;
      case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
        Log.w(TAG, "Install referrer service unavailable");
        break;
      default:
        Log.w(TAG, "Install referrer setup failed with code: " + responseCode);
    }
  }

  @Override
  public void onInstallReferrerServiceDisconnected() {
    // Try to reconnect if the connection is lost
    referrerClient.startConnection(this);
  }

  private void handleReferrerData(String referrerUrl) {
    if (referrerUrl == null || referrerUrl.isEmpty()) {
      return;
    }

    try {
      // The referrer URL is a URL encoded string of parameters
      String decodedUrl = URLDecoder.decode(referrerUrl, "UTF-8");
      
      // Parse the parameters
      Map<String, String> params = new HashMap<>();
      String[] pairs = decodedUrl.split("&");
      
      for (String pair : pairs) {
        int idx = pair.indexOf("=");
        if (idx > 0) {
          String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
          String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
          params.put(key, value);
        }
      }
      
      // Store params for later use when getInitialLink is called
      if (!params.isEmpty()) {
        initialParams = params;
        initialLink = "android://install";
      }
      
    } catch (UnsupportedEncodingException e) {
      Log.e(TAG, "Error decoding referrer URL: " + e.getMessage(), e);
    }
  }

  public void onNewIntent(Intent intent) {
    if (intent == null || intent.getData() == null) {
      return;
    }

    Uri uri = intent.getData();
    handleUri(uri);
  }

  private void handleUri(Uri uri) {
    if (uri == null) {
      return;
    }

    // Parse the Uri parameters
    Map<String, String> params = new HashMap<>();
    
    for (String paramName : uri.getQueryParameterNames()) {
      String value = uri.getQueryParameter(paramName);
      if (value != null) {
        params.put(paramName, value);
      }
    }
    
    // Create the event data
    WritableMap eventData = Arguments.createMap();
    eventData.putString("url", uri.toString());
    
    // Add parameters
    WritableMap paramsMap = Arguments.createMap();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      paramsMap.putString(entry.getKey(), entry.getValue());
    }
    eventData.putMap("params", paramsMap);
    
    // Send event to JS
    reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit("onDynamicLink", eventData);
  }

  @ReactMethod
  public void getInitialLink(Promise promise) {
    // If we have a referrer from install, return that
    if (initialLink != null && initialParams != null) {
      WritableMap map = Arguments.createMap();
      map.putString("url", initialLink);
      
      WritableMap paramsMap = Arguments.createMap();
      for (Map.Entry<String, String> entry : initialParams.entrySet()) {
        paramsMap.putString(entry.getKey(), entry.getValue());
      }
      map.putMap("params", paramsMap);
      
      // Clear the initial link so we don't return it again
      String linkToReturn = initialLink;
      Map<String, String> paramsToReturn = initialParams;
      initialLink = null;
      initialParams = null;
      
      promise.resolve(map);
      return;
    }
    
    // If we have a pending intent Uri, return that
    if (pendingUri != null) {
      WritableMap map = Arguments.createMap();
      map.putString("url", pendingUri.toString());
      
      // Parse Uri parameters
      WritableMap paramsMap = Arguments.createMap();
      for (String paramName : pendingUri.getQueryParameterNames()) {
        String value = pendingUri.getQueryParameter(paramName);
        if (value != null) {
          paramsMap.putString(paramName, value);
        }
      }
      map.putMap("params", paramsMap);
      
      // Clear the pending uri so we don't return it again
      pendingUri = null;
      
      promise.resolve(map);
      return;
    }
    
    // No dynamic link
    promise.resolve(null);
  }

  @ReactMethod
  public void createDynamicLink(ReadableMap options, Promise promise) {
    String link = options.hasKey("link") ? options.getString("link") : null;
    String domainUriPrefix = options.hasKey("domainUriPrefix") ? options.getString("domainUriPrefix") : null;
    
    if (link == null || domainUriPrefix == null) {
      promise.reject("E_MISSING_REQUIRED", "link and domainUriPrefix are required");
      return;
    }
    
    StringBuilder dynamicLinkBuilder = new StringBuilder();
    dynamicLinkBuilder.append("https://").append(domainUriPrefix).append("/");
    
    // Add parameters if available
    if (options.hasKey("params")) {
      ReadableMap params = options.getMap("params");
      boolean firstParam = true;
      
      for (String key : params.toHashMap().keySet()) {
        String value = params.getString(key);
        if (value != null) {
          if (firstParam) {
            dynamicLinkBuilder.append("?");
            firstParam = false;
          } else {
            dynamicLinkBuilder.append("&");
          }
          
          try {
            // URL encode the key and value
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            String encodedValue = URLEncoder.encode(value, "UTF-8");
            dynamicLinkBuilder.append(encodedKey).append("=").append(encodedValue);
          } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Error encoding URL parameters: " + e.getMessage(), e);
            promise.reject("E_ENCODING_ERROR", "Error encoding URL parameters: " + e.getMessage());
            return;
          }
        }
      }
    }
    
    String dynamicLink = dynamicLinkBuilder.toString();
    promise.resolve(dynamicLink);
  }

  @ReactMethod
  public void addListener(String eventName) {
    // Required for RN built in Event Emitter
  }

  @ReactMethod
  public void removeListeners(Integer count) {
    // Required for RN built in Event Emitter
  }
}
