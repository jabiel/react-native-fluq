import { NativeModules, Platform } from 'react-native';

const { RNFluq } = NativeModules;

export interface DynamicLinkParams {
  [key: string]: string;
}

export interface DynamicLinkResult {
  url: string;
  params?: DynamicLinkParams;
  minimumAppVersion?: string;
}

export default class Fluq {
  /**
   * Get the initial dynamic link that opened the app
   * 
   * @returns Promise that resolves to the dynamic link or null if none
   */
  static getInitialLink(): Promise<DynamicLinkResult | null> {
    return RNFluq.getInitialLink();
  }

  /**
   * Add a listener for dynamic links while the app is running
   * 
   * @param listener The callback function that will receive dynamic link data
   * @returns Function to unsubscribe the listener
   */
  static onLink(listener: (dynamicLink: DynamicLinkResult) => void): () => void {
    const eventEmitter = Platform.OS === 'ios' 
      ? RNFluq.addListener 
      : RNFluq.addListener;
    
    const subscription = eventEmitter('onDynamicLink', (event: { url: string, params?: DynamicLinkParams }) => {
      listener({
        url: event.url,
        params: event.params || {},
      });
    });
    
    return () => subscription.remove();
  }

  /**
   * Create a dynamic link with parameters
   * 
   * @param options The options for creating a dynamic link
   * @returns Promise that resolves to the created dynamic link
   */
  static async createDynamicLink(options: {
    link: string;
    domainUriPrefix: string;
    androidPackageName?: string;
    androidFallbackLink?: string;
    iosBundleId?: string;
    iosFallbackLink?: string;
    params?: DynamicLinkParams;
  }): Promise<string> {
    return RNFluq.createDynamicLink(options);
  }
}
