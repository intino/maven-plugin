package io.intino.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Runs the current Maven project through package, including UI assets and the JAR. */
@Mojo(name = "package", requiresProject = true, threadSafe = true)
@Execute(phase = LifecyclePhase.PACKAGE)
public final class PackageLifecycleMojo extends AbstractMojo {
	@Override public void execute() throws MojoExecutionException {
		getLog().info("Package completed: Maven executed the current project lifecycle through package.");
	}
}
