plugins { `kotlin-dsl` }

// JUnit 5 + Gradle TestKit are test-only so they stay off the production classpath and never touch
// a real build. The versions below match the ones already cached under ~/.gradle/caches, so this
// resolves fully offline.
dependencies {
  implementation(gradleApi())
  implementation(libs.stonecutter)
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.jvm.get()}")

  testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
  // junit-platform-launcher is required at runtime by the Gradle JUnit test engine; it is
  // cached at 1.11.0 so this resolves fully offline.
  testImplementation("org.junit.platform:junit-platform-launcher:1.11.0")
  // TestKit (GradleRunner/TaskOutcome) used by the cache-fixture test. In a buildSrc `kotlin-dsl`
  // project the `gradleTestKit()` DSL helper resolves to nothing on the test classpath, so pin the
  // concrete artifact that ships inside the running Gradle distribution (9.5.0) by its absolute
  // path. It is cached under ~/.gradle/caches, so this resolves fully offline and stays off the
  // production classpath.
  val testKitJar =
    System.getProperty("user.home") +
      "/.gradle/caches/9.5.0/generated-gradle-jars/gradle-test-kit-9.5.0.jar"
  testImplementation(files(testKitJar))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  testLogging {
    events("passed", "failed", "skipped")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
