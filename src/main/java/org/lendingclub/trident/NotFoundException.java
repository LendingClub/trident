package org.lendingclub.trident;

public class NotFoundException extends TridentException {


	private static final long serialVersionUID = 1L;

	String type;
	String id;
	public NotFoundException(String type, String id) {
		super(type+" not found: "+id);
		this.type = type;
		this.id=id;
	}
}
