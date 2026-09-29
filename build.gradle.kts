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

// Lock dependencies so `audit` (osv-scanner) has a gradle.lockfile to read. The file
// is generated on demand (gitignored) so lock drift never breaks the gate.
dependencyLocking { lockAllConfigurations() }

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
