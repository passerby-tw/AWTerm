package org.awterm;

import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import java.awt.event.InputMethodEvent;
import java.text.AttributedString;

/** Keep JediTerm's preedit display responsive independently of cursor blinking. */
final class AwtermWidget extends JediTermWidget {
  AwtermWidget(SettingsProvider settings) { super(settings); }

  @Override protected TerminalPanel createTerminalPanel(SettingsProvider settings,
      StyleState style, TerminalTextBuffer buffer) {
    return new ImePanel(settings, buffer, style);
  }

  static final class ImePanel extends TerminalPanel {
    ImePanel(SettingsProvider settings, TerminalTextBuffer buffer, StyleState style) {
      super(settings, buffer, style);
    }

    @Override protected void processInputMethodEvent(InputMethodEvent event) {
      // JediTerm treats a caret-only event's null text as composition cancellation.
      if (event.getID() == InputMethodEvent.INPUT_METHOD_TEXT_CHANGED) {
        super.processInputMethodEvent(event);
        var text = event.getText();
        int committed = event.getCommittedCharacterCount();
        if (text != null && committed > 0
            && committed < text.getEndIndex() - text.getBeginIndex()) {
          // The upstream commit branch clears preedit even for a partial commit.
          var remaining = new AttributedString(text,
              text.getBeginIndex() + committed, text.getEndIndex()).getIterator();
          super.processInputMethodEvent(new InputMethodEvent(this,
              InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, event.getWhen(), remaining,
              0, event.getCaret(), event.getVisiblePosition()));
        }
      }
      event.consume();
      repaint();
    }
  }
}
