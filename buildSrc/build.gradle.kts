plugins { `kotlin-dsl` }

dependencies {
  implementation(libs.stonecutter)
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.jvm.get()}")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test { useJUnitPlatform() }
