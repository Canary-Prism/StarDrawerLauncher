package canaryprism.stardrawerlauncher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.border.EmptyBorder;

import org.json.JSONObject;
import org.json.JSONWriter;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GitHub;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;

public class Main {

    public static final int major = 2;
    public static final Pattern pattern = Pattern.compile("StarDrawer-(.*)\\.jar");
    public static void main(String[] args) {

        FlatMacDarkLaf.setup();

        String working_directory;
        //here, we assign the name of the OS, according to Java, to a variable...
        String OS = (System.getProperty("os.name")).toUpperCase();
        if (OS.contains("WIN")) {
            working_directory = System.getenv("AppData");
        } else {
            //in either case, we would start in the user's home directory
            working_directory = System.getProperty("user.home");
            //if we are on a Mac, we are not done, we look for "Application Support"
            working_directory += "/Library/Application Support";
        }

        working_directory += "/StarDrawerLauncher";
        //we are now free to set the working_directory to the subdirectory that is our 
        //folder.

        var folder = new File(working_directory);

        if (!folder.exists()) {
            folder.mkdirs();
        }
        
        var save_file = new File(working_directory + "/save.json");
        var save = loadSave(save_file);

        
        var installs_folder = new File(working_directory + "/installs");

        
        if (!hasUsableInstalls(installs_folder)) {
            if (!installs_folder.exists()) {
                installs_folder.mkdirs();
            }
            var frame = new JFrame("StarDrawerLauncher");
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

            var label = new JLabel("""
                    <html>
                    <h1>StarDrawerLauncher</h1>
                    <p>StarDrawer not found in compooper, downloading now...</p>
                    </html>
                    """);
            label.setBorder(new EmptyBorder(10, 10, 10, 10));
            frame.getContentPane().add(label);
            
            frame.pack();
            frame.setResizable(false);
            frame.setVisible(true);
            try {
                var github = GitHub.connectAnonymously();

                var repo = github.getRepository("Canary-Prism/StarDrawer");
                var jar_asset = repo.getLatestRelease().listAssets().toList().stream().filter((e) -> e.getName().matches("StarDrawer-.*\\.jar")).findAny().get();
                System.out.println(jar_asset.getName());
                var matcher = pattern.matcher(jar_asset.getName());
                matcher.matches();
                var version = new Version(matcher.group(1));

                if (version.major != major) {
                    fatal("Current versions of StarDrawer are incompatible with this version of StarDrawerLauncher, please update StarDrawerLauncher");
                }

                download(installs_folder.getAbsolutePath(), jar_asset);

            } catch (IOException e) {
                fatal("Failed to find StarDrawer, make sure you are connected to the internet: " + e.getMessage());
            }
            frame.dispose();
        }

        var target = pickNewest(installs_folder);

        new Thread(() -> {
            try {
                var github = GitHub.connectAnonymously();
                var repo = github.getRepository("Canary-Prism/StarDrawer");
                var jar_asset = repo.getLatestRelease().listAssets().toList().stream().filter((e) -> e.getName().matches("StarDrawer-.*\\.jar")).findAny().get();

                var matcher = pattern.matcher(target.getName());
                matcher.matches();

                var version = new Version(matcher.group(1));


                var matcher2 = pattern.matcher(jar_asset.getName());
                matcher2.matches();

                var latest_version = new Version(matcher2.group(1));

                if (version.major != major) {
                    info("Future versions of StarDrawer are incompatible with this version of StarDrawerLauncher, please update StarDrawerLauncher");
                }
                if (latest_version.compareTo(version) > 0) {
                    info("New version of StarDrawer available, downloading now...");
                    download(installs_folder.getAbsolutePath(), jar_asset);
                }

            } catch (IOException e) {
                info("Autoupdater failed to find StarDrawer, make sure you are connected to the internet: " + e.getMessage());
            }
        }, "AutoUpdater").start();

        var command = new ArrayList<String>();
        command.add(System.getProperty("java.home") + "/bin/java");
        command.add("--enable-preview");
        command.add("-jar");
        command.add(target.getAbsolutePath());

        if (save != null) {
            command.add("--posx");
            command.add(Integer.toString(save.posx));
            command.add("--posy");
            command.add(Integer.toString(save.posy));
            command.add("--width");
            command.add(Integer.toString(save.width));
            command.add("--height");
            command.add(Integer.toString(save.height));
            command.add("--sides");
            command.add(Integer.toString(save.sides));
        }

        try {
            var proc = Runtime.getRuntime().exec(command.toArray(new String[0]));

            
            BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            
            var stdout_record = new ArrayList<String>();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                proc.destroy();
                shutdownSequence(proc, stdout_record.getLast(), save_file);
            }));

            new Thread(() -> {
                String line = "";
                while (proc.isAlive()) {
                    try {
                        while (proc.isAlive() && (line = stderr.readLine()) != null) {
                            System.out.println("ERROR in Stardrawer: " + line);
                        }
                    } catch (IOException e) {
                    }
                }
            }, "stderr reader").start();

            new Thread(() -> {
                String line = "";
                while (proc.isAlive()) {
                    try {
                        while (proc.isAlive() && (line = stdout.readLine()) != null) {
                            stdout_record.add(line);
                        }
                    } catch (IOException e) {
                    }
                }
            }, "stdout reader").start();


            proc.onExit().thenAccept(e -> shutdownSequence(e, stdout_record.getLast(), save_file));


        } catch (IOException e) {
            fatal("Fatal error while starting StarDrawer: " + e.getMessage());
        }

    }

    static void shutdownSequence(Process proc, String output, File save_file) {

        if (proc.exitValue() != 0) {
            fatal("StarDrawer exited with code " + proc.exitValue());
        }

        var end_state = output.split(" ");

        var posx = Integer.parseInt(end_state[0]);
        var posy = Integer.parseInt(end_state[1]);
        var width = Integer.parseInt(end_state[2]);
        var height = Integer.parseInt(end_state[3]);
        var sides = Integer.parseInt(end_state[4]);

        var new_save = new SaveData(posx, posy, width, height, sides);

        save(save_file, new_save);
    }

    private static boolean hasUsableInstalls(File installs_folder) {
        return installs_folder.listFiles((e) -> e.getName().matches("StarDrawer-" + major + "\\..*\\.jar")).length > 0;
    }

    static class SaveData {
        int posx, posy, width, height, sides;
        SaveData(int posx, int posy, int width, int height, int sides) {
            this.posx = posx;
            this.posy = posy;
            this.width = width;
            this.height = height;
            this.sides = sides;
        }
    }

    static SaveData loadSave(File save_file) {
        if (!save_file.exists()) {
            return null;
        }

        // load the save file
        try {
            var save_string = Files.readAllLines(save_file.toPath()).stream().reduce("", (a, b) -> a + b);
            var save = new JSONObject(save_string);

            var posx = save.getInt("posx");
            var posy = save.getInt("posy");
            var width = save.getInt("width");
            var height = save.getInt("height");
            var sides = save.getInt("sides");

            return new SaveData(posx, posy, width, height, sides);

        } catch (IOException e) {
            fatal("Failed to load save file: " + e.getMessage());
            return null;
        }
    }

    static void save(File save_file, SaveData save) {
        try (FileWriter fw = new FileWriter(save_file)) {
            var json_writer = new JSONWriter(fw);

            json_writer
            .object()
                .key("posx").value(save.posx)
                .key("posy").value(save.posy)
                .key("width").value(save.width)
                .key("height").value(save.height)
                .key("sides").value(save.sides)
            .endObject();

        } catch (IOException e) {
            fatal("Failed to save save file: " + e.getMessage());
        }
    }

    static void download(String install_folder, GHAsset asset) {
        try {
            var url = new URI(asset.getBrowserDownloadUrl()).toURL();
            var connection = url.openConnection();

            var input_channel = Channels.newChannel(url.openStream());

            var output_file = new File(install_folder + "/" + asset.getName());

            output_file.createNewFile();

            var output = new FileOutputStream(output_file);

            var output_channel = output.getChannel();

            output_channel.transferFrom(input_channel, 0L, connection.getContentLengthLong());

            output.close();
            input_channel.close();
        } catch (IOException | URISyntaxException e) {
            fatal("Failed to download StarDrawer, make sure you are connected to the internet: " + e.getMessage());
        }
    }

    static File pickNewest(File installs_folder) {
        var installs = installs_folder.listFiles((e) -> e.getName().matches("StarDrawer-.*\\.jar"));

        var map = new HashMap<Version, File>();

        for (var install : installs) {
            var matcher = pattern.matcher(install.getName());
            if (matcher.matches()) {
                var version = new Version(matcher.group(1));
                if (version.major == major)
                    map.put(version, install);
            }
        }

        return map.entrySet().stream().sorted((a, b) -> -a.getKey().compareTo(b.getKey())).findFirst().get().getValue();
    }


    static void fatal(String message) {
        JOptionPane.showMessageDialog(null, message, "StarDrawerLauncher", JOptionPane.ERROR_MESSAGE, null);
        System.err.println("FATAL: " + message);
        System.exit(1);
    }
    static void info(String message) {
        JOptionPane.showMessageDialog(null, message, "StarDrawerLauncher", JOptionPane.INFORMATION_MESSAGE, null);
        System.out.println("INFO: " + message);
    }

}
