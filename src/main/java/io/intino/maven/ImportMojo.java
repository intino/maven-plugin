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
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regenerates Maven metadata for this server module and its declared UI siblings. */
@Mojo(name = "import", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public final class ImportMojo extends AbstractMojo {
	private static final Pattern WEB_ARTIFACT = Pattern.compile("WebArtifact\\(\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"([^\\\"]+)\\\"\\s*\\)\\s*([^\\s]+)");
	private static final Pattern UI_SERVICE = Pattern.compile("(?m)^\\s*Service(?:\\([^\\r\\n]*\\))?\\s+([A-Za-z][A-Za-z0-9_]*)\\s+(?:as|is)\\s+UI\\b");
	private static final Pattern GENERATION_PACKAGE = Pattern.compile("(?s)Builder.*?generationPackage\\s*=\\s*\\\"([^\\\"]+)\\\"");
	private static final Pattern PARAMETER = Pattern.compile("Parameter\\s*\\(\\s*\\\"([^\\\"]+)\\\"\\s*\\)");
	private static final Pattern IDEA_OUTPUT = Pattern.compile("<output\\s+url=\\\"file://\\$PROJECT_DIR\\$/([^\\\"]+)\\\"");

	/** Optional extra legacy client module, in addition to modules inferred from Service as UI. */
	@Parameter
	private File legioDirectory;
	@Parameter(defaultValue = "${project.basedir}", readonly = true)
	private File projectDirectory;
	@Parameter(defaultValue = "${project.build.directory}", readonly = true)
	private File buildDirectory;
	@Parameter(defaultValue = "${project.basedir}/../project.legio")
	private File projectFile;
	@Parameter
	private File credentialsFile;
	@Parameter(required = true)
	private String legio2mavenVersion;
	@Parameter(required = true)
	private String legio2packagejsonVersion;
	@Parameter(required = true)
	private String intinoBuilderVersion;
	@Parameter(defaultValue = "1.0.0-SNAPSHOT")
	private String intinoMavenVersion;
	@Parameter(defaultValue = "21")
	private String javaVersion;
	@Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
	private ArtifactRepository localRepository;
	@Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
	private List<ArtifactRepository> repositories;
	@Component
	private RepositorySystem repositorySystem;

	@Override public void execute() throws MojoExecutionException {
		scan(projectDirectory.toPath());
		for (Path client : clientDirectories()) {
			if (Files.isDirectory(client)) scan(client);
			else getLog().info("No client module found at " + client + "; skipping client import");
		}
	}

	private List<Path> clientDirectories() throws MojoExecutionException {
		var directories = new ArrayList<Path>();
		if (legioDirectory != null) directories.add(legioDirectory.toPath());
		try (var files = Files.walk(projectDirectory.toPath().resolve("src"))) {
			files.filter(path -> path.toString().endsWith(".konos") || path.toString().endsWith(".tara"))
				.forEach(path -> servicesIn(path).forEach(service -> directories.add(projectDirectory.toPath().resolveSibling(kebabCase(service)))));
		} catch (IOException exception) {
			throw new MojoExecutionException("Cannot inspect UI services in " + projectDirectory, exception);
		}
		return directories.stream().distinct().toList();
	}

	static List<String> servicesIn(Path model) {
		try {
			var matcher = UI_SERVICE.matcher(Files.readString(model));
			var services = new ArrayList<String>();
			while (matcher.find()) services.add(matcher.group(1));
			return services;
		} catch (IOException exception) { throw new IllegalStateException("Cannot read " + model, exception); }
	}

	static String kebabCase(String value) {
		return value.replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
			.replaceAll("([a-z0-9])([A-Z])", "$1-$2").replace('_', '-').toLowerCase(Locale.ROOT);
	}

	private void scan(Path directory) throws MojoExecutionException {
		Path legio = directory.resolve("artifact.legio");
		if (Files.isRegularFile(legio)) convert(legio);
		else getLog().debug("No artifact.legio found in module " + directory);
	}

	private void convert(Path legio) throws MojoExecutionException {
		String content;
		try { content = Files.readString(legio); }
		catch (IOException exception) { throw new MojoExecutionException("Cannot read " + legio, exception); }
		boolean client = content.contains("WebImports");
		if (!client) saveKonosSettings(content);
		File converter = resolve("io.intino", client ? "legio2packagejson" : "legio2maven", client ? legio2packagejsonVersion : legio2mavenVersion);
		if (client) convertClient(converter, legio.getParent());
		else convertServer(converter, legio.getParent(), content);
		if (client) unpackWebArtifacts(content, legio.getParent());
	}

	private void convertServer(File converter, Path moduleDirectory, String legio) throws MojoExecutionException {
		var arguments = new ArrayList<String>();
		arguments.add(moduleDirectory.toString());
		if (projectFile.isFile()) arguments.add(projectFile.toString());
		ToolRunner.run(converter, moduleDirectory, arguments);
		if (!Files.isRegularFile(moduleDirectory.resolve("pom.xml")))
			throw new MojoExecutionException("legio2maven did not create pom.xml in " + moduleDirectory);
		configureGeneratedPom(moduleDirectory.resolve("pom.xml"), legio);
		relocateKonosCache(moduleDirectory);
	}

	private void configureGeneratedPom(Path pom, String legio) throws MojoExecutionException {
		try (Reader input = Files.newBufferedReader(pom)) {
			var model = new MavenXpp3Reader().read(input);
			if (model.getProperties() == null) model.setProperties(new Properties());
			model.getProperties().putIfAbsent("maven.compiler.release", javaVersion);
			if (model.getBuild() == null) model.setBuild(new org.apache.maven.model.Build());
			configureBuildOutput(model.getBuild(), model.getArtifactId());
			Plugin plugin = model.getBuild().getPlugins().stream()
				.filter(candidate -> "io.intino".equals(candidate.getGroupId()) &&
					("intino-maven-plugin".equals(candidate.getArtifactId()) || "intino-maven".equals(candidate.getArtifactId())))
				.findFirst().orElseGet(() -> { var created = new Plugin(); model.getBuild().addPlugin(created); return created; });
			plugin.setGroupId("io.intino");
			plugin.setArtifactId("intino-maven-plugin");
			plugin.setVersion(intinoMavenVersion);
			plugin.setConfiguration(configuration(legio));
			migrateLifecycleGoals(plugin);
			if (plugin.getExecutions().isEmpty()) {
				var execution = new PluginExecution();
				execution.addGoal("import"); execution.addGoal("export"); execution.addGoal("generate");
				plugin.addExecution(execution);
			}
			ensurePackageExecution(plugin);
			try (Writer output = Files.newBufferedWriter(pom)) { new MavenXpp3Writer().write(output, model); }
		} catch (Exception exception) { throw new MojoExecutionException("Cannot add intino-maven-plugin to " + pom, exception); }
	}

	private void migrateLifecycleGoals(Plugin plugin) {
		for (PluginExecution execution : plugin.getExecutions()) {
			if (execution.getGoals().remove("compile") && !execution.getGoals().contains("generate")) execution.addGoal("generate");
			if (execution.getGoals().remove("package") && !execution.getGoals().contains("package-ui")) execution.addGoal("package-ui");
		}
	}

	private void ensurePackageExecution(Plugin plugin) {
		if (plugin.getExecutions().stream().anyMatch(execution -> execution.getGoals().contains("package-ui"))) return;
		var execution = new PluginExecution();
		execution.setId("intino-package-ui");
		execution.setPhase("prepare-package");
		execution.addGoal("package-ui");
		plugin.addExecution(execution);
	}

	private void configureBuildOutput(org.apache.maven.model.Build build, String artifactId) {
		Path output = projectOutputDirectory();
		build.setDirectory(output.resolve("build").resolve(artifactId).toString());
		build.setOutputDirectory(output.resolve("production").resolve(artifactId).toString());
		build.setTestOutputDirectory(output.resolve("test").resolve(artifactId).toString());
	}

	private Path projectOutputDirectory() {
		Path root = intinoProjectDirectory();
		Path idea = root.resolve(".idea/misc.xml");
		try {
			if (Files.isRegularFile(idea)) {
				var matcher = IDEA_OUTPUT.matcher(Files.readString(idea));
				if (matcher.find()) return root.resolve(matcher.group(1));
			}
		} catch (IOException ignored) { getLog().debug("Cannot read project output configuration from " + idea); }
		return root.resolve("out");
	}

	private Path intinoProjectDirectory() {
		if (projectFile != null && projectFile.isFile()) return projectFile.toPath().toAbsolutePath().getParent();
		Path current = projectDirectory.toPath().toAbsolutePath();
		while (current != null) {
			if (Files.isDirectory(current.resolve(".intino")) || Files.isRegularFile(current.resolve("project.legio"))) return current;
			current = current.getParent();
		}
		return projectDirectory.toPath().toAbsolutePath();
	}

	private void relocateKonosCache(Path moduleDirectory) throws MojoExecutionException {
		Path source = moduleDirectory.resolve("konos");
		if (!Files.isDirectory(source)) return;
		Path target = intinoProjectDirectory().resolve(".intino/Konos");
		try (var paths = Files.walk(source)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				Path output = target.resolve(source.relativize(path));
				Files.createDirectories(output.getParent());
				Files.move(path, output, StandardCopyOption.REPLACE_EXISTING);
			}
			try (var directories = Files.walk(source)) {
				directories.sorted((left, right) -> right.getNameCount() - left.getNameCount()).forEach(directory -> {
					try { Files.deleteIfExists(directory); }
					catch (IOException exception) { throw new IllegalStateException(exception); }
				});
			}
		} catch (IOException | IllegalStateException exception) {
			throw new MojoExecutionException("Cannot move Konos cache from " + source + " to " + target, exception);
		}
	}

	private Xpp3Dom configuration(String legio) {
		var root = new Xpp3Dom("configuration");
		add(root, "legio2mavenVersion", legio2mavenVersion);
		add(root, "legio2packagejsonVersion", legio2packagejsonVersion);
		add(root, "intinoBuilderVersion", intinoBuilderVersion);
		add(root, "javaVersion", javaVersion);
		var generation = GENERATION_PACKAGE.matcher(legio);
		if (generation.find()) add(root, "generationPackage", generation.group(1));
		var parameters = new ArrayList<String>();
		var parameter = PARAMETER.matcher(legio);
		while (parameter.find()) parameters.add(parameter.group(1));
		if (!parameters.isEmpty()) add(root, "moduleParameters", String.join(";", parameters));
		return root;
	}

	private static void add(Xpp3Dom parent, String name, String value) {
		var child = new Xpp3Dom(name); child.setValue(value); parent.addChild(child);
	}

	private void saveKonosSettings(String legio) throws MojoExecutionException {
		Path settings = buildDirectory.toPath().resolve("intino/konos.properties");
		try {
			Files.createDirectories(settings.getParent());
			var properties = new Properties();
			if (Files.isRegularFile(settings)) try (var input = Files.newInputStream(settings)) { properties.load(input); }
			var generation = GENERATION_PACKAGE.matcher(legio);
			if (generation.find()) properties.setProperty("generationPackage", generation.group(1));
			var parameters = new ArrayList<String>();
			var parameter = PARAMETER.matcher(legio);
			while (parameter.find()) parameters.add(parameter.group(1));
			if (!parameters.isEmpty()) properties.setProperty("moduleParameters", String.join(";", parameters));
			try (var output = Files.newOutputStream(settings)) { properties.store(output, "Imported Konos settings"); }
		} catch (IOException exception) { throw new MojoExecutionException("Cannot save Konos builder settings", exception); }
	}

	private void convertClient(File converter, Path moduleDirectory) throws MojoExecutionException {
		Path credentials = credentialsFile == null ? projectDirectory.toPath().resolve("target/intino/credentials") : credentialsFile.toPath();
		try {
			Files.createDirectories(credentials.getParent());
			if (!Files.exists(credentials)) Files.createFile(credentials);
		} catch (IOException exception) { throw new MojoExecutionException("Cannot create credentials file " + credentials, exception); }
		ToolRunner.run(converter, moduleDirectory, List.of(moduleDirectory.toString(), projectFile.toString(), credentials.toString()));
		if (!Files.isRegularFile(moduleDirectory.resolve("package.json")) || !Files.isRegularFile(moduleDirectory.resolve("pnpm-workspace.yaml")))
			throw new MojoExecutionException("legio2packagejson did not create package.json and pnpm-workspace.yaml in " + moduleDirectory);
	}

	private void unpackWebArtifacts(String legio, Path clientDirectory) throws MojoExecutionException {
		Matcher matcher = WEB_ARTIFACT.matcher(legio);
		while (matcher.find()) {
			var dependency = new WebArtifact(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
			File jar = resolve(dependency.groupId(), dependency.artifactId(), dependency.version());
			String directory = dependency.packageName() + "-" + dependency.version();
			Path target = clientDirectory.resolve(".intino/web-artifacts").resolve(directory);
			unpack(jar.toPath(), target);
			try { PackageJson.addLocalDependency(clientDirectory.resolve("package.json"), dependency.packageName(), ".intino/web-artifacts/" + directory); }
			catch (IOException exception) { throw new MojoExecutionException("Cannot add local WebArtifact dependency " + dependency.packageName(), exception); }
			recordWebArtifact(clientDirectory, dependency);
			getLog().info("Resolved WebArtifact " + dependency.groupId() + ":" + dependency.artifactId() + ":" + dependency.version() + " as local dependency " + target);
		}
	}

	private void recordWebArtifact(Path clientDirectory, WebArtifact dependency) throws MojoExecutionException {
		Path file = buildDirectory.toPath().resolve("intino/web-artifacts.tsv");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, clientDirectory + "\t" + dependency.groupId() + "\t" + dependency.artifactId() + "\t" + dependency.version() + "\t" + dependency.packageName() + "\n",
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException exception) { throw new MojoExecutionException("Cannot install WebArtifact " + dependency.artifactId(), exception); }
	}

	private File resolve(String groupId, String artifactId, String version) throws MojoExecutionException {
		try {
			Artifact artifact = repositorySystem.createArtifact(groupId, artifactId, version, "jar");
			var result = repositorySystem.resolve(new ArtifactResolutionRequest()
				.setArtifact(artifact).setRemoteRepositories(repositories).setLocalRepository(localRepository));
			if (!result.isSuccess()) throw new MojoExecutionException("Unable to resolve " + groupId + ":" + artifactId + ":" + version + ": " + result.getExceptions());
			return artifact.getFile();
		} catch (Exception exception) {
			throw new MojoExecutionException("Cannot resolve " + groupId + ":" + artifactId + ":" + version + ". Declare its repository in the consuming POM.", exception);
		}
	}

	static void unpack(Path archive, Path target) throws MojoExecutionException {
		try (JarFile jar = new JarFile(archive.toFile())) {
			var entries = jar.entries();
			while (entries.hasMoreElements()) {
				var entry = entries.nextElement();
				Path output = target.resolve(entry.getName()).normalize();
				if (!output.startsWith(target)) throw new MojoExecutionException("Unsafe entry in " + archive + ": " + entry.getName());
				if (entry.isDirectory()) Files.createDirectories(output);
				else { Files.createDirectories(output.getParent()); try (InputStream in = jar.getInputStream(entry)) { Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING); } }
			}
		} catch (IOException exception) { throw new MojoExecutionException("Cannot unpack " + archive, exception); }
	}
}
