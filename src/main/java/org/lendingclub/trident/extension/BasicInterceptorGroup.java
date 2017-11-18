package org.lendingclub.trident.extension;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BasicInterceptorGroup<T> implements InterceptorGroup<T> {

	List<T> interceptors = Lists.newCopyOnWriteArrayList();
	
	@Override
	public void addInterceptor(T t) {
		interceptors.add(t);	
	}

	@Override
	public List<T> getInterceptors() {
		return interceptors;
	}

	@Override
	public void lock() {
		interceptors = ImmutableList.copyOf(interceptors);		
	}


	public void unlock() {
		interceptors = Lists.newCopyOnWriteArrayList(interceptors);		
	}

	
}
