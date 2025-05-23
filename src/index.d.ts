declare module 'react-native-dynamic-links' {
  export interface DynamicLinkParams {
    [key: string]: string;
  }

  export interface DynamicLinkResult {
    url: string;
    params?: DynamicLinkParams;
    minimumAppVersion?: string;
  }

  export default class DynamicLinks {
    static getInitialLink(): Promise<DynamicLinkResult | null>;
    static onLink(listener: (dynamicLink: DynamicLinkResult) => void): () => void;
    static createDynamicLink(options: {
      link: string;
      domainUriPrefix: string;
      androidPackageName?: string;
      androidFallbackLink?: string;
      iosBundleId?: string;
      iosFallbackLink?: string;
      params?: DynamicLinkParams;
    }): Promise<string>;
  }
}
