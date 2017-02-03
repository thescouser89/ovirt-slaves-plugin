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

import hudson.Plugin;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.trilead.ssh2.Connection;

/**
 * PluginEntry is the entry point to provide plugin functionality.
 * One instance of this class will be created by Jenkins.
 *
 * @see <a href="http://javadoc.jenkins-ci.org/hudson/Plugin.html"></a>
 *
 * Part of code taken from ssh slaves plugin
 */
public class PluginEntry extends Plugin {

    private static final java.util.logging.Logger LOGGER =
            Logger.getLogger(PluginEntry.class.getName());

    /**
     * The connections to close when the plugin is stopped.
     */
    private static final List<Connection> activeConnections = new ArrayList<Connection>();

    @Override
    public void start() throws Exception {
        LOGGER.log(Level.FINE, "Starting ovirt-slave plugin");
    }

    @Override
    public void stop() throws Exception {
        LOGGER.log(Level.FINE, "Stopping ovirt-slave plugin");
    }

    /**
     * Registers a connection for cleanup when the plugin is stopped.
     *
     * @param connection The connection.
     */
    public static synchronized void register(Connection connection) {
        if (!activeConnections.contains(connection)) {
            activeConnections.add(connection);
        }
    }

    /**
     * Unregisters a connection for cleanup when the plugin is stopped.
     *
     * @param connection The connection.
     */
    public static synchronized void unregister(Connection connection) {
        activeConnections.remove(connection);
    }

    /**
     * Closes all the registered connections.
     */
    private static synchronized void closeRegisteredConnections() {
        for (Connection connection : activeConnections) {
            LOGGER.log(Level.INFO, "Forcing connection to {0}:{1} closed.",
                    new Object[]{connection.getHostname(), connection.getPort()});
            // force closed just in case
            connection.close();
        }
        activeConnections.clear();
    }
}
