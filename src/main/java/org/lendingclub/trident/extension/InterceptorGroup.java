package org.lendingclub.trident.extension;

import java.util.List;

public interface InterceptorGroup<T> {

	public void addInterceptor(T t);
	public List<T> getInterceptors();
	
	public void lock();

}
