import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, Button } from 'react-native';
import Fluq from 'react-native-fluq';

const DynamicLinksExample = () => {
  const [initialLink, setInitialLink] = useState(null);
  const [receivedLink, setReceivedLink] = useState(null);

  useEffect(() => {
    // Check for the initial link that opened the app
    const checkInitialLink = async () => {
      try {
        const linkData = await Fluq.getInitialLink();
        if (linkData) {
          setInitialLink(linkData);
        }
      } catch (error) {
        console.error('Error getting initial link:', error);
      }
    };

    checkInitialLink();    // Set up listener for links received while app is running
    const unsubscribe = Fluq.onLink((linkData) => {
      setReceivedLink(linkData);
    });

    // Clean up listener on component unmount
    return () => unsubscribe();
  }, []);  // Function to create a dynamic link with proper Play Store referrer for Android
  const createDynamicLink = async () => {
    try {
      const link = await Fluq.createDynamicLink({
        link: 'https://yourdomain.com/content',
        domainUriPrefix: 'dyn.link',
        androidPackageName: 'com.yourapp',
        // Make sure your Android fallback URL points to the Play Store with room for referrer
        androidFallbackUrl: 'https://play.google.com/store/apps/details?id=com.yourapp',
        iosBundleId: 'com.yourapp',
        iosFallbackUrl: 'https://apps.apple.com/app/yourapp/id123456789',
        params: {
          referralId: 'user123',
          campaign: 'summer2025',
          // These parameters will be passed through installation process
          // using Play Install Referrer API on Android
        },
      });
      
      console.log('Created dynamic link:', link);
      // You can share this link or use it elsewhere in your app
    } catch (error) {
      console.error('Error creating dynamic link:', error);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Dynamic Links Example</Text>
      
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Initial Link:</Text>
        {initialLink ? (
          <View>
            <Text>URL: {initialLink.url}</Text>
            <Text>Parameters:</Text>
            {Object.entries(initialLink.params || {}).map(([key, value]) => (
              <Text key={key}>
                {key}: {value}
              </Text>
            ))}
          </View>
        ) : (
          <Text>No initial link detected</Text>
        )}
      </View>
      
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Received Link:</Text>
        {receivedLink ? (
          <View>
            <Text>URL: {receivedLink.url}</Text>
            <Text>Parameters:</Text>
            {Object.entries(receivedLink.params || {}).map(([key, value]) => (
              <Text key={key}>
                {key}: {value}
              </Text>
            ))}
          </View>
        ) : (
          <Text>No links received while app is running</Text>
        )}
      </View>
      
      <Button
        title="Create Dynamic Link"
        onPress={createDynamicLink}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: '#fff',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
  },
  section: {
    marginBottom: 20,
    padding: 16,
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 10,
  },
});

export default DynamicLinksExample;
