package io.github.klover.di

import io.github.klover.detection.CloverDetector
import io.github.klover.detection.createCloverDetector
import io.github.klover.screens.capture.CaptureViewModel
import io.github.klover.screens.scan.ScanViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val detectorModule = module {
    // Platform picks ONNX (Android) or a mock stub (iOS); falls back to mock if no model is bundled.
    single<CloverDetector> { createCloverDetector() }
}

val viewModelModule = module {
    factoryOf(::CaptureViewModel)
    factoryOf(::ScanViewModel)
}

fun initKoin() {
    startKoin {
        modules(
            detectorModule,
            viewModelModule,
        )
    }
}
