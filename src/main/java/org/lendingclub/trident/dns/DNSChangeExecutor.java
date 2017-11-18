package org.lendingclub.trident.dns;

public interface DNSChangeExecutor {

	public boolean accepts(DNSManager.DNSRequest request);
	public void execute(DNSManager.DNSRequest request);
}
