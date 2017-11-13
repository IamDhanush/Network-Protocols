/**
 * 
 */
package com.protocol.gbnsender;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Date;
import java.util.Scanner;

/**
 * @author vishal
 *
 */
public class MainApp {

	/**
	 * @param args
	 */
	public DatagramSocket sock = null;
	public DatagramPacket sizepacket, sizeackpack = null;
	
	public void gbnTransfer() throws Exception{
		try {
			try {
				sock = new DatagramSocket();
			} catch (IOException ex) {
				System.out.println(ex);
			}
			Sender s = new Sender();
			Receiver r = new Receiver();
			Shared sh = new Shared();
			s.sendShared(sh, s);
			r.recvShared(sh, r);
			s.sendSocket(sock);
			r.recvSocket(sock);
			Thread thread1 = new Thread(s);
			Thread thread2 = new Thread(r);
			// getting file name
			Scanner scanner = new Scanner(System.in);
			System.out.println("Enter the image file name: ");
			String filename = scanner.nextLine();
			File file = new File(filename);
			// sending numberofpackets
			long length = file.length();
			float f = length / 1000;
			long TotalPackets=(long) f;
			System.out.println("Total Packets: "+TotalPackets);
			System.out.println("Loss option ");
			int userChoice = scanner.nextInt();
			s.getOption(userChoice);
			System.out.println("Introduce Error%: ");
			int errorRate = scanner.nextInt();
			scanner = new Scanner(System.in);
			System.out.println("Enter the Window Size:");
			int userInp = scanner.nextInt();// window
			s.getWindow(userInp, errorRate);
			s.getFileName(file, TotalPackets);
			r.getTotPackets(TotalPackets, userChoice);
			byte[] sizebuffer = new byte[8];
			sizebuffer = s.long2byte(length);
			InetAddress ip = InetAddress.getByName("localhost");
			sizepacket = new DatagramPacket(sizebuffer, sizebuffer.length, ip, 5000);
			sock.send(sizepacket);
			byte[] recvackbuf = new byte[1];
			sizeackpack = new DatagramPacket(recvackbuf, recvackbuf.length);
			sock.receive(sizeackpack);
			if (recvackbuf[0] == 0) {
				System.out.println("size received, sending the chunks");
			} else {
				System.out.println("Network busy,try after sometime");
				sock.close();
			}
			thread2.start();
			thread1.start();
			long lStartTime = new Date().getTime();
			while (thread1.isAlive()) {
				continue;
			}
			long lEndTime = new Date().getTime();
			long difference = lEndTime - lStartTime; // check different
			System.out.println("Elapsed milliseconds: " + difference);
		} // try
		catch (Exception ex) {
		}
	}

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		new MainApp().gbnTransfer();
	}
}
