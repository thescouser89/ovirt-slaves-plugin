/*
 * The MIT License
 *
 * Copyright (c) 2004-, all the contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.ovirt;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.framework.io.IOException2;
import org.ovirt.engine.sdk.decorators.VM;
import org.ovirt.engine.sdk.entities.IP;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.SFTPv3FileAttributes;
import com.trilead.ssh2.Session;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.NamingThreadFactory;
import hudson.util.NullStream;

/**
 * Part of code taken from ssh slaves plugin
 */
public class OVirtSshLauncher extends ComputerLauncher {

    // TODO: use credentials
    private String username;

    private String password;

    // TODO: Don't hardcode these values
    private int launchTimeout = 300000;
    private int maxRetries = 5;
    private int retryWaitTime = 30;

    /**
     * SSH connection to the slave.
     */
    private transient Connection connection;

    @DataBoundConstructor
    public OVirtSshLauncher(String username, String password) {
        super();
        this.username = username;
        this.password = password;
    }

    /**
     * This method is called when the node is about to be used.
     * So what it will do (in theory) is to start the vm (if
     * needed), then find a way to link to the vm, and run slave.jar on the vm
     *
     * @param slaveComputer the node to be launched
     * @param taskListener  listener
     */
    @Override
    public synchronized void launch(final SlaveComputer slaveComputer, final TaskListener taskListener)
            throws IOException, InterruptedException {

        OVirtVMSlave slave = (OVirtVMSlave) slaveComputer.getNode();
        String hypervisor = slave.getHypervisorDescription();
        String vmName = slave.getVirtualMachineName();

        String ip = null;

        VM vm = OVirtHypervisor.find(hypervisor).getVM(vmName);
        for (int i = 0; i < maxRetries; i++) {
            try {
                List<IP> ips = vm.getGuestInfo().getIps().getIPs();

                if (ips.size() >= 1) {
                    // use first IP to connect via ssh
                    ip = ips.get(0).getAddress();
                    taskListener.getLogger().println("IP of VM Obtained! " + ip);
                    break;
                }
            } catch (NullPointerException e) {
                taskListener.error("Couldn't get IP address of VM.. retrying in " + retryWaitTime + " s");
                Thread.sleep(TimeUnit.SECONDS.toMillis(retryWaitTime));
            }
        }
        if (ip == null) {
            throw new InterruptedException("Couldn't find IP address of VM. Abandoning...");
        }
        connection = new Connection(ip, 22);

        ExecutorService executorService = Executors.newSingleThreadExecutor(
                new NamingThreadFactory(Executors.defaultThreadFactory(),
                        "SSHLauncher.launch for '" + slaveComputer.getName() + "' node"));

        Set<Callable<Boolean>> callables = new HashSet<Callable<Boolean>>();

        callables.add(new Callable<Boolean>() {
            public Boolean call() throws InterruptedException {
                Boolean rval = Boolean.FALSE;
                try {
                    openConnection(taskListener);
                    verifyNoHeaderJunk(taskListener);
                    reportEnvironment(taskListener);

                    final String workingDirectory = getWorkingDirectory(slaveComputer);
                    if (workingDirectory == null) {
                        taskListener.error("Cannot get the working directory for " + slaveComputer);
                        return Boolean.FALSE;
                    }

                    copySlaveJar(taskListener, workingDirectory);
                    startSlave(slaveComputer, taskListener, workingDirectory);

                    PluginEntry.register(connection);
                    rval = Boolean.TRUE;
                } catch (RuntimeException e) {
                    e.printStackTrace(taskListener.error("Unexpected Error"));
                } catch (Error e) {
                    e.printStackTrace(taskListener.error("Unexpected Error"));
                } catch (IOException e) {
                    e.printStackTrace(taskListener.getLogger());
                } finally {
                    return rval;
                }
            }
        });
        try {
            long time = System.currentTimeMillis();
            List<Future<Boolean>> results;
            if (launchTimeout > 0) {
                results = executorService.invokeAll(callables, launchTimeout, TimeUnit.MILLISECONDS);
            } else {
                results = executorService.invokeAll(callables);
            }
            long duration = System.currentTimeMillis() - time;
            Boolean res;
            try {
                res = results.get(0).get();
            } catch (ExecutionException e) {
                res = Boolean.FALSE;
            }
            if (!res) {
                System.out.println("Launch failed");
                taskListener.getLogger().println(" Launch failed - cleaning up connection");
                cleanupConnection(taskListener);
            } else {
                System.out.println("Launch completed");
            }
            executorService.shutdown();
        } catch (InterruptedException e) {
            System.out.println("Launch failed");
        }
    }

