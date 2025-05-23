package com.dynamiclinks;

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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class RNDynamicLinksModule extends ReactContextBaseJavaModule {
    private static final String TAG = "RNDynamicLinks";
    private final ReactApplicationContext reactContext;
    private Promise initialLinkPromise;
    private Intent initialIntent;
    private boolean initialLinkHandled = false;

    public RNDynamicLinksModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.initialIntent = getCurrentActivity() != null ? getCurrentActivity().getIntent() : null;
    }

    @Override
    public String getName() {
        return "RNDynamicLinks";
    }

    /**
     * Called when a new intent is received by the activity
     */
    public void onNewIntent(Intent intent) {
        this.initialIntent = intent;
        handleIntent(intent);
    }
      @ReactMethod
    public void getInitialLink(Promise promise) {
        if (initialLinkHandled) {
            promise.resolve(null);
            return;
        }
        
        initialLinkPromise = promise;
        
        if (initialIntent != null) {
            // First try to handle intent if the app was opened via a deep link
            handleIntent(initialIntent);
        } else {
            // If no intent, check for install referrer data
            checkInstallReferrer();
        }
        
        initialLinkHandled = true;
    }
    
    private void checkInstallReferrer() {
        final InstallReferrerClient referrerClient = InstallReferrerClient.newBuilder(reactContext).build();
        
        referrerClient.startConnection(new InstallReferrerStateListener() {
            @Override
            public void onInstallReferrerSetupFinished(int responseCode) {
                switch (responseCode) {
                    case InstallReferrerClient.InstallReferrerResponse.OK:
                        // Connection established
                        try {
                            ReferrerDetails response = referrerClient.getInstallReferrer();
                            String referrerUrl = response.getInstallReferrer();
                            Log.d(TAG, "Install referrer: " + referrerUrl);
                            
                            // Parse the referrer URL for our dynamic link parameters
                            if (referrerUrl != null && !referrerUrl.isEmpty()) {
                                // Here we expect the referrer to be in the format: 
                                // utm_source=dynamic_link&<our_custom_params>
                                if (referrerUrl.contains("utm_source=dynamic_link")) {
                                    Uri uri = Uri.parse("https://example.com/?" + referrerUrl);
                                    
                                    // Extract parameters
                                    WritableMap params = Arguments.createMap();
                                    for (String name : uri.getQueryParameterNames()) {
                                        String value = uri.getQueryParameter(name);
                                        if (value != null) {
                                            params.putString(name, value);
                                        }
                                    }
                                    
                                    // Create the result
                                    WritableMap result = Arguments.createMap();
                                    result.putString("url", "install_referrer://" + referrerUrl);
                                    result.putMap("params", params);
                                    
                                    if (initialLinkPromise != null) {
                                        initialLinkPromise.resolve(result);
                                        initialLinkPromise = null;
                                        referrerClient.endConnection();
                                        return;
                                    }
                                }
                            }
                            
                        } catch (RemoteException e) {
                            Log.e(TAG, "Error getting install referrer: ", e);
                        }
                        break;
                    case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
                        Log.w(TAG, "Install referrer API not supported");
                        break;
                    case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
                        Log.w(TAG, "Install referrer service unavailable");
                        break;
                }
                
                // If we get here, either there's no referrer data or there was an error
                if (initialLinkPromise != null) {
                    initialLinkPromise.resolve(null);
                    initialLinkPromise = null;
                }
                
                referrerClient.endConnection();
            }
            
            @Override
            public void onInstallReferrerServiceDisconnected() {
                // Service disconnected, try to reconnect in next attempt
                if (initialLinkPromise != null) {
                    initialLinkPromise.resolve(null);
                    initialLinkPromise = null;
                }
            }
        });
    }

    @ReactMethod
    public void createDynamicLink(ReadableMap options, Promise promise) {
        try {
            String link = options.getString("link");
            String domainUriPrefix = options.getString("domainUriPrefix");

            if (link == null || domainUriPrefix == null) {
                throw new Exception("link and domainUriPrefix are required");
            }
            
            Uri.Builder builder = new Uri.Builder()
                .scheme("https")
                .authority(domainUriPrefix)
                .path("/");
            
            if (options.hasKey("params")) {
                ReadableMap params = options.getMap("params");
                Set<String> keys = params.toHashMap().keySet();
                
                for (String key : keys) {
                    builder.appendQueryParameter(key, params.getString(key));
                }
            }
            
            String dynamicLink = builder.build().toString();
            promise.resolve(dynamicLink);
            
        } catch (Exception e) {
            promise.reject("E_CREATE_LINK", e.getMessage());
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            
            if (data != null) {
                String url = data.toString();
                Log.d(TAG, "Received dynamic link: " + url);
                
                // Extract parameters from the URI
                WritableMap params = Arguments.createMap();
                
                for (String key : data.getQueryParameterNames()) {
                    String value = data.getQueryParameter(key);
                    if (value != null) {
                        params.putString(key, value);
                    }
                }
                
                // Create the result object
                WritableMap result = Arguments.createMap();
                result.putString("url", url);
                result.putMap("params", params);
                
                if (initialLinkPromise != null) {
                    initialLinkPromise.resolve(result);
                    initialLinkPromise = null;
                } else {
                    // Send an event to JavaScript
                    sendEvent("onDynamicLink", result);
                }
            }
        } else if (initialLinkPromise != null) {
            initialLinkPromise.resolve(null);
            initialLinkPromise = null;
        }
    }

    private void sendEvent(String eventName, WritableMap params) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }
}
