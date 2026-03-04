import { }from '@atomiqlab/react-native-mapbox-navigation';
import { ScrollView, Text } from 'react-native';

export default function App() {
  return (
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Module API Example</Text>
      </ScrollView>
  );
}

const styles = {
  header: {
    fontSize: 30,
    margin: 20,
  },
  groupHeader: {
    fontSize: 20,
    marginBottom: 20,
  },
  group: {
    margin: 20,
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 20,
  },
  container: {
    flex: 1,
    backgroundColor: '#eee',
  },
  view: {
    flex: 1,
    height: 200,
  },
};