    protected void openConnection(TaskListener listener) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        connection.setTCPNoDelay(true);

        for (int i = 0; i <= maxRetries; i++) {
            try {
                connection.connect();
                break;
            } catch (IOException ioexception) {
                String message = ioexception.getMessage();

                if (maxRetries - i > 0) {
                    logger.println("SSH Connection failed with IOException: \"" + message
                            + "\", retrying in " + retryWaitTime + " seconds.  There are "
                            + (maxRetries - i) + " more retries left.");
                } else {
                    logger.println("SSH Connection failed with IOException: \"" + message + "\".");
                    throw ioexception;
                }
            }
        }
        Thread.sleep(TimeUnit.SECONDS.toMillis(retryWaitTime));

        connection.authenticateWithPassword(username, password);

        if (connection.isAuthenticationComplete()) {
            logger.println("Authentication successful");
        } else {
            logger.println("Authentication failed");
            throw new AbortException("Abort");
        }
    }

    /**
     * Makes sure that SSH connection won't produce any unwanted text, which will interfere with sftp execution.
     */
    private void verifyNoHeaderJunk(TaskListener listener) throws
            IOException, InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        connection.exec("true", baos);
        final String s;
        //TODO: Seems we need to retrieve the encoding from the connection destination
        try {
            s = baos.toString(Charset.defaultCharset().name());
        } catch (UnsupportedEncodingException ex) { // Should not happen
            throw new IOException("Default encoding is unsupported", ex);
        }

        if (s.length() != 0) {
            listener.getLogger().println("SSh header junk detected");
            listener.getLogger().println(s);
            throw new AbortException();
        }
    }

    protected void reportEnvironment(TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("Environment:");
        connection.exec("set", listener.getLogger());
    }

    /**
     * Starts the slave process.
     *
     * @param computer         The computer.
     * @param listener         The listener.
     * @param workingDirectory The working directory from which to start the java process.
     * @throws IOException If something goes wrong.
     */
    private void startSlave(SlaveComputer computer, final TaskListener listener,
                            String workingDirectory) throws IOException {
        Session session = connection.openSession();
        expandChannelBufferSize(session, listener);
        String cmd = "cd \"" + workingDirectory + "\" && java -jar slave.jar";

        listener.getLogger().println("Starting slave process " + cmd);
        session.execCommand(cmd);

        session.pipeStderr(new DelegateNoCloseOutputStream(listener.getLogger()));

        try {
            computer.setChannel(session.getStdout(), session.getStdin(), listener.getLogger(), null);
        } catch (InterruptedException e) {
            session.close();
            throw new IOException2("Aborted during connection open", e);
        } catch (IOException e) {
            try {
                // often times error this early means the JVM has died, so let's see if we can capture all stderr
                // and exit code
                throw new IOException2(getSessionOutcomeMessage(session, false), e);
            } catch (InterruptedException x) {
                throw (IOException) new IOException().initCause(e);
            }
        }
    }

    private void expandChannelBufferSize(Session session, TaskListener listener) {
        // see hudson.remoting.Channel.PIPE_WINDOW_SIZE for the discussion of why 1MB is in the right ball park
        // but this particular session is where all the master/slave communication will happen, so
        // it's worth using a bigger buffer to really better utilize bandwidth even when the latency is even larger
        // (and since we are draining this pipe very rapidly, it's unlikely that we'll actually accumulate this much data)
        int sz = 4;
        session.setWindowSize(sz * 1024 * 1024);
        listener.getLogger().println("Expanded the channel window size to " + sz + "MB");
    }

    /**
     * Find the exit code or exit status, which are differentiated in SSH protocol.
     */
    private String getSessionOutcomeMessage(Session session, boolean isConnectionLost) throws InterruptedException {
        session.waitForCondition(ChannelCondition.EXIT_STATUS | ChannelCondition.EXIT_SIGNAL, 3000);

        Integer exitCode = session.getExitStatus();
        if (exitCode != null)
            return "Slave JVM has terminated. Exit code=" + exitCode;

        String sig = session.getExitSignal();
        if (sig != null)
            return "Slave JVM has terminated. Exit signal=" + sig;

        if (isConnectionLost)
            return "Slave JVM has not reported exit code before the socket was lost";

        return "Slave JVM has not reported exit code. Is it still running?";
    }

    private String getWorkingDirectory(SlaveComputer slaveComputer) {
        String workingDirectory = slaveComputer.getNode().getRemoteFS();
        while (workingDirectory.endsWith("/")) {
            workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
        }
        return workingDirectory;
    }

    /**
     * Called to terminate the SSH connection. Used liberally when we back out from an error.
     */
    private void cleanupConnection(TaskListener listener) {
        // we might be called multiple times from multiple finally/catch block,
        if (connection != null) {
            connection.close();
            listener.getLogger().println("Connection closed");
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    /**
     * Method copies the slave jar to the remote system.
     *
     * @param listener         The listener.
     * @param workingDirectory The directory into whihc the slave jar will be copied.
     * @throws IOException If something goes wrong.
     */
    private void copySlaveJar(TaskListener listener, String workingDirectory) throws IOException, InterruptedException {
        String fileName = workingDirectory + "/slave.jar";

        listener.getLogger().println("Starting sftp client");
        SFTPClient sftpClient = null;
        try {
            sftpClient = new SFTPClient(connection);

            try {
                SFTPv3FileAttributes fileAttributes = sftpClient._stat(workingDirectory);
                if (fileAttributes == null) {
                    listener.getLogger().println("Remote FS doesn't exist");
                    sftpClient.mkdirs(workingDirectory, 0700);
                } else if (fileAttributes.isRegularFile()) {
                    throw new IOException("Remote FS is a file");
                }

                try {
                    // try to delete the file in case the slave we are copying is shorter than the slave
                    // that is already there
                    sftpClient.rm(fileName);
                } catch (IOException e) {
                    // the file did not exist... so no need to delete it!
                }

                listener.getLogger().println("Copying slave jar");

                try {
                    byte[] slaveJar = new Slave.JnlpJar("slave.jar").readFully();
                    OutputStream os = sftpClient.writeToFile(fileName);
                    try {
                        os.write(slaveJar);
                    } finally {
                        os.close();
                    }
                    listener.getLogger().println("Copied " + slaveJar.length + " bytes");
                } catch (Error error) {
                    throw error;
                } catch (Throwable e) {
                    throw new IOException2("Error copying slave jar", e);
                }
            } catch (Error error) {
                throw error;
            } catch (Throwable e) {
                throw new IOException2("Error copying slave jar", e);
            }
        } catch (IOException e) {
            if (sftpClient == null) {
                e.printStackTrace(listener.error("Starting stfp client"));
                // lets try to recover if the slave doesn't have an SFTP service
                copySlaveJarUsingSCP(listener, workingDirectory);
            } else {
                throw e;
            }
        } finally {
            if (sftpClient != null) {
                sftpClient.close();
            }
        }
    }

    /**
     * Method copies the slave jar to the remote system using scp.
     *
     * @param listener         The listener.
     * @param workingDirectory The directory into which the slave jar will be copied.
     * @throws IOException          If something goes wrong.
     * @throws InterruptedException If something goes wrong.
     */
    private void copySlaveJarUsingSCP(TaskListener listener, String workingDirectory) throws IOException, InterruptedException {
        SCPClient scp = new SCPClient(connection);
        try {
            // check if the working directory exists
            if (connection.exec("test -d " + workingDirectory, listener.getLogger()) != 0) {
                listener.getLogger().println("Remote filesystem doesn't exist");
                // working directory doesn't exist, lets make it.
                if (connection.exec("mkdir -p " + workingDirectory, listener.getLogger()) != 0) {
                    listener.getLogger().println("Failed to create " + workingDirectory);
                }
            }

            // delete the slave jar as we do with SFTP
            connection.exec("rm " + workingDirectory + "/slave.jar", new NullStream());

            // SCP it to the slave. hudson.Util.ByteArrayOutputStream2 doesn't work for this. It pads the byte array.
            listener.getLogger().println("Copying slave jar");
            scp.put(new Slave.JnlpJar("slave.jar").readFully(), "slave.jar", workingDirectory, "0644");
        } catch (IOException e) {
            throw new IOException2("Error copying slave jar", e);
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        @Override
        public String getDisplayName() {
            return "RHEV + SSH Connection";
        }
    }

    private static class DelegateNoCloseOutputStream extends OutputStream {
        private OutputStream out;

        public DelegateNoCloseOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            if (out != null) out.write(b);
        }

        @Override
        public void close() throws IOException {
            out = null;
        }

        @Override
        public void flush() throws IOException {
            if (out != null) out.flush();
        }

        @Override
        public void write(byte[] b) throws IOException {
            if (out != null) out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (out != null) out.write(b, off, len);
        }
    }
}
