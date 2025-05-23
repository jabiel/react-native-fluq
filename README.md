# react-native-fluq

A Firebase Dynamic Links alternative for React Native applications.

## Features

- Generate dynamic links on the backend
- Pass parameters through the installation process 
- Handle platform-specific behavior (iOS, Android, Web)
- Receive parameters after app installation
- Lightweight and easy to use

## Installation

```bash
npm install react-native-fluq --save
# or
yarn add react-native-fluq
```

### iOS Setup

1. Add the following to your `AppDelegate.m`:

```objc
#import <RNFluq.h>

// Add this method if it doesn't exist
- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url options:(NSDictionary<UIApplicationOpenURLOptionsKey,id> *)options {
  return [RNFluq handleLink:url];
}

// For Universal Links
- (BOOL)application:(UIApplication *)application continueUserActivity:(NSUserActivity *)userActivity restorationHandler:(void (^)(NSArray<id<UIUserActivityRestoring>> * _Nullable))restorationHandler {
  if ([userActivity.activityType isEqualToString:NSUserActivityTypeBrowsingWeb]) {
    return [RNFluq handleLink:userActivity.webpageURL];
  }
  return NO;
}
```

2. Update your Info.plist to add URL Schemes:

```xml
<key>CFBundleURLTypes</key>
<array>
  <dict>
    <key>CFBundleURLSchemes</key>
    <array>
      <string>your-app-scheme</string>
    </array>
  </dict>
</array>
```

### Android Setup

1. Add the following to your `android/app/src/main/AndroidManifest.xml` within the `<activity>` tag:

```xml
<intent-filter>
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <category android:name="android.intent.category.BROWSABLE" />
  <data android:scheme="your-app-scheme" />
</intent-filter>
```

2. Update your MainActivity.java:

```java
import com.fluq.RNFluqPackage;
import android.content.Intent;

public class MainActivity extends ReactActivity {
  // ...

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    ((RNFluqModule) getReactNativeHost()
      .getReactInstanceManager()
      .getNativeModule("RNFluq"))
      .onNewIntent(intent);
  }
}
```

3. Make sure to add the package to your `MainApplication.java`:

```java
import com.fluq.RNFluqPackage;

// Inside the getPackages() method
@Override
protected List<ReactPackage> getPackages() {
  @SuppressWarnings("UnnecessaryLocalVariable")
  List<ReactPackage> packages = new PackageList(this).getPackages();
  // Add the dynamic links package
  packages.add(new RNFluqPackage());
  return packages;
}
```

## Usage

### Check for initial link that opened the app

```javascript
import Fluq from 'react-native-fluq';

// In your component or app startup
async function checkInitialLink() {
  const linkData = await Fluq.getInitialLink();
  if (linkData) {
    // App was opened with a dynamic link
    console.log('App opened with link:', linkData.url);
    console.log('Parameters:', linkData.params);
    
    // Handle link parameters
    if (linkData.params.referral) {
      // Handle referral code
    }
  }
}
```

### Listen for dynamic links while app is running

```javascript
import Fluq from 'react-native-fluq';

// Set up listener in your component
useEffect(() => {
  const unsubscribe = Fluq.onLink((linkData) => {
    // App received a dynamic link while running
    console.log('Received link:', linkData.url);
    console.log('Parameters:', linkData.params);
    
    // Handle the link parameters
    // ...
  });
  
  // Clean up listener
  return () => unsubscribe();
}, []);
```

## Backend Configuration

This library works with any backend that can generate appropriately formatted dynamic links. The backend should:

1. Generate a short URL that redirects to your app
2. Include platform-specific fallback URLs for web, iOS, and Android
3. Pass parameters through the installation process

See the C# Azure Functions backend implementation for a complete reference.

## License

MIT
