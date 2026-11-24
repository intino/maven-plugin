package io.intino.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.repository.RepositorySystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reinstalls Maven-provided WebArtifacts after a package manager has pruned node_modules. */
@Mojo(name = "resolve-web-artifacts", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class WebArtifactsMojo extends AbstractMojo {
	@Parameter(defaultValue = "${project.build.directory}/intino/web-artifacts.tsv")
	private File webArtifactsFile;
	@Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
	private ArtifactRepository localRepository;
	@Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
	private List<ArtifactRepository> repositories;
	@Component private RepositorySystem repositorySystem;

	@Override public void execute() throws MojoExecutionException {
		Path file = webArtifactsFile.toPath();
		if (!Files.isRegularFile(file)) { getLog().info("No imported WebArtifacts to install"); return; }
		try (var lines = Files.lines(file)) {
			for (String line : lines.distinct().toList()) install(line);
		} catch (IOException exception) { throw new MojoExecutionException("Cannot read " + file, exception); }
	}

	private void install(String line) throws MojoExecutionException {
		String[] values = line.split("\\t", -1);
		if (values.length != 5) throw new MojoExecutionException("Invalid WebArtifact record: " + line);
		try {
			Artifact artifact = repositorySystem.createArtifact(values[1], values[2], values[3], "jar");
			var result = repositorySystem.resolve(new ArtifactResolutionRequest().setArtifact(artifact).setRemoteRepositories(repositories).setLocalRepository(localRepository));
			if (!result.isSuccess()) throw new MojoExecutionException("Unable to resolve " + values[1] + ":" + values[2] + ":" + values[3]);
			Path target = Path.of(values[0], ".intino", "web-artifacts", values[4] + "-" + values[3]);
			ImportMojo.unpack(artifact.getFile().toPath(), target);
			getLog().info("Resolved WebArtifact " + values[1] + ":" + values[2] + ":" + values[3] + " into " + target);
		} catch (MojoExecutionException exception) { throw exception; }
		catch (Exception exception) { throw new MojoExecutionException("Cannot install WebArtifact " + line, exception); }
	}
}
