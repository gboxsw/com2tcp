package com.gboxsw.tools.com2tcp;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

/**
 * The proxy providing access to serial ports.
 */
public class SerialProxy {

	/**
	 * Logger.
	 */
	private static final Logger logger = Logger.getLogger(SerialProxy.class.getName());

	/**
	 * Binding to a serial port.
	 */
	private static class SerialPortBinding {

		/**
		 * Name of serial port.
		 */
		final String portName;

		/**
		 * Baudrate of the serial link.
		 */
		final int baudRate;

		/**
		 * Indicates whether data dumping is enabled.
		 */
		final boolean dumpData;

		/**
		 * Open input stream to serial port.
		 */
		InputStream serialInput;

		/**
		 * Open output stream to serial port.
		 */
		OutputStream serialOutput;

		/**
		 * Socket of the currently active client.
		 */
		Socket activeSocket;

		/**
		 * Copy thread forwarding bytes from serial port to tcp port.
		 */
		CopyThread copyThreadSerial2TCP;

		/**
		 * Copy thread forwarding bytes from tcp port to serial port.
		 */
		CopyThread copyThreadTCP2Serial;

		/**
		 * Constructs the binding to a serial port.
		 * 
		 * @param portName
		 *            the port name.
		 * @param baudRate
		 *            the baud rate.
		 */
		SerialPortBinding(String portName, int baudRate, boolean dumpData) {
			this.portName = portName;
			this.baudRate = baudRate;
			this.dumpData = dumpData;
		}

		/**
		 * Returns identification of the underlying serial.
		 * 
		 * @return the identification string.
		 */
		String getId() {
			return portName + "@" + baudRate;
		}

		/**
		 * Activates the serial port of the binding.
		 * 
		 * @return true, if the activation has been completed, false otherwise.
		 */
		boolean openSerial() {
			logger.log(Level.INFO, getId() + ": Opening ...");
			SerialPortSocket portSocket = new SerialPortSocket(portName, baudRate);
			try {
				SerialPortSocket.SerialFullDuplexStream serialStream = portSocket.createStream();
				serialInput = serialStream.getInputStream();
				serialOutput = serialStream.getOutputStream();
				logger.log(Level.INFO, getId() + ": Open");
			} catch (Exception e) {
				serialInput = null;
				serialOutput = null;
				logger.log(Level.INFO, getId() + ": Opening failed");
				return false;
			}

			return true;
		}

		/**
		 * Stops the client.
		 */
		void stopClient() {
			if (activeSocket == null) {
				return;
			}

			logger.log(Level.INFO,
					getId() + ": Client " + activeSocket.getRemoteSocketAddress().toString() + " disconnected.");

			try {
				activeSocket.close();
			} catch (IOException e) {
				logger.log(Level.SEVERE, getId() + ": Closing of client socket failed.", e);
			}

			activeSocket = null;
			copyThreadSerial2TCP.close();
			copyThreadTCP2Serial.close();
			copyThreadSerial2TCP = null;
			copyThreadTCP2Serial = null;
		}

		/**
		 * Handles new client.
		 * 
		 * @param socket
		 *            the client socket.
		 */
		void handleClient(Socket socket) {
			// close session of active client
			if (activeSocket != null) {
				stopClient();
			}

			logger.log(Level.INFO, getId() + ": New client " + socket.getRemoteSocketAddress().toString());

			// open serial port (if necessary)
			if ((serialInput == null) || (serialOutput == null)) {
				if (!openSerial()) {
					logger.log(Level.SEVERE, getId() + ": Client " + socket.getRemoteSocketAddress().toString()
							+ " disconnected due to inactive serial port.");
					try {
						socket.close();
					} catch (Exception e) {
						logger.log(Level.SEVERE, getId() + ": Closing of socket failed.");
					}

					return;
				}
			}

			// prepare and start session for new client
			activeSocket = socket;
			try {
				copyThreadTCP2Serial = dumpData ? new CopyThread(socket.getInputStream(), serialOutput, getId() + " <<")
						: new CopyThread(socket.getInputStream(), serialOutput);
				copyThreadSerial2TCP = dumpData ? new CopyThread(serialInput, socket.getOutputStream(), getId() + " >>")
						: new CopyThread(serialInput, socket.getOutputStream());
				copyThreadTCP2Serial.start();
				copyThreadSerial2TCP.start();
			} catch (IOException e) {
				logger.log(Level.SEVERE, getId() + ": Unable to start session for client.");
				stopClient();
			}
		}
	}

	/**
	 * Mappings of tcp ports to serial ports.
	 */
	private final Map<Integer, SerialPortBinding> portBindings = new HashMap<>();

	/**
	 * Indicates whether listening server threads are daemon threads.
	 */
	private final boolean daemonThreads;

	/**
	 * Constructs the proxy.
	 * 
	 * @param daemonThreads
	 *            true, if listening threads are daemon threads, false
	 *            otherwise.
	 */
	public SerialProxy(boolean daemonThreads) {
		this.daemonThreads = daemonThreads;
	}

	/**
	 * Adds binding of a tcp port to a serial port.
	 * 
	 * @param tcpPort
	 *            the tcp port listening for tcp clients.
	 * @param serialPort
	 *            the bound serial port.
	 * @param baudRate
	 *            the baud rate of the serial port.
	 */
	public void addBinding(int tcpPort, String serialPort, int baudRate) {
		addBinding(tcpPort, serialPort, baudRate, false);
	}

	/**
	 * Adds binding of a tcp port to a serial port.
	 * 
	 * @param tcpPort
	 *            the tcp port listening for tcp clients.
	 * @param serialPort
	 *            the bound serial port.
	 * @param baudRate
	 *            the baud rate of the serial port.
	 * @param dumpData
	 *            true, if data dumping is enabled, false otherwise.
	 */
	public void addBinding(int tcpPort, String serialPort, int baudRate, boolean dumpData) {
		if ((tcpPort < 1) || (tcpPort > 65535)) {
			throw new IllegalArgumentException("Invalid tcp port.");
		}

		if (baudRate < 1) {
			throw new IllegalArgumentException("Invalid baud rate.");
		}

		if (portBindings.containsKey(tcpPort)) {
			throw new IllegalStateException("TCP port is already bound to a serial port.");
		}

		portBindings.put(tcpPort, new SerialPortBinding(serialPort, baudRate, dumpData));
	}

	/**
	 * Launches the proxy.
	 */
	public void launch() {
		// listen on ports
		portBindings.forEach((port, binding) -> {
			Thread clientThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try (ServerSocket server = new ServerSocket(port)) {
						while (true) {
							binding.handleClient(server.accept());
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			clientThread.setDaemon(daemonThreads);
			clientThread.start();
		});
	}
}
