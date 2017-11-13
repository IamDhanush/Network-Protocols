/**
 * 
 */
package com.protocol.sender;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author vishal
 *
 */
public class SelectRepeatSender implements Runnable {
	public static DatagramSocket sock = null;
	public static DatagramPacket sendPacket, recvPacket = null;
	public static int port = 15000, window = 5, packetSize = 10;
	public static long checkSum, timedoutPckt;
	public static InetAddress ip;
	public static ByteBuffer fileBuffer;
	public static AtomicBoolean finFlag, timeoutFlag;
	public static AtomicIntegerArray ackedArray = new AtomicIntegerArray(100);
	public static ConcurrentLinkedQueue<Long> queue = new ConcurrentLinkedQueue<Long>();
	public static Semaphore sema = new Semaphore(0);
	public static byte[] sendBytes, checkSumBuf, seqNoBuf;

	public byte[] getBytes(long position) {
		byte bytes[];
		try {
			bytes = new byte[packetSize];
			fileBuffer.position((int) position);
			fileBuffer.get(bytes);
		} catch (BufferUnderflowException bue) {
			System.out.println("Position: " + position);
			bytes = new byte[(fileBuffer.capacity()) - (int) position];
			fileBuffer.get(bytes);
		}
		return bytes;
	}

	public synchronized long calculateChecksum(byte[] buf) {
		int l = buf.length;
		int i = 0;
		long s = 0;
		long d;
		while (l > 1) {
			d = (((buf[i] << 8) & 0xFF00) | ((buf[i + 1]) & 0xFF));
			s += d;
			if ((s & 0xFFFF0000) > 0) {
				s = s & 0xFFFF;
				s += 1;
			}
			i += 2;
			l -= 2;
		}
		if (l > 0) {
			s += (buf[i] << 8 & 0xFF00);
			if ((s & 0xFFFF0000) > 0) {
				s = s & 0xFFFF;
				s += 1;
			}
		}
		s = ~s;
		s = s & 0xFFFF;
		return s;
	}

