version = 1

cloudstream {
    description = "Live football fixtures and the streams published on their match pages."
    authors = listOf("MTG9T4")
    language = "ar"
    status = 1
    tvTypes = listOf("Live")
    iconUrl = "https://www.google.com/s2/favicons?domain=onlinesportlive.com&sz=128"
}

android {
    sourceSets.getByName("main").java.srcDir("../shared/src/main/kotlin")
    sourceSets.getByName("test").java.srcDir("../shared/src/test/kotlin")
    sourceSets.getByName("test").resources.srcDir("../shared/src/test/resources")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
