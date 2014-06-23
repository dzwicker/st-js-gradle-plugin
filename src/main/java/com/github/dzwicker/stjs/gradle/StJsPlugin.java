package com.github.dzwicker.stjs.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.SourceSet;

import java.io.File;

@SuppressWarnings("UnusedDeclaration")
public class StJsPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        boolean javaPlugin = project.getPlugins().hasPlugin(JavaPlugin.class);
        boolean warPlugin = project.getPlugins().hasPlugin(WarPlugin.class);

        final Logger logger = project.getLogger();
        if (!(javaPlugin && warPlugin)) {
            logger.error("st-js plugin can only be applied if jar or war plugin is applied, too!");
            throw new IllegalStateException("st-js plugin can only be applied if jar or war plugin is applied, too!");
        }

        final JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        final SourceSet main = javaPluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final SourceDirectorySet allJava = main.getAllJava();
        if (allJava.getSrcDirs().size() != 1) {
            throw new IllegalStateException("Only a single source directory is supported!");
        }

        final GenerateStJsTask task = project.getTasks().create("stjs", GenerateStJsTask.class);
        task.setClasspath(
            main.getCompileClasspath()
        );
        task.setWar(warPlugin);
        File generatedSourcesDirectory;
        if (warPlugin) {
            generatedSourcesDirectory = new File(project.getBuildDir(), "stjs");
            project.getTasks().getByPath(WarPlugin.WAR_TASK_NAME).dependsOn(task);
        } else {
            generatedSourcesDirectory = main.getOutput().getClassesDir();
            project.getTasks().getByPath(JavaPlugin.JAR_TASK_NAME).dependsOn(task);
        }
        task.setGeneratedSourcesDirectory(generatedSourcesDirectory);
        task.setCompileSourceRoots(allJava);
        task.setOutput(main.getOutput());
    }
}