	public byte[] long2Byte(long l) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(Long.SIZE / 8);
		DataOutputStream dos = new DataOutputStream(baos);
		dos.writeLong(l);
		byte[] result = baos.toByteArray();
		dos.close();
		return result;
	}

	public long byte2Long(byte[] b) throws IOException {
		ByteArrayInputStream baos = new ByteArrayInputStream(b);
		DataInputStream dos = new DataInputStream(baos);
		long result = dos.readLong();
		dos.close();
		return result;
	}

	public byte[] int2Byte(int i) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(Integer.SIZE / 4);
		DataOutputStream dos = new DataOutputStream(baos);
		dos.writeInt(i);
		byte[] result = baos.toByteArray();
		dos.close();
		return result;
	}

	public byte[] blendArrays(byte[] arr1, byte[] arr2) {
		byte[] appendarray = new byte[arr1.length + arr2.length];
		System.arraycopy(arr1, 0, appendarray, 0, arr1.length);
		System.arraycopy(arr2, 0, appendarray, arr1.length, arr2.length);
		return appendarray;
	}

	public synchronized boolean checkAck(int i) {
		int j = 0;
		boolean flag = false;
		while (j < ackedArray.length()) {
			if (ackedArray.get(j) == i) {
				flag = true;
				break;
			}
			j++;
		}
		return flag;
	}

	public void sendPackets() {
		long range = 1;
		long lastUnAck = 1;
		Runnable timerThread = new TimerAction();
		ExecutorService executor = Executors.newFixedThreadPool(10);

		while (finFlag.get() == false) {
			try {
				if (timeoutFlag.get() == true) {
					// get the timeout packet;
					System.out.println("Inside Timeout");
					sendBytes = getBytes((timedoutPckt - 1) * packetSize);
					sendBytes = blendArrays(long2Byte(timedoutPckt), sendBytes);
					checkSum = calculateChecksum(sendBytes);
					sendBytes = blendArrays(long2Byte(checkSum), sendBytes);
					sendPacket = new DatagramPacket(sendBytes, sendBytes.length, ip, port);
					sock.send(sendPacket);
					queue.add(timedoutPckt);
					timeoutFlag.set(false);
					sema.release();
				} else {
					while (true) {
						while (lastUnAck <= range && range < (lastUnAck + window)) {
							// send the next packet;
							try {
								sendBytes = getBytes((range - 1) * Long.valueOf(packetSize));
								sendBytes = blendArrays(long2Byte(range), sendBytes);
								checkSum = calculateChecksum(sendBytes);
								sendBytes = blendArrays(long2Byte(checkSum), sendBytes);
								sendPacket = new DatagramPacket(sendBytes, sendBytes.length, ip, port);
								sock.send(sendPacket);
								queue.add(range);
								executor.execute(timerThread);
								range++;
							} catch (IllegalArgumentException iae) {
								range = range + window + 100;
								break;
							}

						}
						if (checkAck((int) lastUnAck)) {
							lastUnAck++;
							continue;
						} else {
							break;
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		executor.shutdownNow();
	}

	public void run() {
		sendPackets();
	}

	public void recvPackets(int timeoutMilliSeconds) {
		byte[] recvBytes = new byte[16];
		byte[] chkSumBuf = new byte[8];
		byte[] ackNoBuf = new byte[8];
		long chkSum, ack_No;
		int consecutiveTimeouts = 0;

		while (finFlag.get() == false) {
			try {
				recvPacket = new DatagramPacket(recvBytes, recvBytes.length);
				sock.setSoTimeout(timeoutMilliSeconds);
				sock.receive(recvPacket);
				consecutiveTimeouts = 0;
				System.arraycopy(recvBytes, 0, chkSumBuf, 0, chkSumBuf.length);
				System.arraycopy(recvBytes, chkSumBuf.length, ackNoBuf, 0, ackNoBuf.length);
				System.out.println("Ack received: " + byte2Long(ackNoBuf));

				if (byte2Long(chkSumBuf) + calculateChecksum(ackNoBuf) == 65535) {
					if (byte2Long(ackNoBuf) == 0) {
						break;
					} else if (new String(ackNoBuf).equals("FINISHFL")) {
						System.out.println("Ack received: " + new String(ackNoBuf));
						finFlag.set(true);
					} else {
						ack_No = byte2Long(ackNoBuf);
						ackedArray.set((int) ack_No, (int) ack_No);
					}
				}
			} catch (Exception e) {
				consecutiveTimeouts++;
				if (consecutiveTimeouts == 5000) {
					finFlag.set(true);// auto release
				}
				if (timeoutMilliSeconds == 500) {
					break;
				}
				continue;
			}

		}

	}

	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);
		System.out.println("Name of the file to transfer: ");
		String userStrInp = scanner.nextLine();
		System.out.println("File: "+userStrInp);
		SelectRepeatSender srSender = new SelectRepeatSender();
		try {
			FileChannel inChannel = new FileInputStream(userStrInp).getChannel();
			fileBuffer = ByteBuffer.allocate((int) inChannel.size());
			inChannel.read(fileBuffer);
			inChannel.close();
			fileBuffer.flip();

			ip = InetAddress.getByName("localhost");
			sock = new DatagramSocket();

			finFlag = new AtomicBoolean(false);
			timeoutFlag = new AtomicBoolean(false);

			// 2-way handshake
			long seq_No = 0;
			sendBytes = srSender.int2Byte(fileBuffer.capacity());
			sendBytes = srSender.blendArrays(srSender.int2Byte(packetSize), sendBytes);
			sendBytes = srSender.blendArrays(srSender.int2Byte(window), sendBytes);
			sendBytes = srSender.blendArrays(srSender.long2Byte(seq_No), sendBytes);
			checkSum = srSender.calculateChecksum(sendBytes);
			System.out.println("CheckSum: " + checkSum);
			sendBytes = srSender.blendArrays(srSender.long2Byte(checkSum), sendBytes);
			sendPacket = new DatagramPacket(sendBytes, sendBytes.length, ip, port);
			sock.send(sendPacket);
			srSender.recvPackets(500);

			// send the packets
			Thread sendPktThread = new Thread(srSender);
			sendPktThread.start();

			srSender.recvPackets(50);

			while (sendPktThread.isAlive()) {
				continue;
			}

			sock.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
