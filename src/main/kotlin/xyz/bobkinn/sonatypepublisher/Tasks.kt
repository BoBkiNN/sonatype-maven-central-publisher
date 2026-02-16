package xyz.bobkinn.sonatypepublisher

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.*
import xyz.bobkinn.sonatypepublisher.utils.HashUtils
import xyz.bobkinn.sonatypepublisher.utils.PublisherApi
import xyz.bobkinn.sonatypepublisher.utils.ZipUtils
import java.io.File
import javax.inject.Inject

abstract class BuildPublicationArtifacts
    @Inject
    constructor(
        @Internal val publication: Provider<MavenPublication>,
    ) : DefaultTask() {

        @get:Internal
        abstract val additionalTasks: ListProperty<String>

        init {
            val publication = publication.get()
            if (publication is PublicationInternal<*>) {
                // using PublicationInternal#publishableArtifacts to obtain real artifacts list
                val tasks = publication.publishableArtifacts.map {
                    it.buildDependencies.getDependencies(null)
                }.flatten()
                dependsOn(tasks)
                logger.debug("Publication depends on tasks: {}", tasks.map { it.path })
            }
            dependsOn(*additionalTasks.get().toTypedArray())

            group = TASKS_GROUP
            description = "Aggregator task to build publication artifacts"
        }

    }

abstract class AggregateFiles : DefaultTask() {

    @get:Internal
    abstract val aggregateDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val targetDirectory: DirectoryProperty

    @get:Internal
    abstract val publication: Property<MavenPublication>

    private val rootDir = project.rootDir

    init {
        group = TASKS_GROUP
        description = "Aggregate all publishable artifacts into a temporary directory with proper names."
    }

    @TaskAction
    fun action() {
        val folder = targetDirectory.get().asFile
        val aggFolder = aggregateDirectory.get().asFile
        if (aggFolder.exists() && !aggFolder.deleteRecursively()) {
            throw GradleException("Failed to clean directory $aggFolder")
        }
        folder.mkdirs()

        val publication = publication.get()

        val artifactId = publication.artifactId
        val version = publication.version

        // Copy and rename all publishable artifacts directly into temp dir
        val pub = publication as PublicationInternal<*>
        logger.lifecycle("Aggregating ${pub.publishableArtifacts.size} artifacts" +
                " into ${folder.relativeTo(rootDir)}")
        fun processArtifact(file: File, classifier: String?, extension: String) {
            var newName = when (file.name) {
                "module.json" -> "$artifactId-$version.module"
                "module.json.asc" -> "$artifactId-$version.module.asc"
                "pom-default.xml" -> "$artifactId-$version.pom"
                "pom-default.xml.asc" -> "$artifactId-$version.pom.asc"
                else -> file.name
            }
            if (file.name.endsWith(".jar.asc") || file.name.endsWith(".jar")) {
                val cls = classifier?.let { "-$it" } ?: ""
                newName = "$artifactId-$version$cls.${extension}"
//                println("were ${file.name}, become $newName")
            }
            val targetFile = folder.resolve(newName)
            file.copyTo(targetFile, overwrite = true)
            logger.debug("Copied artifact {} to {}", file, targetFile)
        }
        pub.publishableArtifacts.filterIsInstance<MavenArtifact>().forEach { it ->
//            val producerTasks = it.buildDependencies.getDependencies(null)
//            println("Artifact ${it.file}, prod: $producerTasks")
            processArtifact(it.file, it.classifier, it.extension)
        }
    }
}

@CacheableTask
abstract class ComputeHashes : DefaultTask() {

    init {
        group = TASKS_GROUP
        description = "Compute Hash of all files in a temporary directory."
    }

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val directory: DirectoryProperty

    @get:Input
    abstract val additionalAlgorithms: ListProperty<String>

    private val rootDir = project.rootDir

    companion object {
        val REQUIRED_ALGORITHMS = listOf("MD5", "SHA-1")
    }

        @TaskAction
        fun run() {
            logger.debug("Writing file hashes at {}",
                directory.get().asFile.relativeTo(rootDir))
            HashUtils.writesFilesHashes(directory.get().asFile,
                REQUIRED_ALGORITHMS + additionalAlgorithms.get())
        }
    }

@CacheableTask
abstract class CreateZip : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fromDirectory: DirectoryProperty

    @get:OutputFile
    abstract val zipFile: RegularFileProperty

    private val rootDir = project.rootDir

    init {
        group = TASKS_GROUP
        description = "Creates a zip from aggregated and processed files"
    }

    @TaskAction
    fun createArchive() {
        val source = fromDirectory.get().asFile
        val target = zipFile.get().asFile

        logger.lifecycle("Creating zip file from: ${source.relativeTo(rootDir)}")
        target.parentFile.mkdirs()
        ZipUtils.prepareZipFile(source, target)
        logger.lifecycle("Zip created at ${target.relativeTo(rootDir)}")
    }
}

abstract class PublishToSonatypeCentral : DefaultTask() {

    @get:InputFile
    abstract val zipFile: RegularFileProperty

    @get:Nested
    abstract val config: Property<SonatypePublishConfig>

    init {
        group = TASKS_GROUP
        description = "Publish to New Sonatype Maven Central Repository."
    }

    private val thisProject = project

    @TaskAction
    fun uploadZip() {
        val config = config.get()
        logger.lifecycle("Uploading ${config.name} to Sonatype..")
        val id = try {
            PublisherApi.uploadBundle(zipFile.get(), config.publishingType.get(),
                config.publication.get(), config.username.get(), config.password.get())
        } catch (e: PublisherApi.PortalApiError) {
            throw GradleException("Failed to perform upload", e)
        }
        logger.lifecycle("Publication uploaded with deployment id $id")

        logger.debug("Writing deployment status..")
        StoredDeploymentsManager.putCurrent(thisProject, Deployment(id))
        logger.debug("Deployment data updated")
    }
}
