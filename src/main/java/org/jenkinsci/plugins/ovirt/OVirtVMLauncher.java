package org.jenkinsci.plugins.ovirt;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Extension point to allow control over how Computers are "launched", meaning how they get connected to their slave
 * agent program. In this case this class will determine how to start and run slave.jar on the vm running in the ovirt
 * server
 *
 * So when the user press 'Launch Agent' or a job is run, this class will be used. I hope.
 *
 * @See <a href='http://javadoc.jenkins-ci.org/hudson/slaves/ComputerLauncher.html'>
 */
public class OVirtVMLauncher extends ComputerLauncher {

    private ComputerLauncher delegate;
    private String hypervisorDescription;
    private String virtualMachineName;
    private String snapshotName;
    private final int WAITING_TIME_SECS;

    @DataBoundConstructor
    public OVirtVMLauncher(ComputerLauncher delegate, String hypervisorDescription, String virtualMachineName,
                           String snapshotName, int waitingTimeSecs) {
        super();
        this.delegate = delegate;
        this.hypervisorDescription = hypervisorDescription;
        this.virtualMachineName = virtualMachineName;
        this.snapshotName = snapshotName;
        this.WAITING_TIME_SECS = secToMilliseconds(waitingTimeSecs);
    }

    private static int secToMilliseconds(final int seconds) {
        return seconds * 1000;
    }

    @Override
    /**
     * This method is called when the node is about to be used. So what it will do (in theory) is to start the vm (if
     * needed), then find a way to link to the vm, and run slave.jar on the vm
     *
     * @param slaveComputer the node to be launched
     * @param taskListener listener
     */
    public void launch(SlaveComputer slaveComputer, TaskListener taskListener)
                                                        throws IOException, InterruptedException {
        taskListener.getLogger().println("Hakuna Matata");

    }

    public OVirtHypervisor findHypervisor() throws RuntimeException {
        if (hypervisorDescription != null && virtualMachineName != null) {
            for (Cloud cloud: Jenkins.getInstance().clouds) {
                if (cloud instanceof OVirtHypervisor) {
                    OVirtHypervisor temp = (OVirtHypervisor) cloud;
                    if (temp.getHypervisorDescription().equals(hypervisorDescription)) {
                        return temp;
                    }
                }
            }
        }
        throw new RuntimeException("Could not find our ovirt instance");
    }

    /**
     * This method should call the delegate `beforeDisconnect` method before turning off this slave computer.
     *
     * @param computer node to be disconnected
     * @param listener listener
     */
    @Override
    public synchronized void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
    }

    /**
     * Try to shutdown the computer after the slave.jar has stopped running.
     *
     * @param computer node that has been disconnected
     * @param listener listener
     */
    @Override
    public synchronized void afterDisconnect(SlaveComputer computer, TaskListener listener) {

    }
}
