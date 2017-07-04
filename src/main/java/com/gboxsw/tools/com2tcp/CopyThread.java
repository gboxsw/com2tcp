package com.gboxsw.tools.com2tcp;

import java.io.*;

/**
 * The copy thread that asynchronously copies bytes from source stream to target
 * stream.
 */
public class CopyThread extends Thread {

	/**
	 * Source stream.
	 */
	private final InputStream source;

	/**
	 * Target stream.
	 */
	private final OutputStream target;

	/**
	 * Indicates whether dumping of data is enabled.
	 */
	private final boolean dumpData;

	/**
	 * Label used to identify data when dump mode is enabled.
	 */
	private final String label;

	/**
	 * Indicates whether the thread is requested to stop.
	 */
	private volatile boolean stopRequested = false;

	/**
	 * Constructs the copy thread.
	 * 
	 * @param source
	 *            the source stream.
	 * @param target
	 *            the target stream.
	 */
	public CopyThread(InputStream source, OutputStream target) {
		this.source = source;
		this.target = target;
		this.label = null;
		this.dumpData = false;
		setDaemon(true);
	}

	/**
	 * Constructs the copy thread with dumps of transferred data.
	 * 
	 * @param source
	 *            the source stream.
	 * @param target
	 *            the target stream.
	 * @param dumpLabel
	 *            the label used to
	 */
	public CopyThread(InputStream source, OutputStream target, String dumpLabel) {
		this.source = source;
		this.target = target;
		this.label = dumpLabel;
		this.dumpData = true;
		setDaemon(true);
	}

	@Override
	public void run() {
		byte[] buffer = new byte[1024];
		while (!stopRequested) {
			try {
				int bytesRead = source.read(buffer);
				if (bytesRead < 0) {
					break;
				}

				if (bytesRead > 0) {
					target.write(buffer, 0, bytesRead);
					target.flush();
					if (dumpData) {
						System.out.println(label + " " + dumpData(buffer, bytesRead));
					}
				}

			} catch (IOException e) {
				break;
			}
		}
	}

	/**
	 * Closes the copy thread.
	 */
	public void close() {
		stopRequested = true;
		interrupt();
	}

	/**
	 * Constructs a string with hexadecimal dump of array values.
	 * 
	 * @param data
	 *            the data.
	 * @param length
	 *            the length of data.
	 * @return the constructed string.
	 */
	private static String dumpData(byte[] data, int length) {
		length = Math.min(length, data.length);
		StringBuilder result = new StringBuilder();

		for (int i = 0; i < length; i++) {
			if (i != 0) {
				result.append(' ');
			}

			int value = Byte.toUnsignedInt(data[i]);
			if (value < 16) {
				result.append('0');
			}
			result.append(Integer.toString(value, 16));
		}

		return result.toString();
	}
}