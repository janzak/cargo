/*
 * ========================================================================
 *
 * Copyright 2005-2006 Vincent Massol.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ========================================================================
 */
package org.codehaus.cargo.container.glassfish;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.ExecuteWatchdog;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.taskdefs.PumpStreamHandler;
import org.codehaus.cargo.container.ContainerCapability;
import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.deployable.Deployable;
import org.codehaus.cargo.container.property.ServletPropertySet;
import org.codehaus.cargo.container.spi.AbstractInstalledLocalContainer;
import org.codehaus.cargo.util.CargoException;

/**
 * GlassFish installed local container.
 * 
 * @version $Id$
 */
public class GlassFishInstalledLocalContainer extends AbstractInstalledLocalContainer
{

    /**
     * Container capability instance.
     */
    private static final ContainerCapability CAPABILITY = new GlassFishContainerCapability();

    /**
     * Calls parent constructor, which saves the configuration.
     *
     * @param localConfiguration Configuration.
     */
    public GlassFishInstalledLocalContainer(LocalConfiguration localConfiguration)
    {
        super(localConfiguration);
    }

    /**
     * Invokes asadmin.
     *
     * @param async Asynchronous invoke?
     * @param args Invoke arguments.
     */
    /* package */ void invokeAsAdmin(boolean async, String[] args)
    {
        File exec = this.getAsadminExecutable();

        List cmds = new ArrayList();
        cmds.add(exec.getAbsolutePath());
        for (String arg : args)
        {
            cmds.add(arg);
        }

        try
        {
            Execute exe = new Execute(new PumpStreamHandler(), new ExecuteWatchdog(30 * 1000L));
            exe.setAntRun(new Project());
            String[] arguments = new String[cmds.size()];
            cmds.toArray(arguments);
            exe.setCommandline(arguments);
            if (async)
            {
                exe.spawn();
            }
            else
            {
                int exitCode = exe.execute();
                if (exitCode != 0)
                {
                    // the first token is the command
                    throw new CargoException(cmds + " failed. asadmin exited " + exitCode);
                }
            }
        }
        catch (IOException e)
        {
            throw new CargoException("Failed to invoke asadmin", e);
        }
    }

    /**
     * Gets the adadmin executable.
     *
     * @return The adadmin executable
     */
    private File getAsadminExecutable()
    {
        String home = this.getHome();
        if (home == null || !this.getFileHandler().isDirectory(home))
        {
            throw new CargoException("Glassfish home directory is not set");
        }

        File exec;

        if (File.pathSeparatorChar == ';')
        {
            // on Windows
            exec = new File(home, "bin/asadmin.bat");
        }
        else
        {
            // on other systems
            exec = new File(home, "bin/asadmin");
        }

        if (!exec.exists())
        {
            throw new CargoException("asadmin command not found at " + exec);
        }

        return exec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStart(Java java) throws Exception
    {
        this.getConfiguration().configure(this);

        this.getLogger().debug("Starting domain on HTTP port "
            + this.getConfiguration().getPropertyValue(ServletPropertySet.PORT)
            + " and admin port "
            + this.getConfiguration().getPropertyValue(GlassFishPropertySet.ADMIN_PORT),
            this.getClass().getName());

        // see https://glassfish.dev.java.net/issues/show_bug.cgi?id=885
        // needs to spawn
        this.invokeAsAdmin(true, new String[]
        {
            "start-domain",
            "--interactive=false",
            "--domaindir",
            this.getConfiguration().getHome(),
            "cargo-domain"
        });

        // to workaround GF bug, the above needs to be async,
        // so give it some time to make the admin port available
        Thread.sleep(20 * 1000);

        // deploy scheduled deployables
        GlassFishInstalledLocalDeployer deployer = new GlassFishInstalledLocalDeployer(this);
        for (Iterator iterator = this.getConfiguration().getDeployables().iterator(); iterator
            .hasNext();)
        {
            deployer.deploy((Deployable) iterator.next());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStop(Java java) throws Exception
    {
        this.invokeAsAdmin(false, new String[]
        {
            "stop-domain",
            "--domaindir",
            this.getConfiguration().getHome(),
            "cargo-domain"
        });
    }

    /**
     * {@inheritDoc}
     */
    public String getId()
    {
        return "glassfish";
    }

    /**
     * {@inheritDoc}
     */
    public String getName()
    {
        return "GlassFish";
    }

    /**
     * {@inheritDoc}
     */
    public ContainerCapability getCapability()
    {
        return CAPABILITY;
    }

}