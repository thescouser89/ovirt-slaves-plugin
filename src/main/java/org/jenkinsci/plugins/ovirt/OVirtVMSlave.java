package org.jenkinsci.plugins.ovirt;

import hudson.model.*;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Slave;
import hudson.slaves.ComputerListener;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.QueryParameter;
import org.ovirt.engine.sdk4.types.Snapshot;
import org.ovirt.engine.sdk4.types.Vm;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * OVirtVMSlave is used to represent a node. It uses OVirtHypervisor
 * (@see OVirtHypervisor) to get the current nodes available in the ovirt
 * server and uses OVirtComputerLauncher to *really* start the vm, and
 * run slave.jar
 *
 * @see <a href="http://javadoc.jenkins-ci.org/hudson/model/Slave.html"></a>
 */
public class OVirtVMSlave extends Slave {

    /**
     * We need to save most of these information so that we can retrieve them
     * back later when we 'edit' a node.
     *
     * This is also the reason why we need getters for them
     */
    static final long serialVersionUID = 1L;
    private String hypervisorDescription;
    private String snapshotName;
    private String virtualMachineName;
    private ComputerLauncher delegateLauncher;
    private int waitSec;

    private int retries;

    /**
     * The constructor for OVIrtVMSlave. Even though it has tons of parameters,
     * it's Jenkins' responsibility to fill those in. This object is used to
     * represent a node creating using the ovirt cloud.
     *
     * @param name name of the node
     * @param nodeDescription node description
     * @param remoteFS the filesystem used
     * @param numExecutors how many executors to run with
     * @param mode mode?
     * @param labelString the labels associated with this node
     * @param delegateLauncher the launcher used to launch that node
     * @param retentionStrategy retention strategy
     * @param hypervisorDescription the hypervisor description
     * @param snapshotName the snapshot used
     * @param waitSec how many seconds to wait before retrying
     * @param retries how many retries to do
     * @param virtualMachineName the name of the ovirt vm
     * @param nodeProperties the node properties
     *
     * @throws Descriptor.FormException FormException
     * @throws IOException IOException
     */
    @DataBoundConstructor
    public OVirtVMSlave(String name, String nodeDescription, String remoteFS,
                        String numExecutors, Mode mode, String labelString,
                        ComputerLauncher delegateLauncher,
                        RetentionStrategy retentionStrategy,
                        String hypervisorDescription, String snapshotName,
                        int waitSec, int retries,
                        String virtualMachineName,
                        List<? extends NodeProperty<?>> nodeProperties)
                throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString,
              new OVirtVMLauncher(delegateLauncher, hypervisorDescription,
                                  virtualMachineName, snapshotName,
                                  waitSec, retries),
              retentionStrategy,
              nodeProperties);

        this.hypervisorDescription = hypervisorDescription;
        this.snapshotName = snapshotName;
        this.virtualMachineName = virtualMachineName;
        this.waitSec = waitSec;
        this.retries = retries;
        this.delegateLauncher = delegateLauncher;
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

    public int getRetries() {
        return retries;
    }

    public ComputerLauncher getDelegateLauncher() {
        return delegateLauncher;
    }


    /**
     * Receives notifications about status changes of Computers.
     *
     * @see <a href="http://javadoc.jenkins-ci.org/hudson/slaves/ComputerListener.html"></a>
     */
    @Extension
    public static class OVirtVMSlaveListener extends ComputerListener {

        /** FIXME: this is doing nothing! */
        @Override
        public void preLaunch(Computer c, TaskListener taskListener)
                                      throws IOException, InterruptedException {
            /* We may be called on any slave type so check that we should
             * be in here.
             */
            if (!(c.getNode() instanceof OVirtVMSlave)) {
                return;
            }
        }
    }

    /**
     * Class that is used as helper for the slave view
     *
     * @see <a href="http://javadoc.jenkins-ci.org/hudson/model/Slave.SlaveDescriptor.html"></a>
     */
    @Extension
    public static final class SlaveDescriptorImpl extends SlaveDescriptor {

        public SlaveDescriptorImpl() {
            load(); // load the data from the disk into this object
        }

        /**
         * Human readable name of this kind of configurable object.
         * @return string representation of the vm
         */
        @Override
        public String getDisplayName() {
            return "Slave VM computer running on a virtualization platform " +
                   "(via ovirt)";
        }

        /**
         * Can the administrator create this type of nodes from UI?
         *
         * @return true
         */
        @Override
        public boolean isInstantiable() {
            return true;
        }

        /**
         * Method that is called when we create a new node. It will validate
         * the waitSec entry in the UI
         *
         * @param value the waitSec value
         * @return FormValidation object that says if the validation passed
         */
        public FormValidation doCheckWaitSec(@QueryParameter("waitSec")
                                             String value) {
         	try {
                int v = Integer.parseInt(value);
                FormValidation result;

                if (v < 0) {
                     result = FormValidation.error("Negative value..");
                } else if (v == 0) {
                    result = FormValidation.warning(
                       "You declared this virtual machine to be ready right " +
                       "away. It probably needs a couple of seconds before it" +
                       " is ready to process jobs!");
                } else {
                    result = FormValidation.ok();
                }
                return result;
            } catch (NumberFormatException e) {
                return FormValidation.error("Not a number..");
            }
        }

        /**
         * Fill in the hypervisor dropdown menu in the UI
         *
         * @return the list of hypoervisors
         */
        public ListBoxModel doFillHypervisorDescriptionItems() {
            ListBoxModel m = new ListBoxModel();

            for(String key: OVirtHypervisor.getAll().keySet()) {
                m.add(key, key);
            }
            return m;
        }

        /**
         * Get all vms from this hypervisor specified as a parameter to
         * generate a dropdown menu
         *
         * @param value the hypervisor description
         *
         * @return the list of vm names
         *
         * @throws IOException IOException
         * @throws ServletException ServletException
         */
        public ListBoxModel doGetVMNames(@QueryParameter("hypervisor")
                                         String value)
                                      throws IOException, ServletException {

            ListBoxModel m = new ListBoxModel();
            List<String> vmNames = getVMNamesList(value);
            for (String vmName: vmNames) {
                m.add(vmName, vmName);
            }
            return m;
        }

        /**
         * Get all vms from this hypervisor
         *
         * @param hypervisor the hypervisor selected
         * @return list of vms
         */
        public List<String> getVMNamesList(final String hypervisor) {
            List<String> vmNames = new LinkedList<String>();

            if (hypervisor == null) {
                return vmNames;
            }
            OVirtHypervisor hype = OVirtHypervisor.getAll().get(hypervisor);
            for (String vmName: hype.getVMNames()) {
                vmNames.add(vmName);
            }
            return vmNames;
        }

        /**
         * Get all the snapshots corresponding to this vm and hypervisor to
         * generate a dropdown menu
         *
         * @param vm vm whose snapshots are to be found
         * @param hypervisor vm belonging to this hypervisor
         * @return list of snapshots for that vm
         *
         * @throws IOException IOException
         * @throws ServletException ServletException
         */
        public ListBoxModel
        doGetSnapshotNames(@QueryParameter("vm") String vm,
                           @QueryParameter("hypervisor") String hypervisor)
                                        throws IOException, ServletException {

            ListBoxModel m = new ListBoxModel();
            for (String snapshot: getSnapshotNamesList(vm, hypervisor)) {
                m.add(snapshot, snapshot);
            }
            return m;
        }

        /**
         * Get all the snapshots corresponding to this vm and hypervisor
         *
         * @param vm vm whose snapshots are to be found
         * @param hypervisor vm belonging to this hypervisor
         * @return list of snapshots for that vm
         */
        public List<String> getSnapshotNamesList(final String vm,
                                                 final String hypervisor) {

            List<String> snapshotNames = new LinkedList<String>();

            // add an empty snapshot option
            snapshotNames.add("");

            if (vm == null || hypervisor == null) {
                return snapshotNames;
            }

            OVirtHypervisor hype = OVirtHypervisor.getAll().get(hypervisor);
            Vm vmi = hype.getVM(vm);

            try {
                for (Snapshot snapshot: vmi.snapshots()) {
                    snapshotNames.add(snapshot.description());
                }
                /** FIXME: empty catch name */
            } catch (Exception e) {}
            return snapshotNames;
        }

    }
}
