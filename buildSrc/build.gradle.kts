plugins { `kotlin-dsl` }

dependencies {
  implementation(libs.stonecutter)
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.jvm.get()}")
}
