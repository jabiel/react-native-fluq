#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNDynamicLinks : RCTEventEmitter <RCTBridgeModule>

+ (BOOL)handleLink:(NSURL *)url;

@end
