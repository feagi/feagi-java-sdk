plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

sourceSets {
    test {
        java.setSrcDirs(listOf("tests"))
    }
}

dependencies {
    // Intentionally minimal for the skeleton.
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

