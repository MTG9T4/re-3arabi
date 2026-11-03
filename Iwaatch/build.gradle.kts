version = 1

cloudstream {
    description = "Iwaatch movie catalog; playback uses the site player in Android WebView."
    authors = listOf("MTG9T4")
    language = "ar"
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=iwaatch.com&sz=128"
}

android {
    sourceSets.getByName("main").java.srcDir("../shared/src/main/kotlin")
    sourceSets.getByName("test").java.srcDir("../shared/src/test/kotlin")
    sourceSets.getByName("test").resources.srcDir("../shared/src/test/resources")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
