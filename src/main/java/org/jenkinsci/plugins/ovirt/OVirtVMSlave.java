package org.jenkinsci.plugins.ovirt;

import hudson.model.*;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Slave;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerListener;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * OVirtVMSlave is used to represent a node. It uses OVirtHypervisor (@see OVirtHypervisor) to get the current nodes
 * available in the ovirt server and uses OVirtComputerLauncher to *really* start the vm, and run slave.jar
 *
 * @See <a href="http://javadoc.jenkins-ci.org/hudson/model/Slave.html">
 */
public class OVirtVMSlave extends Slave {
    @DataBoundConstructor
    public OVirtVMSlave(String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode,
                        String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy,
                        List<? extends NodeProperty<?>> nodeProperties)
                throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy,
              nodeProperties);

    }

    /**
     * Receives notifications about status changes of Computers.
     *
     * @See <a href="http://javadoc.jenkins-ci.org/hudson/slaves/ComputerListener.html">
     */
    @Extension
    public static class OVirtVMSlaveListener extends ComputerListener {

        @Override
        public void preLaunch(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
            /* We may be called on any slave type so check that we should be in here. */
            if (!(c.getNode() instanceof OVirtVMSlave)) {
                return;
            }
        }
    }

    /**
     * Class that is used as helper for the slave view
     *
     * @See <a href="http://javadoc.jenkins-ci.org/hudson/model/Slave.SlaveDescriptor.html">
     */
    @Extension
    public static final class SlaveDescriptorImpl extends SlaveDescriptor {

        public SlaveDescriptorImpl() {
            load(); // load the data from the disk into this object
        }

        @Override
        public String getDisplayName() {
            return "Slave VM computer running on a virtualization platform (via ovirt)";
        }

        @Override
        public boolean isInstantiable() {
            return true;
        }

        public FormValidation doCheckWaitSec(@QueryParameter("waitSec") final String waitSec) {
            return FormValidation.ok(waitSec);
        }
    }
}
