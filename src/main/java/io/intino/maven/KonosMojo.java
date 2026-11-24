package io.intino.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.repository.RepositorySystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

abstract class KonosMojo extends AbstractMojo {
	@Parameter(required = true)
	private String intinoBuilderVersion;
	@Parameter(defaultValue = "${project.basedir}", readonly = true)
	private File projectDirectory;
	@Parameter(defaultValue = "${project.build.directory}", readonly = true)
	private File buildDirectory;
	@Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
	private File outputDirectory;
	@Parameter(defaultValue = "${project.groupId}", readonly = true)
	private String groupId;
	@Parameter(defaultValue = "${project.artifactId}", readonly = true)
	private String artifactId;
	@Parameter(defaultValue = "${project.version}", readonly = true)
	private String version;
	/** Intino models conventionally live in src, independently of Maven Java sources. */
	@Parameter(defaultValue = "${project.basedir}/src", readonly = true)
	private File sourceDirectory;
	@Parameter(defaultValue = "${project.basedir}/gen")
	private File generatedSourcesDirectory;
	/** Legacy Builder generationPackage suffix. It is appended to the artifact Java package. */
	@Parameter
	private String generationPackage;
	@Parameter
	private String moduleParameters;
	@Parameter(defaultValue = "${project.build.resources[0].directory}", readonly = true)
	private File resourcesDirectory;
	/** Existing Konos arguments file; when supplied it is passed unchanged to the builder. */
	@Parameter
	private File argumentsFile;
	@Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
	private ArtifactRepository localRepository;
	@Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
	private List<ArtifactRepository> repositories;
	@Component
	private RepositorySystem repositorySystem;

	final void execute(String mode) throws MojoExecutionException {
		File konos = resolveBuilder();
		Path arguments = argumentsFile == null ? createArgumentsFile(mode) : argumentsFile.toPath();
		if (!Files.isRegularFile(arguments)) throw new MojoExecutionException("Konos arguments file not found: " + arguments);
		ToolRunner.run(konos, projectDirectory.toPath(), List.of(arguments.toAbsolutePath().toString(), "true"));
	}

	private File resolveBuilder() throws MojoExecutionException {
		try {
			Artifact artifact = repositorySystem.createArtifact("io.intino", "konos", intinoBuilderVersion, "jar");
			var result = repositorySystem.resolve(new ArtifactResolutionRequest()
				.setArtifact(artifact).setRemoteRepositories(repositories).setLocalRepository(localRepository));
			if (!result.isSuccess()) throw new MojoExecutionException("Unable to resolve io.intino:konos:" + intinoBuilderVersion + ": " + result.getExceptions());
			return artifact.getFile();
		} catch (Exception exception) {
			throw new MojoExecutionException("Cannot resolve io.intino:konos:" + intinoBuilderVersion, exception);
		}
	}

	private Path createArgumentsFile(String mode) throws MojoExecutionException {
		try {
			Path generated = buildDirectory.toPath().resolve("intino").resolve(mode.toLowerCase() + ".args");
			Files.createDirectories(generated.getParent());
			Path output = generatedSourcesDirectory.toPath();
			var values = new ArrayList<String>();
			try (var files = Files.walk(sourceDirectory.toPath())) {
				files.filter(path -> path.toString().endsWith(".konos") || path.toString().endsWith(".tara"))
					.forEach(path -> values.add(path.toAbsolutePath() + "#true"));
			}
			Path intinoProject = intinoProjectDirectory();
			Path intinoDirectory = intinoProject.resolve(".intino");
			values.add("");
			values.addAll(List.of("def.file", "", "project", intinoProject.toString(), "module", artifactId,
				"project.path", intinoProject.toString(), "intino.project.path", intinoDirectory.toString(), "module.path", projectDirectory.getAbsolutePath(),
				"outputpath", output.toString(), "final_outputpath", outputDirectory.getAbsolutePath(),
				"res.path", resourcesDirectory == null ? projectDirectory.toPath().resolve("src/main/resources").toString() : resourcesDirectory.getAbsolutePath(),
				"repository.path", localRepository.getBasedir(), "src.path", sourceDirectory.getAbsolutePath(),
				"make", "false", "test.module", "false", "encoding", "UTF-8",
				"groupId", groupId, "artifactId", artifactId, "version", version,
				"generation.package", generationPackage()));
			String parameters = moduleParameters == null || moduleParameters.isBlank() ? importedSetting("moduleParameters") : moduleParameters;
			if (parameters != null && !parameters.isBlank()) values.addAll(List.of("module.parameters", parameters));
			values.addAll(List.of("compilation.mode", mode));
			Files.writeString(generated, String.join("\n", values) + "\n");
			return generated;
		} catch (IOException exception) { throw new MojoExecutionException("Cannot create Konos arguments file", exception); }
	}

	/** Konos stores per-module metadata below the workspace's .intino/Konos directory. */
	private Path intinoProjectDirectory() {
		Path current = projectDirectory.toPath().toAbsolutePath();
		while (current != null) {
			if (Files.isDirectory(current.resolve(".intino")) || Files.isRegularFile(current.resolve("project.legio"))) return current;
			current = current.getParent();
		}
		return projectDirectory.toPath().toAbsolutePath();
	}

	private String generationPackage() throws MojoExecutionException {
		String suffix = generationPackage == null || generationPackage.isBlank() ? importedSetting("generationPackage") : generationPackage;
		if (suffix == null || suffix.isBlank()) suffix = "konos";
		String base = basePackage();
		return suffix.isBlank() ? base : base + "." + suffix;
	}

	private String basePackage() throws MojoExecutionException {
		try (var paths = Files.walk(sourceDirectory.toPath())) {
			var uiModel = paths.filter(path -> path.getFileName().toString().equals("UI.tara") || path.getFileName().toString().equals("UI.konos"))
				.findFirst();
			if (uiModel.isPresent()) {
				Path relativeParent = sourceDirectory.toPath().relativize(uiModel.get().getParent());
				return relativeParent.toString().replace(File.separatorChar, '.');
			}
		} catch (IOException exception) { throw new MojoExecutionException("Cannot determine Konos generation package", exception); }
		return groupId + "." + artifactId.replace('-', '.');
	}

	private String importedSetting(String name) throws MojoExecutionException {
		Path settings = buildDirectory.toPath().resolve("intino/konos.properties");
		if (!Files.isRegularFile(settings)) return null;
		try (var input = Files.newInputStream(settings)) {
			var properties = new Properties();
			properties.load(input);
			return properties.getProperty(name);
		} catch (IOException exception) { throw new MojoExecutionException("Cannot read imported Konos builder settings", exception); }
	}
}
