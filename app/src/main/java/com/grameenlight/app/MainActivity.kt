package com.grameenlight.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.room.Room
import com.grameenlight.app.data.AppDatabase
import com.grameenlight.app.data.FirebaseLightRepository
import com.grameenlight.app.ui.GrameenLightApp
import com.grameenlight.app.ui.GrameenLightViewModel
import com.grameenlight.app.ui.GrameenLightViewModelFactory
import com.grameenlight.app.ui.theme.GrameenLightTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "grameen_light.db"
        ).addMigrations(AppDatabase.MIGRATION_1_2).build()
    }

    private val viewModel: GrameenLightViewModel by viewModels {
        GrameenLightViewModelFactory(
            reportDao = database.poleReportDao(),
            firebaseRepository = FirebaseLightRepository()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            GrameenLightTheme(darkTheme = state.isDarkAudit) {
                GrameenLightApp(
                    state = state,
                    onPoleSelected = viewModel::selectPole,
                    onDismissPoleSheet = viewModel::dismissPoleSheet,
                    onReportPole = viewModel::reportPole,
                    onMessageShown = viewModel::clearMessage
                )
            }
        }
    }
}
