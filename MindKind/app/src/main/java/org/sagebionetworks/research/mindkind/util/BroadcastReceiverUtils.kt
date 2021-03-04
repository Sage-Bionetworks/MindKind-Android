package org.sagebionetworks.research.mindkind.util

import android.content.Intent

// Check for huwaei phones
//https://stackoverflow.com/questions/45448174/broadcast-receiver-not-working-background-on-some-devices/45482394#45482394

// Disable battery opt
//var intent = Intent()
//intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
//intent.setData(android.net.Uri.parse("package:" + getContext().getPackageName()))
//getContext().startActivity(intent)

// Periodic WorkManager tasks
// https://medium.com/androiddevelopers/workmanager-periodicity-ff35185ff006

// Network request work daily to upload to bridge
// https://stackoverflow.com/questions/50761374/how-does-workmanager-schedule-get-requests-to-rest-api