package org.jenkinsci.plugins.ovirt;

import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.ovirt.engine.sdk.decorators.VM;
import org.ovirt.engine.sdk.decorators.VMSnapshot;
import org.ovirt.engine.sdk.entities.Action;

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

    private ComputerLauncher delegateLauncher;
    private String hypervisorDescription;
    private String virtualMachineName;
    private String snapshotName;
    private final int WAITING_TIME_SECS;
    private OVirtHypervisor hypervisor;

    @DataBoundConstructor
    public OVirtVMLauncher(ComputerLauncher delegateLauncher, String hypervisorDescription, String virtualMachineName,
                           String snapshotName, int waitingTimeSecs) {
        super();
        this.delegateLauncher = delegateLauncher;
        this.hypervisorDescription = hypervisorDescription;
        this.virtualMachineName = virtualMachineName;
        this.snapshotName = snapshotName;
        this.WAITING_TIME_SECS = secToMilliseconds(waitingTimeSecs);
    }

    public ComputerLauncher getDelegateLauncher() {
        return delegateLauncher;
    }

    public String getHypervisorDescription() {
        return hypervisorDescription;
    }

    public String getVirtualMachineName() {
        return virtualMachineName;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public int getWAITING_TIME_SECS() {
        return WAITING_TIME_SECS;
    }

    private OVirtHypervisor getHypervisor() {
        if (hypervisor == null) {
            hypervisor = findHypervisor();
        }
        return hypervisor;
    }

    private static int secToMilliseconds(final int seconds) {
        return seconds * 1000;
    }

    private void printLog(TaskListener taskListener, String text) {
        taskListener.getLogger().println(text);
    }


    private void putVMDown(VM vm, TaskListener taskListener) throws Exception {
        if (!getVMStatus().equalsIgnoreCase("down")) {

            printLog(taskListener, vm.getName() + " is to be shutdown");

            Action actionParams = new Action();
            actionParams.setVm(new org.ovirt.engine.sdk.entities.VM());

            vm.shutdown(actionParams);

            while(!getVMStatus().equalsIgnoreCase("down")) {
                printLog(taskListener, "Waiting for " + vm.getName() + " to shutdown...");
                Thread.sleep(10000);
            }
        } else {
            printLog(taskListener, vm.getName() + " is already shutdown");
        }
    }

    private void putVMUp(VM vm, TaskListener taskListener) throws Exception {
        if (getVMStatus().equalsIgnoreCase("down")) {
            printLog(taskListener, vm.getName() + " is to be started");

            Action actionParams = new Action();
            actionParams.setVm(new org.ovirt.engine.sdk.entities.VM());

            vm.start(actionParams);

            while(!getVMStatus().equalsIgnoreCase("up")) {
                printLog(taskListener, "Waiting for " + vm.getName() + " to startup...");
                Thread.sleep(10000);
            }
        } else {
            printLog(taskListener, vm.getName() + " is already up");
        }

    }

    private VMSnapshot getSnapshot(VM vm, String snapshotName) throws Exception {

        if (snapshotName.trim().equals("")) {
            return null;
        }

        for (VMSnapshot snapshot: vm.getSnapshots().list()) {
            if (snapshot.getDescription().equals(snapshotName)) {
                return snapshot;
            }
        }
        return null;
    }

    private void revertSnapshot(VM vm, String snapshotName, TaskListener taskListener) throws Exception {

        VMSnapshot snapshot = getSnapshot(vm, snapshotName);

        // no snapshot to revert to
        if (snapshot == null) {
            return;
        }

        Action actionParams = new Action();
        actionParams.setVm(new org.ovirt.engine.sdk.entities.VM());
        snapshot.restore(actionParams);
        printLog(taskListener, "Reverted " + vm.getName() + " to snapshot " + snapshot.getDescription());
    }

    private String getVMStatus() {
        return getHypervisor().getVM(virtualMachineName).getStatus().getState();
    }
    /**
     * This method is called when the node is about to be used. So what it will do (in theory) is to start the vm (if
     * needed), then find a way to link to the vm, and run slave.jar on the vm
     *
     * @param slaveComputer the node to be launched
     * @param taskListener listener
     */
    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener taskListener)
                                                        throws IOException, InterruptedException {

        OVirtVMSlave slave = (OVirtVMSlave) slaveComputer.getNode();

        printLog(taskListener, "Connecting to ovirt server...");
        VM vm = getHypervisor().getVM(virtualMachineName);

        try {
            putVMDown(vm, taskListener);
            revertSnapshot(vm, slave.getSnapshotName(), taskListener);
            putVMUp(vm, taskListener);
        } catch (Exception e) {
            e.printStackTrace();
        }

        delegateLauncher.launch(slaveComputer, taskListener);
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
            // if nothing found, we are here
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
        delegateLauncher.beforeDisconnect(computer, listener);
    }

    /**
     * Try to shutdown the computer after the slave.jar has stopped running.
     *
     * @param computer node that has been disconnected
     * @param listener listener
     */
    @Override
    public synchronized void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        delegateLauncher.afterDisconnect(computer, listener);
    }
}
