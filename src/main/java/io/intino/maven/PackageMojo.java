package io.intino.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/** Builds declared Web UI siblings and includes their www output in the server JAR. */
@Mojo(name = "package-ui", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class PackageMojo extends AbstractMojo {
	private static final Pattern WEBPACK_OUTPUT_PATH = Pattern.compile("(?s)output\\s*:\\s*\\{.*?\\bpath\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']");
	private static final Pattern WEBPACK_RESOLVED_OUTPUT_PATH = Pattern.compile("(?s)output\\s*:\\s*\\{.*?\\bpath\\s*:\\s*path\\.resolve\\(\\s*__dirname\\s*,\\s*[\\\"']([^\\\"']+)[\\\"']\\s*\\)");
	@Parameter(defaultValue = "${project.basedir}", readonly = true)
	private File projectDirectory;
	@Parameter(defaultValue = "${project.basedir}/src", readonly = true)
	private File sourceDirectory;
	@Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
	private File outputDirectory;
	@Parameter(defaultValue = "${project.build.directory}", readonly = true)
	private File buildDirectory;
	@Parameter(property = "intino.npmExecutable", defaultValue = "npm")
	private String npmExecutable;
	@Parameter(property = "intino.buildScript", defaultValue = "build")
	private String buildScript;
	@Parameter(property = "intino.forceUiBuild", defaultValue = "false")
	private boolean forceUiBuild;

	@Override public void execute() throws MojoExecutionException {
		for (Path client : clientDirectories()) packageClient(client);
	}

	private List<Path> clientDirectories() throws MojoExecutionException {
		if (!Files.isDirectory(sourceDirectory.toPath())) return List.of();
		try (var files = Files.walk(sourceDirectory.toPath())) {
			var clients = new ArrayList<Path>();
			files.filter(path -> path.toString().endsWith(".konos") || path.toString().endsWith(".tara"))
				.forEach(path -> ImportMojo.servicesIn(path).forEach(service -> clients.add(projectDirectory.toPath().resolveSibling(ImportMojo.kebabCase(service)))));
			return clients.stream().distinct().toList();
		} catch (IOException exception) { throw new MojoExecutionException("Cannot inspect UI services in " + sourceDirectory, exception); }
	}

	private void packageClient(Path client) throws MojoExecutionException {
		if (!Files.isRegularFile(client.resolve("package.json"))) {
			getLog().info("No client package.json found at " + client + "; skipping UI package");
			return;
		}
		Path clientOutput = webpackOutputDirectory(client);
		Path fingerprint = buildDirectory.toPath().resolve("intino/ui").resolve(client.getFileName() + ".sha256");
		if (forceUiBuild || requiresBuild(client, clientOutput, fingerprint)) {
			getLog().info("Building client " + client);
			ToolRunner.runCommand(List.of(npmExecutable, "run", buildScript), client);
			writeFingerprint(client, fingerprint);
		} else getLog().info("Client " + client + " is up to date; skipping npm run " + buildScript);
		if (!Files.isDirectory(clientOutput))
			throw new MojoExecutionException("Client build did not create " + clientOutput);
		try { copy(clientOutput, outputDirectory.toPath().resolve("www").resolve(clientOutput.getFileName())); }
		catch (IOException exception) { throw new MojoExecutionException("Cannot include UI output from " + clientOutput, exception); }
	}

	private static boolean requiresBuild(Path client, Path output, Path fingerprintFile) throws MojoExecutionException {
		if (!Files.isDirectory(output)) return true;
		try {
			if (!Files.isRegularFile(fingerprintFile)) return true;
			return !Files.readString(fingerprintFile).equals(fingerprint(client));
		} catch (IOException exception) { throw new MojoExecutionException("Cannot inspect client build inputs in " + client, exception); }
	}

	private static void writeFingerprint(Path client, Path fingerprintFile) throws MojoExecutionException {
		try {
			Files.createDirectories(fingerprintFile.getParent());
			Files.writeString(fingerprintFile, fingerprint(client));
		} catch (IOException exception) { throw new MojoExecutionException("Cannot save UI build fingerprint for " + client, exception); }
	}

	private static String fingerprint(Path client) throws IOException, MojoExecutionException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (var paths = Files.walk(client)) {
				for (Path path : paths.filter(Files::isRegularFile).filter(path -> isBuildInput(client, path)).sorted().toList()) {
					digest.update(client.relativize(path).toString().getBytes());
					digest.update(Files.readAllBytes(path));
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) { throw new MojoExecutionException("SHA-256 is not available", exception); }
	}

	private static boolean isBuildInput(Path client, Path file) {
		Path relative = client.relativize(file);
		for (Path part : relative) {
			String name = part.toString();
			if (name.equals("node_modules") || name.equals(".git") || name.equals("target") || name.equals("out")) return false;
		}
		return true;
	}

	static Path webpackOutputDirectory(Path client) throws MojoExecutionException {
		Path webpack = client.resolve("webpack.config.js");
		try {
			String config = Files.readString(webpack);
			var literal = WEBPACK_OUTPUT_PATH.matcher(config);
			if (literal.find()) return Path.of(literal.group(1));
			var resolved = WEBPACK_RESOLVED_OUTPUT_PATH.matcher(config);
			if (resolved.find()) return client.resolve(resolved.group(1)).normalize();
			throw new MojoExecutionException("Cannot find output.path in " + webpack);
		} catch (IOException exception) { throw new MojoExecutionException("Cannot read " + webpack, exception); }
	}

	private static void copy(Path source, Path destination) throws IOException {
		try (var paths = Files.walk(source)) {
			for (Path path : paths.toList()) {
				Path target = destination.resolve(source.relativize(path));
				if (Files.isDirectory(path)) Files.createDirectories(target);
				else {
					Files.createDirectories(target.getParent());
					Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}
}
