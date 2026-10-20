plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(21) }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
dependencies {
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")
    implementation("com.google.code.gson:gson:2.12.1")
    testImplementation(kotlin("test-junit"))
}
application { mainClass.set("cn.lesson.core.ImportCliKt") }
