plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless") version "6.25.0"
}

group = "io.github.cmaintz"
version = "0.1.0"

repositories { mavenCentral() }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
    withJavadocJar()
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

// Public API docs are part of the gate: any doclint warning fails the build.
tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
    }
}

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
