package org.lendingclub.trident.util;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

import org.lendingclub.trident.Trident;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Convenience proxy selector that delegates back to the more complicated code.
 * @author rschoening
 *
 */
public class TridentProxySelector extends ProxySelector {

	static Supplier<ProxySelector> supplier = Suppliers.memoize(TridentProxySelector::fetch);
	@Override
	public List<Proxy> select(URI uri) {
		return supplier.get().select(uri);
	}

	@Override
	public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
		supplier.get().connectFailed(uri, sa, ioe);

	}

	private static ProxySelector fetch() {
		return Trident.getApplicationContext().getBean(ProxyManager.class).getProxySelector();
	}
}
