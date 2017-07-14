package com.gboxsw.tools.com2tcp;

import java.io.IOException;

public class App {

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			System.out.println("Usage: java -jar com2tcp [BINDING]...");
			System.out.println("Binding configuration:");
			System.out.println("TCP_PORT:SERIAL_PORT@BAUDRATE\t without data dump");
			System.out.println("TCP_PORT::SERIAL_PORT@BAUDRATE\t with data dump");
			return;
		}

		SerialProxy proxy = new SerialProxy(false);
		for (String binding : args) {
			try {
				addBinding(proxy, binding);
			} catch (Exception e) {
				System.err.println("Invalid binding: " + binding);
				return;
			}
		}

		proxy.launch();
	}

	/**
	 * Parses a binding definition and adds new binding to the proxy.
	 * 
	 * @param proxy
	 *            the proxy where a binding will be added.
	 * @param binding
	 *            the binding definition.
	 */
	private static void addBinding(SerialProxy proxy, String binding) {
		int colonIdx = binding.indexOf(':');
		int tcpPort = Integer.parseInt(binding.substring(0, colonIdx));
		binding = binding.substring(colonIdx + 1);

		boolean dataDump = false;
		if (binding.startsWith(":")) {
			dataDump = true;
			binding = binding.substring(1);
		}

		int atIdx = binding.indexOf('@');
		String serialPort = binding.substring(0, atIdx);
		int baudRate = Integer.parseInt(binding.substring(atIdx + 1));

		proxy.addBinding(tcpPort, serialPort, baudRate, dataDump);
	}
}
