package org.dxworks.gradleminer

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilePhase
import java.io.File
import java.nio.file.Path


lateinit var baseFolder: File

fun main(args: Array<String>) {
    if (args.size != 1) {
        throw IllegalArgumentException("Bad arguments! Please provide only one argument, namely the path to the folder containing the source code.")
    }

    val baseFolderArg = args[0]

    baseFolder = File(baseFolderArg)

    println("Starting GraMi (Gradle Miner)\n")
    println("Reading Files...")

    val buildFiles = baseFolder.walk()
        .filter { it.isFile }
        .filter { it.name == "build.gradle" }
        .toList()

    val astBuilder = AstBuilder()

    val gradleProjects = buildFiles.mapNotNull { buildFile ->
        try {
            println(buildFile)
            val content = buildFile.readText()
            if(content.isNotBlank()) {
                val nodes = astBuilder.buildFromString(CompilePhase.CONVERSION, content)

                val visitor = DependenciesVisitor(buildFile)

                for (n in nodes) {
                    try {
                        n.visit(visitor)
                    } catch (_: RuntimeException) {}
                }

                visitor.toGradleProject()
            } else null
        } catch (e: CompilationFailedException) {
            println("Could not read build file $buildFile")
            e.printStackTrace()
            null
        }
    }

    val resultsPath = Path.of("results")
    resultsPath.toFile().mkdirs()

    val modelPath = Path.of("results", "gradle-model.json")
    val inspectorLibPath = Path.of("results", "il-gradle-deps.json")

    println("Writing Results...")


    println("Exporting Model to $modelPath")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(modelPath.toFile(), gradleProjects)

    println("Exporting Inspector Lib results to $inspectorLibPath")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(inspectorLibPath.toFile(), gradleProjects
        .associate { it.relativePath to it.dependencies.map { it.toInspectorLibDependency() } }
    )

    println("\nGraMi (Gradle Miner) finished successfully! Please view your results in the ./results directory")

}

data class GradleProject(
    val id: GradleProjectId,
    @JsonIgnore
    var path: File?,
    var dependencies: List<GradleDependency> = ArrayList(),
) {
    @JsonProperty("path")
    var relativePath = path?.relativeTo(baseFolder)?.parent?.replace("\\", "/")
}

class GradleProjectId(
    val group: String?,
    val name: String?,
    val version: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GradleProjectId

        if (group != other.group) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = group?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        return result
    }
}

data class GradleDependency(
    val group: String?,
    val name: String?,
    val version: String?,
    val scope: String = "implementation"
) {
    fun toInspectorLibDependency() = InspectorLibDependency("$group:$name", version)
}

