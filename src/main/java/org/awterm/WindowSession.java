package org.awterm;

import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import org.espressh.vts.TerminalProvider;

/** A Guest window and a caller-owned VTS frontend; main remains MAIN_BOUND. */
final class WindowSession {
  private final CountDownLatch closed = new CountDownLatch(1);
  private final AtomicBoolean closing = new AtomicBoolean();
  private final AtomicBoolean disposed = new AtomicBoolean();
  private final JFrame frame = new JFrame("AWTerm");
  private final JPanel decision = new JPanel();
  private final VtsConnector connector;
  private final JediTermWidget widget;

  private WindowSession(VtsConnector connector) {
    this.connector = connector;
    widget = new AwtermWidget(new DefaultSettingsProvider() {
      @Override public int caretBlinkingMs() { return 0; }
    });
    // VTS applications write LF; use JediTerm's existing LF-to-next-line display mode.
    widget.getTerminal().setModeEnabled(com.jediterm.terminal.TerminalMode.AutoNewLine, true);
    widget.setTtyConnector(connector);
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.add(widget, BorderLayout.CENTER);
    frame.setSize(800, 500);
    frame.addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent event) { requestClose(); }
      @Override public void windowClosed(WindowEvent event) { closed.countDown(); }
    });
    frame.setVisible(true);
    widget.start();
    Thread terminalEnd = new Thread(() -> {
      try {
        connector.waitFor();
        SwingUtilities.invokeLater(this::dispose);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }, "awterm-terminal-end");
    terminalEnd.setDaemon(true);
    terminalEnd.start();
    widget.requestFocusInWindow();
  }

  static void run() throws Exception {
    // Terminal allocation can start managed processes; never perform it on the EDT.
    VtsConnector connector = new VtsConnector(TerminalProvider.current().createTerminal(80, 24));
    WindowSession[] session = new WindowSession[1];
    try {
      SwingUtilities.invokeAndWait(() -> session[0] = new WindowSession(connector));
      session[0].closed.await();
    } finally {
      // close preserves a last frontend when no Shutdown/Continue decision was supplied.
      connector.close();
    }
  }

  private void requestClose() {
    if (!closing.compareAndSet(false, true)) return;
    worker(() -> {
      long token = connector.requestClose();
      if (token == 0) SwingUtilities.invokeLater(this::dispose);
      else SwingUtilities.invokeLater(() -> prompt(token));
    });
  }

  private void prompt(long token) {
    decision.removeAll();
    decision.add(new JLabel("Last terminal: (S)hutdown / (C)ontinue"));
    JButton shutdown = new JButton("Shutdown");
    JButton keep = new JButton("Continue");
    AtomicBoolean chosen = new AtomicBoolean();
    shutdown.addActionListener(event -> decide(token, true, chosen));
    keep.addActionListener(event -> decide(token, false, chosen));
    decision.getActionMap().put("shutdown",new AbstractAction() {
      public void actionPerformed(java.awt.event.ActionEvent event){decide(token,true,chosen);}
    });
    decision.getActionMap().put("continue",new AbstractAction() {
      public void actionPerformed(java.awt.event.ActionEvent event){decide(token,false,chosen);}
    });
    for(char key:new char[]{'s','S','c','C'}) decision.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(key),key=='s'||key=='S'?"shutdown":"continue");
    decision.add(shutdown);
    decision.add(keep);
    frame.add(decision, BorderLayout.SOUTH);
    frame.revalidate();
    frame.repaint();
    keep.requestFocusInWindow();
  }

  private void decide(long token, boolean shutdown, AtomicBoolean chosen) {
    if (!chosen.compareAndSet(false, true)) return;
    worker(() -> {
      connector.decideClose(token, shutdown);
      SwingUtilities.invokeLater(() -> {
        if (shutdown) dispose();
        else {
          frame.remove(decision);
          closing.set(false);
          frame.revalidate();
          frame.repaint();
          widget.requestFocusInWindow();
        }
      });
    });
  }

  private void worker(Runnable action) {
    Thread thread = new Thread(() -> {
      try { action.run(); }
      catch (RuntimeException failure) {
        failure.printStackTrace();
        SwingUtilities.invokeLater(() -> {
          decision.removeAll();
          decision.add(new JLabel("Terminal close failed: " + failure.getMessage()));
          frame.add(decision, BorderLayout.SOUTH);
          closing.set(false);
          frame.revalidate();
          frame.repaint();
        });
      }
    }, "awterm-vts-close");
    thread.setDaemon(true);
    thread.start();
  }

  private void dispose() {
    if (!disposed.compareAndSet(false, true)) return;
    try { widget.close(); }
    finally { frame.dispose(); closed.countDown(); }
  }
}
