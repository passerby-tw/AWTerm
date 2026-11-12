package org.awterm;

import org.espressh.vts.TerminalProvider;

/** Independent managed application. Terminal capability must be supplied by Espressh. */
public final class AWTerm {
  private AWTerm() {}

  public static void main(String[] args) throws Exception {
    requireManagedGuest();
    if (args.length != 0) throw new IllegalArgumentException("Usage: awterm");
    WindowSession.run();
  }

  private static void requireManagedGuest() {
    try {TerminalProvider.current();}
    catch(RuntimeException failure){throw new IllegalStateException("AWTERM_REQUIRES_ESPRESSH_VTS: launch inside Espressh",failure);}
  }
}
