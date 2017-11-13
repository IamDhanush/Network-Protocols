/**
 * 
 */
package com.protocol.sender;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author vishal
 *
 */
class TimerAction extends SelectRepeatSender implements Runnable {
	private static final Lock lock = new ReentrantLock();
	SelectRepeatSender sendobj;
	long currentPkt;

	public void run() {
		while (true) {
			try {
				Thread.sleep(2000);
				lock.lock();
				sendobj = new TimerAction();
				currentPkt = sendobj.queue.poll();
				if (sendobj.checkAck((int) currentPkt)) {
					lock.unlock();
					break;
				} else {
					sendobj.timedoutPckt = currentPkt;
					sendobj.timeoutFlag.set(true);
					sendobj.sema.acquire();
					lock.unlock();
					continue;
				}
			} catch (InterruptedException e) {
				break;
			}
		}
	}

}