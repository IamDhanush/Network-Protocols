/**
 * 
 */
package com.protocol.gbnsender;

/**
 * @author vishal
 *
 */
public class Shared {

	public long lastUnAck = 0;

	public void setLastUnAck(long lastUnAck) {
		this.lastUnAck = lastUnAck;
	}

	public long getLastUnAck() {
		return lastUnAck;
	}
}
