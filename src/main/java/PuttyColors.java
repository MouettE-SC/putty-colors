import com.sun.jna.platform.win32.Advapi32Util;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import static com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER;

import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

class ColorTheme {
    int index;
    String name;
    Image image;
    Map<String, String> values;

    void parseReg(File f) throws IOException {
        values = new HashMap<>();
        try (BufferedReader in = new BufferedReader(new FileReader(f))) {
            String l = in.readLine();
            while (l != null) {
                if (l.startsWith("\"Colour")) {
                    String [] cc = l.split("=");
                    values.put(cc[0].substring(1, cc[0].length() - 1), cc[1].substring(1, cc[1].length() - 1));
                }
                l = in.readLine();
            }
        }
    }

    public String toString() {
        return index + " - " + name + ", " + values.toString();
    }
}

public class PuttyColors {

    private static final Logger log;

    static {
        String path = PuttyColors.class.getClassLoader().getResource("logging.properties").getFile();
        System.setProperty("java.util.logging.config.file", path);
        log = Logger.getLogger("PuttyColors");
    }

    private static List<ColorTheme> themes;
    private static Label im;
    private static Button prev;
    private static Button next;

    public static void main(String[] args) throws URISyntaxException, IOException {
        log.info("Starting putty-colors");
        Display d = new Display();

        log.info("Loading themes");
        File images_dir = new File(PuttyColors.class.getClassLoader().getResource("images").toURI());
        Map<Integer, File> m_images = new HashMap<>();
        for (File f : images_dir.listFiles()) {
            String name = f.getName();
            m_images.put(Integer.parseInt(name.substring(0, name.indexOf('.'))), f);
        }

        File themes_dir = new File(PuttyColors.class.getClassLoader().getResource("themes").toURI());
        themes = new ArrayList<>();
        for (File f : themes_dir.listFiles()) {
            String name = f.getName();
            String _index = name.substring(0, name.indexOf('.'));
            ColorTheme t = new ColorTheme();
            t.index = Integer.parseInt(_index.replaceAll("^0+", ""));
            t.name = name.substring(name.indexOf('.')+1, name.lastIndexOf('.')).strip();
            t.image = new Image(d, m_images.get(t.index).getAbsolutePath());
            t.parseReg(f);
            themes.add(t);
        }

        log.info("Starting gui");

        Shell w = new Shell(d);
        w.setImage(new Image(d, PuttyColors.class.getClassLoader().getResourceAsStream("shell.png")));
        w.setText("Putty color changer");
        RowLayout l1 = new RowLayout(SWT.VERTICAL);
        l1.center = true;
        l1.marginBottom = l1.marginTop = l1.marginRight = l1.marginLeft = 10;
        w.setLayout(l1);

        im = new Label(w, SWT.NONE);
        im.setImage(themes.get(0).image);

        Composite c_actions = new Composite(w, SWT.NONE);
        RowLayout l2 = new RowLayout(SWT.HORIZONTAL);
        l2.center = true;
        l2.spacing = 10;
        c_actions.setLayout(l2);

        prev = new Button(c_actions, SWT.PUSH);
        prev.setText(" < ");
        prev.setEnabled(false);
        Combo list = new Combo(c_actions, SWT.DROP_DOWN | SWT.READ_ONLY);
        for (ColorTheme t : themes)
            list.add(t.name);
        list.select(0);

        Button apply = new Button(c_actions, SWT.PUSH);
        apply.setText("Apply");
        next = new Button(c_actions, SWT.PUSH);
        next.setText(" > ");

        prev.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                list.select(list.getSelectionIndex() - 1);
                themeChanged(list.getSelectionIndex());
            }
        });

        next.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                list.select(list.getSelectionIndex() + 1);
                themeChanged(list.getSelectionIndex());
            }
        });

        list.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                themeChanged(list.getSelectionIndex());
            }
        });

        apply.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                apply.setEnabled(false);
                d.asyncExec(() -> {
                    String root = "Software\\SimonTatham\\Putty\\Sessions";
                    ColorTheme t = themes.get(list.getSelectionIndex());
                    for (String session : Advapi32Util.registryGetKeys(HKEY_CURRENT_USER, root)) {
                        String s_root = root + "\\" + session;
                        for (Map.Entry<String, String> c : t.values.entrySet()) {
                            Advapi32Util.registrySetStringValue(HKEY_CURRENT_USER, s_root, c.getKey(), c.getValue());
                        }
                    }
                    d.syncExec(() -> { apply.setEnabled(true); });
                });
            }
        });

        w.pack();
        w.open();

        while (! w.isDisposed())
            if (! d.readAndDispatch())
                d.sleep();

        d.dispose();
    }

    private static void themeChanged(int index) {
        prev.setEnabled(index > 0);
        next.setEnabled(index < themes.size() - 1);
        im.setImage(themes.get(index).image);
    }
}
