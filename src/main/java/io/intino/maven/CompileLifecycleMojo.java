package io.intino.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Runs the current Maven project through compile, including Intino generation and javac. */
@Mojo(name = "compile", requiresProject = true, threadSafe = true)
@Execute(phase = LifecyclePhase.COMPILE)
public final class CompileLifecycleMojo extends AbstractMojo {
	@Override public void execute() throws MojoExecutionException {
		getLog().info("Compile completed: Maven executed the current project lifecycle through compile.");
	}
}
