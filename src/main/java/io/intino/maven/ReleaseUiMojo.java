package io.intino.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Packages and deploys only the declared UI clients using their generated Maven POMs. */
@Mojo(name = "release-ui", threadSafe = true)
public final class ReleaseUiMojo extends PackageMojo {
	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;
	@Parameter(property = "intino.mavenExecutable", defaultValue = "mvn")
	private String mavenExecutable;

	@Override public void execute() throws MojoExecutionException {
		if ("pom".equals(session.getCurrentProject().getPackaging())) {
			getLog().info("Omitiendo distribución UI en el POM agregador");
			return;
		}
		var distribution = session.getCurrentProject().getDistributionManagement();
		if (distribution == null || (distribution.getRepository() == null && distribution.getSnapshotRepository() == null))
			throw new MojoExecutionException("Configure distributionManagement in the server POM before releasing UI clients");
		var poms = packageClients(false);
		if (poms.isEmpty()) getLog().info("No hay clientes UI para distribuir en este módulo");
		for (Path pom : poms) {
			getLog().info("Distribuyendo cliente UI con " + pom);
			ToolRunner.runCommand(deployCommand(pom), pom.toAbsolutePath().getParent());
		}
	}

	private List<String> deployCommand(Path pom) {
		var command = new ArrayList<String>();
		command.add(mavenExecutable); command.add("--batch-mode");
		command.add("-f"); command.add(pom.toAbsolutePath().toString());
		var request = session.getRequest();
		settings(command, "--settings", request.getUserSettingsFile());
		settings(command, "--global-settings", request.getGlobalSettingsFile());
		if (request.getLocalRepositoryPath() != null) command.add("-Dmaven.repo.local=" + request.getLocalRepositoryPath());
		if (request.isOffline()) command.add("--offline");
		command.add("deploy");
		return command;
	}

	private static void settings(List<String> command, String option, File file) {
		if (file != null && file.isFile()) { command.add(option); command.add(file.getAbsolutePath()); }
	}
}
