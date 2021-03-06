/*
 * Copyright (c) 2012, 2013 Hemanta Sapkota.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Hemanta Sapkota (laex.pearl@gmail.com)
 */
package com.laex.j2objc;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.Target;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import com.laex.j2objc.util.LogUtil;
import com.laex.j2objc.util.MessageUtil;

/**
 * The Class AntDelegate.
 */
public class AntDelegate {

    /** The java project. */
    private IJavaProject javaProject;
    private String sourceDir;
    private String destinationDir;

    /**
     * The Class EclipeConsoleBuildLogger.
     */
    class EclipeConsoleBuildLogger extends DefaultLogger {

        /** The msg console. */
        private MessageConsole msgConsole;

        /** The msg console stream. */
        private MessageConsoleStream msgConsoleStream;

        /**
         * Instantiates a new eclipe console build logger.
         * 
         * @param display
         *            the display
         * @param msgConsole
         *            the msg console
         */
        public EclipeConsoleBuildLogger(Display display, MessageConsole msgConsole) {
            super();

            setMessageOutputLevel(Project.MSG_ERR);
            setErrorPrintStream(System.err);
            setOutputPrintStream(System.out);

            this.msgConsole = msgConsole;
            msgConsoleStream = this.msgConsole.newMessageStream();
            msgConsoleStream.setActivateOnWrite(true);

            MessageUtil.setConsoleColor(display, msgConsoleStream, SWT.COLOR_BLUE);
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.apache.tools.ant.DefaultLogger#targetStarted(org.apache.tools
         * .ant.BuildEvent)
         */
        @Override
        public void targetStarted(BuildEvent event) {
            super.targetStarted(event);
            try {
                Target target = event.getTarget();

                if (isExportTarget(target)) {
                    msgConsoleStream.write("Exporting ObjectiveC Files");
                    msgConsoleStream.write(MessageUtil.NEW_LINE_CONSTANT);
                    msgConsoleStream.write("Source Directory: " + sourceDir);
                    msgConsoleStream.write(MessageUtil.NEW_LINE_CONSTANT);
                    msgConsoleStream.write("Destination Directory: " + destinationDir);
                    msgConsoleStream.write(MessageUtil.NEW_LINE_CONSTANT);
                }

                if (isTargetCleanup(target)) {
                    msgConsoleStream.write("Cleans up internally generated files (<<project_name>>-classpath and <<project_name>>-prefix).");
                    msgConsoleStream.write(MessageUtil.NEW_LINE_CONSTANT);
                    msgConsoleStream.write("Does not clean J2OBJC generated source files.");
                    msgConsoleStream.write(MessageUtil.NEW_LINE_CONSTANT);
                    msgConsoleStream.write("Cleaning up project: " + javaProject.getElementName());
                    msgConsoleStream.write(MessageUtil.NEW_LINE_CONSTANT);
                }

            } catch (IOException e) {
                LogUtil.logException(e);
            }
        }

        private boolean isTargetCleanup(Target target) {
            return target.getName().equals("CLEANUP");
        }

        private boolean isExportTarget(Target target) {
            return target.getName().equals("Export-ObjectiveC-Files");
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.apache.tools.ant.DefaultLogger#targetFinished(org.apache.tools
         * .ant.BuildEvent)
         */
        @Override
        public void targetFinished(BuildEvent event) {
            super.targetFinished(event);
            try {

                Target target = event.getTarget();

                if (isExportTarget(target)) {
                    msgConsoleStream.write("Export finished.");
                    msgConsoleStream.write(MessageUtil.NEW_LINE_CONSTANT);
                }

                if (isTargetCleanup(target)) {
                    msgConsoleStream.write("Cleanup finished");
                    msgConsoleStream.write(MessageUtil.NEW_LINE_CONSTANT);
                }

            } catch (IOException e) {
                LogUtil.logException(e);
            }
        }

    }

    /**
     * Instantiates a new ant delegate.
     * 
     * @param javaProject
     *            the java project
     * @param sourceDir
     *            the source dir
     * @param destinationDir
     *            the destination dir
     */
    public AntDelegate(IJavaProject javaProject) {
        this.javaProject = javaProject;
    }

    /**
     * Execute export.
     * 
     * @param display
     *            the display
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws CoreException
     *             the core exception
     */
    public void executeExport(Display display, String sourceDir, String destinationDir) throws IOException, CoreException {
        // Resolve file from the plugin
        this.sourceDir = sourceDir;
        this.destinationDir = destinationDir;

        URL url = new URL("platform:/plugin/j2objc-eclipse-plugin/exportANT.xml");
        InputStream is = url.openConnection().getInputStream();
        IFile tmpFile = javaProject.getProject().getFile(".exportANT.xml");
        if (!tmpFile.exists()) {
            tmpFile.create(is, false, null);
        }

        String tmpAntFile = tmpFile.getLocation().makeAbsolute().toOSString();

        Project exportObjCFilesProject = new Project();

        exportObjCFilesProject.setUserProperty("ant.file", tmpAntFile);
        exportObjCFilesProject.init();

        ProjectHelper helper = ProjectHelper.getProjectHelper();
        exportObjCFilesProject.addReference("ant.projectHelper", helper);

        exportObjCFilesProject.setProperty("EXPORT_DIRECTORY", destinationDir);
        exportObjCFilesProject.setProperty("SOURCE_DIRECTORY", sourceDir);

        MessageConsole console = MessageUtil.findConsole(MessageUtil.J2OBJC_CONSOLE);

        exportObjCFilesProject.addBuildListener(new EclipeConsoleBuildLogger(display, console));

        helper.parse(exportObjCFilesProject, tmpFile.getLocation().toFile());

        exportObjCFilesProject.executeTarget(exportObjCFilesProject.getDefaultTarget());

        tmpFile.delete(true, null);
    }

    public void executeCleanup(Display display) throws IOException, CoreException {
        URL url = new URL("platform:/plugin/j2objc-eclipse-plugin/exportANT.xml");
        InputStream is = url.openConnection().getInputStream();
        IFile tmpFile = javaProject.getProject().getFile(".exportANT.xml");
        if (!tmpFile.exists()) {
            tmpFile.create(is, false, null);
        }

        String tmpAntFile = tmpFile.getLocation().makeAbsolute().toOSString();

        Project cleanupProject = new Project();

        cleanupProject.setUserProperty("ant.file", tmpAntFile);
        cleanupProject.init();

        ProjectHelper helper = ProjectHelper.getProjectHelper();
        cleanupProject.addReference("ant.projectHelper", helper);

        cleanupProject.setProperty("PROJECT_NAME", javaProject.getElementName());

        MessageConsole console = MessageUtil.findConsole(MessageUtil.J2OBJC_CONSOLE);
        console.clearConsole();

        cleanupProject.addBuildListener(new EclipeConsoleBuildLogger(display, console));

        helper.parse(cleanupProject, tmpFile.getLocation().toFile());

        Target target = (Target) cleanupProject.getTargets().get("CLEANUP");
        cleanupProject.executeTarget(target.getName());

        tmpFile.delete(true, null);
    }
}
