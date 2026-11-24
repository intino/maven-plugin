package io.intino.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Runs Maven's default lifecycle through deploy for the current Maven project. */
@Mojo(name = "release", requiresProject = true, threadSafe = true)
@Execute(phase = LifecyclePhase.DEPLOY)
public final class ReleaseMojo extends AbstractMojo {
	@Override public void execute() throws MojoExecutionException {
		getLog().info("Release completed: Maven executed the current project lifecycle through deploy.");
	}
}
