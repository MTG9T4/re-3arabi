version = 1

cloudstream {
    description = "Arabic Cartoon catalog. Restricted episodes require your own signed-in subscription."
    authors = listOf("MTG9T4")
    language = "ar"
    status = 1
    tvTypes = listOf("Cartoon", "TvSeries", "Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=arabiccartoon.org&sz=128"
}

android {
    sourceSets.getByName("main").java.srcDir("../shared/src/main/kotlin")
    sourceSets.getByName("test").java.srcDir("../shared/src/test/kotlin")
    sourceSets.getByName("test").resources.srcDir("../shared/src/test/resources")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
