package org.jenkinsci.plugins.ovirt;

import hudson.model.*;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
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
import org.ovirt.engine.sdk.decorators.VM;
import org.ovirt.engine.sdk.decorators.VMSnapshot;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * OVirtVMSlave is used to represent a node. It uses OVirtHypervisor (@see OVirtHypervisor) to get the current nodes
 * available in the ovirt server and uses OVirtComputerLauncher to *really* start the vm, and run slave.jar
 *
 * @See <a href="http://javadoc.jenkins-ci.org/hudson/model/Slave.html">
 */
public class OVirtVMSlave extends Slave {

    private String hypervisorDescription;
    private String snapshotName;
    private String virtualMachineName;
    private int waitSec;

    @DataBoundConstructor
    public OVirtVMSlave(String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode,
                        String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy,
                        String hypervisorDescription, String snapshotName, int waitSec, String virtualMachineName,
                        List<? extends NodeProperty<?>> nodeProperties)
                throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString,
              new OVirtVMLauncher(launcher, hypervisorDescription, name, snapshotName, waitSec),
              retentionStrategy,
              nodeProperties);

        this.hypervisorDescription = hypervisorDescription;
        this.snapshotName = snapshotName;
        this.virtualMachineName = virtualMachineName;
        this.waitSec = waitSec;
    }

    public String getHypervisorDescription() {
        return hypervisorDescription;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public String getVirtualMachineName() {
        return virtualMachineName;
    }

    public int getWaitSec() {
        return waitSec;
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

            SlaveComputer slave = (SlaveComputer) c;
            OVirtVMLauncher launcher = (OVirtVMLauncher) slave.getLauncher();
            OVirtHypervisor hypervisor = launcher.findHypervisor();
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

        private Map<String, OVirtHypervisor> getDescHypervisor() {
            Map<String, OVirtHypervisor> descHypervisor = new HashMap<String, OVirtHypervisor>();
            for (Cloud cloud: Jenkins.getInstance().clouds) {
                if (cloud instanceof OVirtHypervisor) {
                    OVirtHypervisor temp = (OVirtHypervisor) cloud;
                    descHypervisor.put(temp.getHypervisorDescription(), temp);
                }
            }
            return descHypervisor;
        }

        @Override
        public String getDisplayName() {
            return "Slave VM computer running on a virtualization platform (via ovirt)";
        }

        @Override
        public boolean isInstantiable() {
            return true;
        }

        public FormValidation doCheckWaitSec(@QueryParameter("waitSec") String value) {
         	try {
                int v = Integer.parseInt(value);

                FormValidation result;

                if (v < 0) {
                     result = FormValidation.error("Negative value..");
                } else if (v == 0) {
                    result = FormValidation.warning("You declared this virtual machine to be ready right away. " +
                                                    "It probably needs a couple of seconds before it is " +
                                                    "ready to process jobs!");
                } else {
                    result = FormValidation.ok();
                }

                return result;

            } catch (NumberFormatException e) {
                return FormValidation.error("Not a number..");
            }
        }

        public ListBoxModel doFillHypervisorDescriptionItems() {
            ListBoxModel m = new ListBoxModel();

            for(String key: getDescHypervisor().keySet()) {
                m.add(key, key);
            }

            return m;
        }

        public ListBoxModel doGetVMNames(@QueryParameter("hypervisor") String value) throws IOException, ServletException {
            ListBoxModel m = new ListBoxModel();
            OVirtHypervisor hypervisor = getDescHypervisor().get(value);
            for (String vmName: hypervisor.getVMNames()) {
                m.add(vmName, vmName);
            }
            return m;
        }

        public ListBoxModel doGetSnapshotNames(@QueryParameter("vm") String vm,
                                               @QueryParameter("hypervisor") String hypervisor) throws IOException, ServletException {

            ListBoxModel m = new ListBoxModel();
            OVirtHypervisor hype = getDescHypervisor().get(hypervisor);
            VM vmi = hype.getVM(vm);

            try {
                for (VMSnapshot snapshot: vmi.getSnapshots().list()) {
                    m.add(snapshot.getDescription(), snapshot.getDescription());
                }
            } catch (Exception e) {}

            return m;
        }

    }
}
