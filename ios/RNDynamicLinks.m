#import "RNDynamicLinks.h"
#import <React/RCTEventEmitter.h>
#import <React/RCTConvert.h>

@implementation RNDynamicLinks
{
  BOOL hasListeners;
  NSDictionary *_initialLink;
  BOOL _initialLinkHandled;
}

RCT_EXPORT_MODULE();

- (instancetype)init
{
  self = [super init];
  if (self) {
    _initialLink = nil;
    _initialLinkHandled = NO;
    
    // Add observer for handling incoming links while the app is running
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(handleOpenURL:)
                                                 name:@"RCTOpenURLNotification"
                                               object:nil];
                                               
    // Add observer for app attribution data (iOS 14.5+)
    if (@available(iOS 14.5, *)) {
      [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(handleAttributionData:)
                                                 name:@"com.apple.attribution.notification"
                                               object:nil];
    }
  }
  return self;
}

- (void)dealloc
{
  [[NSNotificationCenter defaultCenter] removeObserver:self];
}

+ (BOOL)requiresMainQueueSetup
{
  return YES;
}

- (NSArray<NSString *> *)supportedEvents
{
  return @[@"onDynamicLink"];
}

- (void)startObserving
{
  hasListeners = YES;
}

- (void)stopObserving
{
  hasListeners = NO;
}

- (BOOL)handleLink:(NSURL *)url
{
  NSURLComponents *components = [NSURLComponents componentsWithURL:url resolvingAgainstBaseURL:NO];
  
  // Parse query parameters
  NSMutableDictionary *params = [NSMutableDictionary dictionary];
  
  for (NSURLQueryItem *item in components.queryItems) {
    if (item.value) {
      params[item.name] = item.value;
    }
  }
  
  NSDictionary *linkData = @{
    @"url": url.absoluteString,
    @"params": params
  };
  
  if (!_initialLinkHandled) {
    _initialLink = linkData;
  } else if (hasListeners) {
    [self sendEventWithName:@"onDynamicLink" body:linkData];
  }
  
  return YES;
}

- (void)handleOpenURL:(NSNotification *)notification
{
  NSURL *url = notification.userInfo[@"url"];
  [self handleLink:url];
}

- (void)handleAttributionData:(NSNotification *)notification
{
  if (!notification.userInfo) {
    return;
  }
  
  // iOS 14.5+ Attribution API data
  NSDictionary *attributionData = notification.userInfo;
  
  // Extract parameters from attribution data
  NSMutableDictionary *params = [NSMutableDictionary dictionary];
  
  // The exact format of attribution data depends on how it's structured in your backend
  // Here we're assuming the data is in the attribution token and needs parsing
  if (attributionData[@"attributionToken"]) {
    NSString *token = attributionData[@"attributionToken"];
    
    // Parse the token - format will depend on how you encoded it
    // This is a simplified example - you'll need to customize this based on your encoding
    NSArray *components = [token componentsSeparatedByString:@"&"];
    for (NSString *component in components) {
      NSArray *keyValue = [component componentsSeparatedByString:@"="];
      if (keyValue.count == 2) {
        NSString *key = [keyValue[0] stringByRemovingPercentEncoding];
        NSString *value = [keyValue[1] stringByRemovingPercentEncoding];
        params[key] = value;
      }
    }
  }
  
  // Save the attribution data to UserDefaults to be retrieved when app is fully launched
  if (params.count > 0) {
    NSDictionary *linkData = @{
      @"url": @"attribution://install",
      @"params": params
    };
    
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    [defaults setObject:linkData forKey:@"RNDynamicLinksAttributionData"];
    [defaults synchronize];
  }
}

RCT_EXPORT_METHOD(getInitialLink:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
  if (_initialLinkHandled) {
    resolve(nil);
    return;
  }
  
  _initialLinkHandled = YES;
  
  if (_initialLink) {
    resolve(_initialLink);
    _initialLink = nil;
    return;
  }
  
  // If no initial link from direct opening, check user defaults for stored attribution data
  NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
  NSDictionary *attributionData = [defaults objectForKey:@"RNDynamicLinksAttributionData"];
  
  if (attributionData) {
    // Clear the stored attribution data after reading
    [defaults removeObjectForKey:@"RNDynamicLinksAttributionData"];
    [defaults synchronize];
    
    resolve(attributionData);
    return;
  }
  
  // No link data found
  resolve(nil);
}

RCT_EXPORT_METHOD(createDynamicLink:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  NSString *link = options[@"link"];
  NSString *domainUriPrefix = options[@"domainUriPrefix"];
  
  if (!link || !domainUriPrefix) {
    reject(@"E_MISSING_REQUIRED", @"link and domainUriPrefix are required", nil);
    return;
  }
  
  NSMutableString *dynamicLink = [NSMutableString stringWithFormat:@"https://%@/", domainUriPrefix];
  
  if (options[@"params"]) {
    NSDictionary *params = options[@"params"];
    NSMutableArray *queryItems = [NSMutableArray array];
    
    [params enumerateKeysAndObjectsUsingBlock:^(NSString *key, NSString *value, BOOL *stop) {
      NSString *queryItem = [NSString stringWithFormat:@"%@=%@",
                            [key stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLQueryAllowedCharacterSet]],
                            [value stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLQueryAllowedCharacterSet]]];
      [queryItems addObject:queryItem];
    }];
    
    if (queryItems.count > 0) {
      [dynamicLink appendFormat:@"?%@", [queryItems componentsJoinedByString:@"&"]];
    }
  }
  
  resolve(dynamicLink);
}

@end
