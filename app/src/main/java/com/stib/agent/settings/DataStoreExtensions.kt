package com.stib.agent.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Extension pour accéder au DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stib_settings")
