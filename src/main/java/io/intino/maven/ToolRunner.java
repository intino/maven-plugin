package io.intino.maven;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ToolRunner {
	private ToolRunner() { }

	static void run(File jar, Path directory, List<String> arguments) throws MojoExecutionException {
		if (!jar.isFile()) throw new MojoExecutionException("Tool JAR not found: " + jar);
		var command = new ArrayList<String>();
		command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
		command.add("-jar");
		command.add(jar.getAbsolutePath());
		command.addAll(arguments);
		try {
			var process = new ProcessBuilder(command).directory(directory.toFile()).inheritIO().start();
			if (process.waitFor() != 0) throw new MojoExecutionException("Tool failed: " + String.join(" ", command));
		} catch (IOException exception) {
			throw new MojoExecutionException("Cannot start tool: " + jar, exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new MojoExecutionException("Tool interrupted: " + jar, exception);
		}
	}

	static void runCommand(List<String> command, Path directory) throws MojoExecutionException {
		try {
			var process = new ProcessBuilder(command).directory(directory.toFile()).inheritIO().start();
			if (process.waitFor() != 0) throw new MojoExecutionException("Command failed: " + String.join(" ", command));
		} catch (IOException exception) {
			throw new MojoExecutionException("Cannot start command: " + command.get(0), exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new MojoExecutionException("Command interrupted: " + String.join(" ", command), exception);
		}
	}
}
