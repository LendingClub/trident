package org.lendingclub.trident.util;

import org.lendingclub.trident.event.TridentEvent;

public class TridentStartedEvent extends TridentEvent {

	public TridentStartedEvent() {
		super();
		withMessage("Trident started on "+getEventSource());
	}

}
