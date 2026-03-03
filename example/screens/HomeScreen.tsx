import * as React from 'react'
import { StyleSheet, Text } from 'react-native'

export default function HomeScreen() {
  

  return (
    <>
      <Text style={styles.urlText}>URL</Text>
    </>
  )
}

const styles = StyleSheet.create({
  urlText: {
    padding: 20,
  },
})