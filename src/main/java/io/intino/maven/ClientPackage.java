package io.intino.maven;

import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/** A WebArtifact is a Maven JAR containing the client's source package. */
final class ClientPackage {
	private static final List<String> INPUTS = List.of("src", "gen", "res", "package.json");
	private ClientPackage() { }

	static Path create(Path client, Path destination, DistributionManagement distribution) throws IOException {
		String legio = Files.readString(client.resolve("artifact.legio"));
		var artifact = Pattern.compile("\\bArtifact\\s*\\(([^)]*)\\)\\s*([A-Za-z0-9_.-]+)", Pattern.DOTALL).matcher(legio);
		if (!artifact.find()) throw new IOException("Missing Artifact declaration in " + client.resolve("artifact.legio"));
		String group = attribute(artifact.group(1), "groupId");
		String version = attribute(artifact.group(1), "version");
		String name = artifact.group(2);
		if (!name.matches("[A-Za-z0-9_.-]+") || !version.matches("[A-Za-z0-9_.-]+")) throw new IOException("Invalid client Maven coordinates");
		Files.createDirectories(destination);
		Path pom = destination.resolve("pom.xml");
		var model = new Model();
		model.setModelVersion("4.0.0"); model.setGroupId(group); model.setArtifactId(name); model.setVersion(version); model.setPackaging("jar");
		model.setName(name);
		if (distribution != null) model.setDistributionManagement(distribution.clone());
		var build = new Build(); model.setBuild(build);
		build.setSourceDirectory(destination.resolve("no-java-sources").toAbsolutePath().toString());
		build.setTestSourceDirectory(destination.resolve("no-java-tests").toAbsolutePath().toString());
		build.setDirectory(destination.resolve("target").toAbsolutePath().toString());
		var resource = new Resource(); resource.setDirectory(client.toAbsolutePath().toString());
		INPUTS.forEach(input -> resource.addInclude(input.equals("package.json") ? input : input + "/**"));
		build.addResource(resource);
		var jar = new Plugin(); jar.setGroupId("org.apache.maven.plugins"); jar.setArtifactId("maven-jar-plugin"); jar.setVersion("3.4.1");
		var execution = new PluginExecution(); execution.setId("client-sources"); execution.setPhase("package"); execution.addGoal("jar");
		var configuration = new Xpp3Dom("configuration"); var classifier = new Xpp3Dom("classifier"); classifier.setValue("sources"); configuration.addChild(classifier);
		execution.setConfiguration(configuration); jar.addExecution(execution); build.addPlugin(jar);
		try (var writer = Files.newBufferedWriter(pom)) { new MavenXpp3Writer().write(writer, model); }
		Path target = destination.resolve("target"); Files.createDirectories(target);
		writeJar(client, target.resolve(name + "-" + version + ".jar"), pom, group, name, version);
		writeJar(client, target.resolve(name + "-" + version + "-sources.jar"), pom, group, name, version);
		return pom;
	}

	private static String attribute(String declaration, String name) throws IOException {
		var matcher = Pattern.compile("\\b" + name + "\\s*=\\s*\"([^\"]+)\"").matcher(declaration);
		if (!matcher.find()) throw new IOException("Missing " + name + " in client Artifact declaration");
		return matcher.group(1);
	}

	private static void writeJar(Path client, Path jar, Path pom, String group, String artifact, String version) throws IOException {
		var manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().putValue("Implementation-Title", artifact);
		manifest.getMainAttributes().putValue("Implementation-Version", version);
		try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
			String metadata = "META-INF/maven/" + group + "/" + artifact + "/";
			entry(output, metadata + "pom.xml", Files.readAllBytes(pom));
			var properties = new Properties(); properties.setProperty("groupId", group); properties.setProperty("artifactId", artifact); properties.setProperty("version", version);
			var bytes = new java.io.ByteArrayOutputStream(); properties.store(bytes, "Maven coordinates");
			entry(output, metadata + "pom.properties", bytes.toByteArray());
			for (String input : INPUTS) {
				Path source = client.resolve(input);
				if (!Files.exists(source)) continue;
				if (Files.isRegularFile(source)) entry(output, input, Files.readAllBytes(source));
				else try (var files = Files.walk(source)) {
					for (Path file : files.filter(Files::isRegularFile).sorted().toList()) entry(output, client.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
				}
			}
		}
	}

	private static void entry(JarOutputStream output, String name, byte[] bytes) throws IOException {
		output.putNextEntry(new JarEntry(name)); output.write(bytes); output.closeEntry();
	}
}
