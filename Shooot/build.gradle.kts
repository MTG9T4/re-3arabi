version = 1

cloudstream {
    description = "Today's football schedule; live streams are available during broadcasts."
    authors = listOf("MTG9T4")
    language = "ar"
    status = 1
    tvTypes = listOf("Live")
    iconUrl = "https://www.google.com/s2/favicons?domain=shooot.me&sz=128"
}

android {
    sourceSets.getByName("main").java.srcDir("../shared/src/main/kotlin")
    sourceSets.getByName("test").java.srcDir("../shared/src/test/kotlin")
    sourceSets.getByName("test").resources.srcDir("../shared/src/test/resources")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
