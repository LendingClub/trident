package org.lendingclub.trident.cli;

import java.util.MissingFormatArgumentException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;


public abstract class CommandLineLogger {

	static Logger logger = LoggerFactory.getLogger(CommandLineLogger.class);

	static AtomicReference<CommandLineLogger> delegate = new AtomicReference<>(new LC2DefaultLogger());

	static class LC2DefaultLogger extends CommandLineLogger {

		@Override
		public void doProgress(String fmt, Object... args) {

			String[] sargs = toStringArray(args);
			String val = format(fmt, sargs);
			logger.info("{}", val);

		}

	}

	public abstract void doProgress(String fmt, Object... args);

	public static String format(String smt, Object... args) {
		return format(smt, toStringArray(args));
	}

	public static String format(String fmt, String... args) {
		try {
			fmt = Strings.nullToEmpty(fmt).replace("{}", "%s");
			return Strings.nullToEmpty(String.format(fmt, (Object[]) args));
		} catch (MissingFormatArgumentException e) {
			return fmt;
		}

	}

	public static String[] toStringArray(Object... args) {
		String[] sargs = new String[0];
		if (args != null) {
			sargs = new String[args.length];
			for (int i = 0; i < sargs.length; i++) {
				if (args[i] == null) {
					sargs[i] = null;
				} else {
					sargs[i] = args[i].toString();
				}
			}
		}
		return sargs;
	}

	public static void progress(String fmt, Object... args) {

		delegate.get().doProgress(fmt, args);

	}

	public static void installLogger(CommandLineLogger logger) {
		delegate.set(logger);
	}
}
