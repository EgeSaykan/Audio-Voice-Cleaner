package studio.wave.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HeaderPanelTest {
    @Test void workspaceMenuOpensCoachAndFolderPicker() throws Exception {
        AtomicInteger coach = new AtomicInteger(), folder = new AtomicInteger(), combine = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            var header = new HeaderPanel(coach::incrementAndGet, folder::incrementAndGet, combine::incrementAndGet);
            JMenu menu = findMenu(header);
            assertNotNull(menu, "Workspace must be an interactive menu");
            assertEquals("workspaceMenu", menu.getName());
            assertEquals("workspaceCoach", menu.getItem(0).getName());
            menu.getItem(0).doClick();
            assertEquals(1, coach.get());
            assertEquals(0, folder.get());
            menu.getItem(1).doClick();
            assertEquals(1, folder.get());
            assertEquals("workspaceCombine", menu.getItem(2).getName());
            menu.getItem(2).doClick(); assertEquals(1, combine.get());
        });
    }

    private static JMenu findMenu(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JMenu menu) return menu;
            if (child instanceof Container container) {
                JMenu found = findMenu(container);
                if (found != null) return found;
            }
        }
        return null;
    }
}
