package io.intino.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Validates a server module and the UI siblings declared by its models. */
@Mojo(name = "validate", threadSafe = true)
public final class ValidateMojo extends AbstractMojo {
	@Parameter(defaultValue = "${project.basedir}", readonly = true)
	private File projectDirectory;
	@Parameter(defaultValue = "${project.packaging}", readonly = true)
	private String packaging;
	@Parameter(defaultValue = "${project.basedir}/src", readonly = true)
	private File sourceDirectory;

	@Override public void execute() throws MojoExecutionException, MojoFailureException {
		if ("pom".equals(packaging)) {
			getLog().info("Omitiendo validación de servidor en el proyecto raíz " + projectDirectory);
			return;
		}
		var errors = new ArrayList<String>();
		Path module = projectDirectory.toPath();
		if (!Files.isRegularFile(module.resolve("artifact.legio"))) errors.add("Missing artifact.legio in " + module);
		if (!Files.isDirectory(sourceDirectory.toPath())) errors.add("Missing model directory " + sourceDirectory);
		for (Path client : clientDirectories(errors)) validateClient(client, errors);
		if (!errors.isEmpty()) throw new MojoFailureException("Intino validation failed:\n - " + String.join("\n - ", errors));
		getLog().info("Intino validation passed for " + module);
	}

	private List<Path> clientDirectories(List<String> errors) throws MojoExecutionException {
		if (!Files.isDirectory(sourceDirectory.toPath())) return List.of();
		try (var models = Files.walk(sourceDirectory.toPath())) {
			return models.filter(path -> path.toString().endsWith(".konos") || path.toString().endsWith(".tara"))
				.flatMap(path -> ImportMojo.servicesIn(path).stream())
				.map(service -> projectDirectory.toPath().resolveSibling(ImportMojo.kebabCase(service))).distinct().toList();
		} catch (IOException exception) { throw new MojoExecutionException("Cannot inspect UI services in " + sourceDirectory, exception); }
	}

	private void validateClient(Path client, List<String> errors) {
		if (!Files.isDirectory(client)) { errors.add("Missing UI sibling " + client); return; }
		if (!Files.isRegularFile(client.resolve("package.json"))) errors.add("Missing package.json in UI sibling " + client);
		try { PackageMojo.webpackOutputDirectory(client); }
		catch (MojoExecutionException exception) { errors.add(exception.getMessage()); }
	}
}
