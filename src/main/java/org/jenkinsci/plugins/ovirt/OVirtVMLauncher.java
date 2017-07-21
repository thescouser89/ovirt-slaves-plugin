package org.jenkinsci.plugins.ovirt;

import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.ovirt.engine.sdk4.services.VmService;
import org.ovirt.engine.sdk4.services.VmsService;
import org.ovirt.engine.sdk4.types.Action;
import org.ovirt.engine.sdk4.types.Snapshot;
import org.ovirt.engine.sdk4.types.Vm;
import org.ovirt.engine.sdk4.types.VmStatus;

/**
 * Extension point to allow control over how Computers are "launched",
 * meaning how they get connected to their slave agent program.
 * In this case this class will determine how to start and run slave.jar on
 * the vm running in the ovirt server
 *
 * So when the user press 'Launch Agent' or a job is run, this class will be
 * used.
 *
 * @see <a href='http://javadoc.jenkins-ci.org/hudson/slaves/ComputerLauncher.html'></a>
 */
public class OVirtVMLauncher extends ComputerLauncher {

    private ComputerLauncher delegateLauncher;

    private String hypervisorDescription;
    private String virtualMachineName;
    private String snapshotName;

    private final int WAITING_TIME_MILLISECS;
    private final int retries;

    @DataBoundConstructor
    public OVirtVMLauncher(ComputerLauncher delegateLauncher,
                           String hypervisorDescription, String virtualMachineName,
                           String snapshotName, int waitingTimeSecs, int retries) {
        super();
        this.delegateLauncher = delegateLauncher;
        this.hypervisorDescription = hypervisorDescription;
        this.virtualMachineName = virtualMachineName;
        this.snapshotName = snapshotName;
        this.WAITING_TIME_MILLISECS = secToMilliseconds(waitingTimeSecs);
        this.retries = retries;
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

    public int getWAITING_TIME_MILLISECS() {
        return WAITING_TIME_MILLISECS;
    }

    /**
     * Super fancy method to convert seconds to milliseconds. The
     * implementation is O(1).
     *
     * @param seconds convert this value to milliseconds
     * @return the converted value
     */
    private static int secToMilliseconds(final int seconds) {
        return seconds * 1000;
    }

    /**
     * Get the current vm status. It does so by continuously getting a new VM
     * object corresponding to 'virtualMachineName', and checking on its state.
     *
     * We expect the states to be 'up', 'down', 'powering_up', etc
     *
     * @return the vm status
     */
    private VmStatus getVMStatus() {
        return getOVirtHypervisor()
                .getVM(virtualMachineName)
                .status();
    }

    private OVirtHypervisor getOVirtHypervisor() {
        return OVirtHypervisor.find(hypervisorDescription);
    }


    /**
     * Helper method to print to the jenkins log output that you'll see on
     * the jenkins webserver when you start a new node
     *
     * @param taskListener listener object
     * @param text text to output
     */
    private void printLog(TaskListener taskListener, final String text) {
        taskListener.getLogger().println(text);
    }

    /**
     * Returns a boolean that will return true if a snapshot is specified.
     * A snapshot is specified when the snapshotName is NOT empty.
     *
     * @return true if snapshot name is specified
     */
    private boolean isSnapshotSpecified() {
        return !snapshotName.trim().equals("");
    }

    /**
     * Check is the current vm bounded to this object is down!
     *
     * @return true if the vm is down
     */
    private boolean isVMDown() {
        return getVMStatus().equals(VmStatus.DOWN);
    }

    /**
     * Check is the current vm bounded to this object is up!
     *
     * @return true if the vm is up
     */
    private boolean isVMUp() {

        return getVMStatus().equals(VmStatus.UP);
    }

    private boolean isVMImageLocked() {
        return getVMStatus().equals(VmStatus.IMAGE_LOCKED);
    }

    /**
     * Try to wait for WAITING_TIME_MILLISECS' milliseconds to see if the vm
     * is shutdown.
     * This wait will be attempted 'retries' times before giving up, and
     * throwing an exception to announce that we are giving up
     *
     * @param vm The vm that we are waiting to shutdown
     * @param taskListener taskListener is needed to print to the jenkins log
     * @throws Exception
     */
    private void waitVMIsDown(Vm vm, TaskListener taskListener)
                                                            throws Exception {
        for (int i = 0; i < retries; ++i) {
            printLog(taskListener, "Waiting for " + vm.name() +
                                   " to shutdown...");
            Thread.sleep(WAITING_TIME_MILLISECS);

            if (isVMDown()) {
                printLog(taskListener, "VM is now shutdown");
                return;
            }
        }
        // if we reached here, vm did not shutdown after that many retries
        printLog(taskListener, "VM did not shutdown properly. Giving up!");
        throw new Exception("VM did not shutdown at all!");
    }

    /**
     * Try to wait for WAITING_TIME_MILLISECS' milliseconds to see if the vm
     * is up.
     * This wait will be attempted 'retries' times before giving up, and
     * throwing an exception to announce that we are giving up
     *
     * @param vm The vm that we are waiting to startup
     * @param taskListener taskListener is needed to print to the jenkins log
     * @throws Exception
     */
    private void waitVMIsUp(Vm vm, TaskListener taskListener) throws Exception {
        for (int i = 0; i < retries; ++i) {
            printLog(taskListener, "Waiting for " + vm.name() +
                                   " to start...");
            Thread.sleep(WAITING_TIME_MILLISECS);

            if (isVMUp()) {
                printLog(taskListener, "VM is now online!");
                return;
            }
        }
        printLog(taskListener, "VM did not startup properly. Giving up!");
        throw new Exception("VM did not startup at all!");
    }

    /**
     * Asks ovirt server to shutdown a vm
     *
     * @param vm The vm to be stopped
     * @throws Exception
     */
    private void shutdownVM(Vm vm) throws Exception {
        VmsService vmsService = getOVirtHypervisor()
                .getConnection()
                .systemService()
                .vmsService();
        VmService vmService = vmsService.vmService(vm.id());
        vmService.shutdown().send();
    }

    /**
     * Asks ovirt server to start a vm
     *
     * @param vm The vm to be started
     * @throws Exception
     */
    private void startVM(Vm vm) throws Exception {
        VmsService vmsService = getOVirtHypervisor()
                .getConnection()
                .systemService()
                .vmsService();
        VmService vmService = vmsService.vmService(vm.id());
        vmService.start().send();
    }

    /**
     * Put the vm down, if it is not yet down, and wait for some time to see
     * if the vm is actually down.
     *
     * @param vm The vm to be shutdown
     * @param taskListener listener object
     * @throws Exception
     */
    private void putVMDown(Vm vm, TaskListener taskListener) throws Exception {
        if (!isVMDown()) {
            printLog(taskListener, vm.name() + " is to be shutdown");
            shutdownVM(vm);
        } else {
            printLog(taskListener, vm.name() + " is already shutdown");
            return;
        }
        waitVMIsDown(vm, taskListener);
    }

    /**
     * Put the vm up if it is not already up, and wait for some time to see
     * if the vm is actually up.
     *
     * @param vm The vm to be started
     * @param taskListener listener object
     * @throws Exception
     */
    private void putVMUp(Vm vm, TaskListener taskListener) throws Exception {
        if (isVMDown()) {
            printLog(taskListener, vm.name() + " is to be started");
            startVM(vm);
            waitVMIsUp(vm, taskListener);
        } else {
            printLog(taskListener, vm.name() + " is already up");
        }

    }

    /**
     * Quick note: Don't try to cache the snapshot object. There are cases
     * where I'll cache the snapshot object based on the name, but the snapshot
     * on Ovirt will get deleted, and another snapshot with the same name will
     * be created. In this scenario, the cached snapshot object becomes invalid
     *
     * Instead, just always ask Ovirt for the latest snapshot object
     * @param vm
     * @param snapshotName
     * @return
     * @throws Exception
     */
    private Snapshot getSnapshot(Vm vm, String snapshotName)
                                                            throws Exception {
        if (!isSnapshotSpecified()) {
            return null;
        }
        for (Snapshot snap: vm.snapshots()) {
            if (snap.description().equals(snapshotName)) {
                return snap;
            }
        }
        // if we reached here, then the snapshotName is not bound to that
        // particular vm
        throw new RuntimeException("No snapshot '" + snapshotName + "' " +
                "for vm '" + vm.name() + "' found");
    }

    /**
     * If snapshot is specified, then this method will try to revert the vm
     * to the snapshotName. Note that in most cases the vm should be shutdown
     * before attempting to call this method, even though this claim was
     * never tested
     *
     * If snapshot is not specified, then do nothing
     *
     * @param vm: vm to revert snapshot to
     * @param snapshotName: the snapshotName that the vm will revert to
     * @param taskListener: listener object
     * @throws Exception
     */
    private void revertSnapshot(Vm vm,
                                String snapshotName,
                                TaskListener taskListener) throws Exception {

        if (!isSnapshotSpecified()) {
            throw new Exception("No snapshot specified!");
        }

        Snapshot snapshot = getSnapshot(vm, snapshotName);

        // no snapshot to revert to
        if (snapshot == null) {
            throw new Exception("No snapshot specified!");
        }
        VmsService vmsService = getOVirtHypervisor()
                .getConnection()
                .systemService()
                .vmsService();
        VmService vmService = vmsService.vmService(vm.id());
        printLog(taskListener, "Previewing the snapshot...");
        vmService.previewSnapshot().snapshot(snapshot).async(false).send();
        printLog(taskListener, "Commiting the snapshot...");
        vmService.commitSnapshot().async(false).send();
        printLog(taskListener, "Reverted '" + vm.name() + "' to snapshot '"
                                            + snapshot.description() + "'");
    }

    /**
     * This method is called when the node is about to be used.
     * So what it will do (in theory) is to start the vm (if
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
        Vm vm = OVirtHypervisor.find(hypervisorDescription)
                               .getVM(virtualMachineName);

        try {
            // only if snapshot is specified should we try to shut it down
            // and revert to snapshot
            if (isSnapshotSpecified()) {
                putVMDown(vm, taskListener);
                revertSnapshot(vm, slave.getSnapshotName(), taskListener);
                waitTillSnapshotUnlocked(taskListener);
            }
            putVMUp(vm, taskListener);

            delegateLauncher.launch(slaveComputer, taskListener);
        } catch (Exception e) {
            handleLaunchFailure(e, taskListener);
        }
    }

    private void waitTillSnapshotUnlocked(TaskListener taskListener) throws InterruptedException {
        while(true) {
            if (isVMImageLocked()) {
               printLog(taskListener, "VM is image locked. Waiting till it's really down...");
               Thread.sleep(WAITING_TIME_MILLISECS);
            } else {
                break;
            }
        }
    }

    /**
     * Put the exception in the launch method to the ovirt server log and
     * throw an InterruptedException error about this failure.
     *
     * @param e: Exception to wrap around an InterruptedException
     * @param taskListener: listener object
     * @throws InterruptedException
     */
    private void handleLaunchFailure(Exception e, TaskListener taskListener)
                                                  throws InterruptedException {
        e.printStackTrace();
        printLog(taskListener, e.toString());

        InterruptedException ie = new InterruptedException();
        ie.initCause(e);
        throw ie;
    }

    /**
     * This method should call the delegate `beforeDisconnect` method.
     *
     * @param computer node to be disconnected
     * @param listener listener
     */
    @Override
    public synchronized void beforeDisconnect(SlaveComputer computer,
                                              TaskListener listener) {
        delegateLauncher.beforeDisconnect(computer, listener);
    }

    /**
     * Try to shutdown the computer after the slave.jar has stopped running.
     *
     * @param computer node that has been disconnected
     * @param listener listener
     */
    @Override
    public synchronized void afterDisconnect(SlaveComputer computer,
                                             TaskListener listener) {
        delegateLauncher.afterDisconnect(computer, listener);
    }
}
