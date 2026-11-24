package io.intino.maven;

import org.apache.maven.model.Model;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Creates a Maven multi-module project POM for Intino server modules. */
@Mojo(name = "init", requiresProject = false, threadSafe = true)
public final class InitMojo extends AbstractMojo {
	@Parameter(property = "intino.directory", defaultValue = "${user.dir}")
	private File directory;
	@Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
	private MojoExecution mojoExecution;
	@Parameter(property = "intino.legio2mavenVersion", defaultValue = "1.2.6")
	private String legio2mavenVersion;
	@Parameter(property = "intino.legio2packagejsonVersion", defaultValue = "1.3.5")
	private String legio2packagejsonVersion;
	@Parameter(defaultValue = "io.intino")
	private String groupId;
	@Parameter(defaultValue = "1.0.0-SNAPSHOT")
	private String version;

	@Override public void execute() throws MojoExecutionException {
		Path root = directory.toPath().toAbsolutePath();
		Path pom = root.resolve("pom.xml");
		if (Files.exists(pom)) {
			try (var input = Files.newBufferedReader(pom)) {
				Model model = new MavenXpp3Reader().read(input);
				for (String module : model.getModules()) bootstrap(root.resolve(module).normalize(), model);
				return;
			} catch (Exception exception) { throw new MojoExecutionException("Cannot initialize modules from " + pom, exception); }
		}
		try (var children = Files.list(root)) {
			var modules = children.filter(Files::isDirectory)
				.filter(child -> isServer(child))
				.map(child -> child.getFileName().toString()).sorted().toList();
			if (modules.isEmpty()) throw new MojoExecutionException("No Maven server modules with artifact.legio found below " + root);
			var model = new Model();
			model.setModelVersion("4.0.0"); model.setGroupId(groupId); model.setArtifactId(root.getFileName().toString());
			model.setVersion(version); model.setPackaging("pom"); modules.forEach(model::addModule);
			model.addPluginRepository(repository());
			var plugin = new Plugin();
			plugin.setGroupId("io.intino"); plugin.setArtifactId("intino-maven-plugin"); plugin.setVersion(mojoExecution.getVersion());
			plugin.setInherited(false);
			model.setBuild(new Build()); model.getBuild().addPlugin(plugin);
			for (String module : modules) bootstrap(root.resolve(module), model);
			try (Writer output = Files.newBufferedWriter(pom)) { new MavenXpp3Writer().write(output, model); }
			getLog().info("Creado POM del proyecto con varios módulos: " + String.join(", ", modules));
		} catch (IOException exception) { throw new MojoExecutionException("No se puede crear el POM del proyecto con varios módulos en " + root, exception); }
	}

	private static boolean isServer(Path module) {
		try {
			Path artifact = module.resolve("artifact.legio");
			return Files.isRegularFile(artifact) && !Files.readString(artifact).contains("WebImports");
		} catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
	}

	static String konosVersion(String artifact) throws MojoExecutionException {
		var declarations = Pattern.compile("\\bDsl\\s*\\(([^)]*)\\)", Pattern.DOTALL).matcher(artifact);
		while (declarations.find()) {
			String attributes = declarations.group(1);
			if (!Pattern.compile("\\bname\\s*=\\s*\"Konos\"").matcher(attributes).find()) continue;
			var version = Pattern.compile("\\bversion\\s*=\\s*\"([^\"]+)\"").matcher(attributes);
			if (version.find()) return version.group(1);
		}
		throw new MojoExecutionException("Missing Dsl(name=\"Konos\", version=\"...\") in artifact.legio");
	}

	private void bootstrap(Path module, Model reactor) throws IOException, MojoExecutionException {
		Path pom = module.resolve("pom.xml");
		if (Files.exists(pom)) { getLog().info("Keeping existing " + pom); return; }
		if (!isServer(module)) { getLog().warn("Skipping module without server artifact.legio: " + module); return; }
		String builder = konosVersion(Files.readString(module.resolve("artifact.legio")));
		var model = new Model();
		model.setModelVersion("4.0.0"); model.setGroupId(reactor.getGroupId() == null ? groupId : reactor.getGroupId());
		model.setArtifactId(module.getFileName().toString()); model.setVersion(reactor.getVersion() == null ? version : reactor.getVersion());
		model.addRepository(repository()); model.addPluginRepository(repository());
		var plugin = new Plugin();
		plugin.setGroupId("io.intino"); plugin.setArtifactId("intino-maven-plugin"); plugin.setVersion(mojoExecution.getVersion());
		var configuration = new Xpp3Dom("configuration");
		setting(configuration, "legio2mavenVersion", legio2mavenVersion);
		setting(configuration, "legio2packagejsonVersion", legio2packagejsonVersion);
		setting(configuration, "intinoBuilderVersion", builder);
		plugin.setConfiguration(configuration);
		model.setBuild(new Build()); model.getBuild().addPlugin(plugin);
		try (Writer output = Files.newBufferedWriter(pom, java.nio.file.StandardOpenOption.CREATE_NEW)) { new MavenXpp3Writer().write(output, model); }
		getLog().info("Created " + pom + " with Konos " + builder + "; run intino:import to migrate it");
	}

	private static Repository repository() {
		var repository = new Repository(); repository.setId("intino-maven");
		repository.setUrl("https://artifactory.intino.io/artifactory/release-libraries"); return repository;
	}

	private static void setting(Xpp3Dom root, String name, String value) {
		var child = new Xpp3Dom(name); child.setValue(value); root.addChild(child);
	}
}
