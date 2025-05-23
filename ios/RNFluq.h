#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNFluq : RCTEventEmitter <RCTBridgeModule>

+ (BOOL)handleLink:(NSURL *)url;

@end
