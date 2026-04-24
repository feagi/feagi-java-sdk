plugins {
    // Root project: no applied plugins by default.
}

allprojects {
    group = "org.feagi"
    version = "0.0.2"
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withId("java-library") {
        dependencies {
            "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}

