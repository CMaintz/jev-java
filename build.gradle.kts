plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless") version "6.25.0"
    // 0.34.x is the last line that supports Gradle 8.x (0.35 needs 8.13, 0.36+ needs 9).
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = "io.github.cmaintz"
version = "0.1.0"

repositories { mavenCentral() }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

// Zero runtime dependencies. Only the test toolchain is pulled in.
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Lock dependencies so builds are reproducible and `audit` (osv-scanner) scans exactly what
// is resolved. Regenerate after a dependency change with `./gradlew dependencies --write-locks`.
dependencyLocking { lockAllConfigurations() }

// Source is UTF-8 regardless of the platform default (Windows would otherwise use cp1252).
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Javadoc>().configureEach { options.encoding = "UTF-8" }

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport { dependsOn(tasks.test) }

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule { limit { minimum = "0.80".toBigDecimal() } }
    }
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat()
        removeUnusedImports()
        importOrder()
    }
}

// Maven Central (Central Portal). The plugin adds the sources and javadoc jars and signs
// everything. Credentials and the signing key come from ORG_GRADLE_PROJECT_* env vars in
// the release workflow, never from this file.
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        name = "jev"
        description = "Zero-dependency Java client for TypeSafe AI's Jev System One API. Unofficial."
        inceptionYear = "2026"
        url = "https://github.com/CMaintz/jev-java"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "cmaintz"
                name = "Christoffer Maintz"
                url = "https://github.com/CMaintz"
            }
        }
        scm {
            url = "https://github.com/CMaintz/jev-java"
            connection = "scm:git:https://github.com/CMaintz/jev-java.git"
            developerConnection = "scm:git:ssh://git@github.com/CMaintz/jev-java.git"
        }
    }
}
