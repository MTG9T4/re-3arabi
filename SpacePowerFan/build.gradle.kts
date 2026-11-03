version = 1

cloudstream {
    description = "Dubbed cartoons and anime from SpacePowerFan"
    authors = listOf("MTG9T4")
    language = "ar"
    status = 1
    tvTypes = listOf("Cartoon", "Anime", "Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=spacepowerfan.com&sz=128"
}

android {
    sourceSets.getByName("main").java.srcDir("../shared/src/main/kotlin")
    sourceSets.getByName("test").java.srcDir("../shared/src/test/kotlin")
    sourceSets.getByName("test").resources.srcDir("../shared/src/test/resources")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
