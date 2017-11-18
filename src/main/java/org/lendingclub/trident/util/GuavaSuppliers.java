package org.lendingclub.trident.util;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Adapters for converting to/from Guava suppliers.  Guava's memoization functionality is very nice.
 * @author rschoening
 *
 */
public class GuavaSuppliers {

	
	public static <T> Supplier<T> toGuavaSupplier(java.util.function.Supplier<T> supplier) {
		return new Supplier<T>() {

			@Override
			public T get() {
				return supplier.get();
			}
		};
	}
	public static <T> java.util.function.Supplier<T> toJdkSupplier(Supplier<T> x) {
		
		return  new java.util.function.Supplier<T>() {

			@Override
			public T get() {
				
				return x.get();
			}
		};
	}
}
